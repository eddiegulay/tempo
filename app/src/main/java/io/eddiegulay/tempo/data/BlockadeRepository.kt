package io.eddiegulay.tempo.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File

/**
 * The app blockade: hiding an app is a **10-day commitment** that cannot be undone early, and that
 * best-effort survives the app being uninstalled and reinstalled.
 *
 * Each blocked package stores an absolute `unlockAt` epoch-millis. Un-hiding is refused until the
 * clock passes it. Two properties harden this against the obvious ways to cheat:
 *
 *  - **Uninstall survival (best-effort).** App-private storage is wiped on uninstall, so the ledger
 *    file ([internalFile]) is included in Android Auto Backup (see `@xml/backup_rules` and
 *    `@xml/data_extraction_rules`). On a fresh install, Android restores it from the user's Google
 *    cloud backup before the app runs, so an active block reappears. This needs no runtime
 *    permission but only works when the user has device backup enabled and the account is available.
 *  - **Clock rollback.** A monotonic `lastSeen` high-water mark is persisted; "now" is taken as
 *    `max(systemClock, lastSeen)`, so winding the system clock back doesn't shorten a block.
 *
 * This is best-effort, not tamper-proof: a determined user can still disable backup or wipe the
 * device. Guaranteed enforcement would require Device Owner provisioning, which is out of scope.
 */
class BlockadeRepository private constructor(private val appContext: Context) {

    /** package -> unlockAt epoch millis. Presence in this map means the app is hidden. */
    private val _blockade = MutableStateFlow<Map<String, Long>>(emptyMap())
    val blockade: StateFlow<Map<String, Long>> = _blockade.asStateFlow()

    /** Monotonic high-water mark of observed time; guards against system-clock rollback. */
    @Volatile
    private var lastSeen: Long = 0L

    init {
        // Seed synchronously so the first Search frame already excludes blocked apps (no flash). The
        // ledger is either freshly written by this install or restored from cloud backup on reinstall.
        val internal = readLedger(internalFile())
        lastSeen = maxOf(internal?.lastSeen ?: 0L, System.currentTimeMillis())
        _blockade.value = internal?.blocks ?: emptyMap()
    }

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

    // ----- internals -----

    /** Advance and return the monotonic clock. */
    private fun touchNow(): Long {
        lastSeen = maxOf(System.currentTimeMillis(), lastSeen)
        return lastSeen
    }

    /** Persist [blocks] to memory + the internal ledger (which Auto Backup mirrors to the cloud). */
    private fun commit(blocks: Map<String, Long>) {
        _blockade.value = blocks
        val json = encode(blocks, lastSeen)
        runCatching { internalFile().writeText(json) }
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
