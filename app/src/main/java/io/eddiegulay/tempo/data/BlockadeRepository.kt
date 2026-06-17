package io.eddiegulay.tempo.data

import android.content.Context
import android.os.Environment
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File

/**
 * The app blockade: hiding an app is a **10-day commitment** that cannot be undone early, and that
 * survives the app being uninstalled and reinstalled.
 *
 * Each blocked package stores an absolute `unlockAt` epoch-millis. Un-hiding is refused until the
 * clock passes it. Two guarantees harden this against the obvious ways to cheat:
 *
 *  - **Uninstall survival.** App-private storage is wiped on uninstall, so the ledger is mirrored to
 *    a file in *shared* storage ([keepFile]) via All-files access. On a fresh install the two
 *    ledgers are reconciled and, per package, the **later** `unlockAt` always wins — reinstalling can
 *    never shorten or clear an active block.
 *  - **Clock rollback.** A monotonic `lastSeen` high-water mark is persisted; "now" is taken as
 *    `max(systemClock, lastSeen)`, so winding the system clock back doesn't shorten a block.
 *
 * This is best-effort, not tamper-proof: a determined user can still delete the shared file or wipe
 * the device. Guaranteed enforcement would require Device Owner provisioning, which is out of scope.
 */
class BlockadeRepository private constructor(private val appContext: Context) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** package -> unlockAt epoch millis. Presence in this map means the app is hidden. */
    private val _blockade = MutableStateFlow<Map<String, Long>>(emptyMap())
    val blockade: StateFlow<Map<String, Long>> = _blockade.asStateFlow()

    /** Monotonic high-water mark of observed time; guards against system-clock rollback. */
    @Volatile
    private var lastSeen: Long = 0L

    init {
        // Seed synchronously so the first Search frame already excludes blocked apps (no flash), then
        // reconcile against the durable mirror off the main thread.
        val internal = readLedger(internalFile())
        lastSeen = maxOf(internal?.lastSeen ?: 0L, System.currentTimeMillis())
        _blockade.value = internal?.blocks ?: emptyMap()
        scope.launch { reconcile() }
    }

    /** Whether All-files access has been granted; required to write the uninstall-proof mirror. */
    fun hasStorageAccess(): Boolean = Environment.isExternalStorageManager()

    /** Guarded "now": never earlier than the highest time we've previously observed. */
    fun now(): Long = maxOf(System.currentTimeMillis(), lastSeen)

    /** Millis until [packageName] may be un-hidden; 0 if not blocked or already unlockable. */
    fun remainingMillis(packageName: String): Long {
        val unlockAt = _blockade.value[packageName] ?: return 0L
        return (unlockAt - now()).coerceAtLeast(0L)
    }

    fun canUnblock(packageName: String): Boolean = remainingMillis(packageName) == 0L

    /** Begin (or extend) a 10-day block. Never shortens an existing block. */
    suspend fun block(packageName: String) = withContext(Dispatchers.IO) {
        val unlockAt = touchNow() + BLOCK_DURATION_MS
        val next = _blockade.value.toMutableMap()
        next[packageName] = maxOf(next[packageName] ?: 0L, unlockAt)
        commit(next)
    }

    /** Un-hide a package — only honoured once its block has elapsed. Returns true if it was removed. */
    suspend fun unblock(packageName: String): Boolean = withContext(Dispatchers.IO) {
        if (!canUnblock(packageName)) return@withContext false
        commit(_blockade.value - packageName)
        true
    }

    /**
     * Merge the internal and durable ledgers (later unlockAt wins per package), then write the result
     * back to both. Run at startup and whenever storage access may have just been granted.
     */
    suspend fun reconcile() = withContext(Dispatchers.IO) {
        val internal = readLedger(internalFile())
        val external = if (hasStorageAccess()) readLedger(keepFile()) else null
        val merged = mergeByMax(internal?.blocks, external?.blocks)
        lastSeen = maxOf(internal?.lastSeen ?: 0L, external?.lastSeen ?: 0L, System.currentTimeMillis())
        commit(merged)
    }

    // ----- internals -----

    /** Advance and return the monotonic clock. */
    private fun touchNow(): Long {
        lastSeen = maxOf(System.currentTimeMillis(), lastSeen)
        return lastSeen
    }

    /** Persist [blocks] to memory + both ledgers. */
    private fun commit(blocks: Map<String, Long>) {
        _blockade.value = blocks
        val json = encode(blocks, lastSeen)
        runCatching { internalFile().writeText(json) }
        if (hasStorageAccess()) {
            runCatching {
                keepFile().parentFile?.mkdirs()
                keepFile().writeText(json)
            }
        }
    }

    private fun mergeByMax(a: Map<String, Long>?, b: Map<String, Long>?): Map<String, Long> {
        val out = HashMap<String, Long>(a ?: emptyMap())
        b?.forEach { (pkg, unlockAt) -> out[pkg] = maxOf(out[pkg] ?: 0L, unlockAt) }
        return out
    }

    private data class Ledger(val blocks: Map<String, Long>, val lastSeen: Long)

    private fun readLedger(file: File): Ledger? = runCatching {
        if (!file.exists()) return null
        val root = JSONObject(file.readText())
        val blocksObj = root.optJSONObject(KEY_BLOCKS) ?: JSONObject()
        val blocks = HashMap<String, Long>()
        blocksObj.keys().forEach { pkg -> blocks[pkg] = blocksObj.getLong(pkg) }
        Ledger(blocks, root.optLong(KEY_LAST_SEEN, 0L))
    }.getOrNull()

    private fun encode(blocks: Map<String, Long>, lastSeen: Long): String {
        val blocksObj = JSONObject()
        blocks.forEach { (pkg, unlockAt) -> blocksObj.put(pkg, unlockAt) }
        return JSONObject()
            .put(KEY_LAST_SEEN, lastSeen)
            .put(KEY_BLOCKS, blocksObj)
            .toString()
    }

    private fun internalFile(): File = File(appContext.filesDir, "blockade.json")

    /** Durable mirror in shared storage; survives uninstall. Dotfile keeps it out of the way. */
    private fun keepFile(): File =
        File(Environment.getExternalStorageDirectory(), "Documents/.tempo_keep.json")

    companion object {
        const val BLOCK_DAYS = 10
        private const val BLOCK_DURATION_MS = BLOCK_DAYS * 24L * 60L * 60L * 1000L

        private const val KEY_BLOCKS = "blocks"
        private const val KEY_LAST_SEEN = "lastSeen"

        @Volatile
        private var instance: BlockadeRepository? = null

        fun getInstance(context: Context): BlockadeRepository =
            instance ?: synchronized(this) {
                instance ?: BlockadeRepository(context.applicationContext).also { instance = it }
            }
    }
}
