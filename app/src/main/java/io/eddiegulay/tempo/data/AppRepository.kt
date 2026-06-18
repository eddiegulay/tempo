package io.eddiegulay.tempo.data

import android.content.ActivityNotFoundException
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ApplicationInfo
import android.content.pm.LauncherApps
import android.net.Uri
import android.os.Bundle
import android.os.Process
import android.os.UserHandle
import android.os.UserManager
import android.widget.Toast
import androidx.compose.runtime.Immutable
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** One launchable activity, identified well enough to launch and badge across user profiles. */
@Immutable
data class AppInfo(
    val label: String,
    val packageName: String,
    val componentName: ComponentName,
    val user: UserHandle,
    /** Stable identity for list keys + the icon cache (component + user). */
    val key: String,
    /** Localized app-category title (e.g. "生産性"), or null when the app declares none. */
    val category: String?,
    /** Raw [ApplicationInfo] category constant (CATEGORY_AUDIO, …); drives line-glyph selection. */
    val categoryId: Int,
    /** Epoch millis the package was last updated; 0 when unknown. */
    val lastUpdated: Long,
)

/**
 * Process-wide, live inventory of launchable apps, backed by [LauncherApps].
 *
 * Why LauncherApps over a bare PackageManager query:
 *  - it enumerates **all user profiles** (work profile / secondary users) via [UserManager];
 *  - its [LauncherApps.Callback] keeps the list **live** as apps are installed/removed/changed;
 *  - it provides a profile-aware launch + app-details path.
 *
 * The drawer draws Tempo's own monochrome line glyphs (see AppGlyph), not platform app icons, so
 * there is no per-app bitmap decode or icon cache to maintain here.
 */
class AppRepository private constructor(private val appContext: Context) {

    private val launcherApps = appContext.getSystemService(LauncherApps::class.java)
    private val userManager = appContext.getSystemService(UserManager::class.java)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _apps = MutableStateFlow<List<AppInfo>>(emptyList())
    val apps: StateFlow<List<AppInfo>> = _apps.asStateFlow()

    @Volatile
    private var started = false

    /** Idempotent: registers live-update listeners and kicks the first load. */
    fun start() {
        if (started) return
        synchronized(this) {
            if (started) return
            started = true
        }
        launcherApps?.registerCallback(packageCallback)
        // Labels are locale-sensitive; LauncherApps has no locale callback, so reload on locale change.
        ContextCompat.registerReceiver(
            appContext,
            localeReceiver,
            IntentFilter(Intent.ACTION_LOCALE_CHANGED),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        scope.launch { reload() }
    }

    private fun triggerReload() {
        scope.launch { reload() }
    }

    private suspend fun reload() = withContext(Dispatchers.IO) {
        val la = launcherApps ?: return@withContext
        val profiles = userManager?.userProfiles ?: listOf(Process.myUserHandle())
        val self = appContext.packageName
        val pm = appContext.packageManager
        // lastUpdateTime is per-package; memoize so multi-activity packages query PM only once.
        val updatedCache = HashMap<String, Long>()
        val list = profiles.flatMap { user ->
            runCatching { la.getActivityList(null, user) }.getOrDefault(emptyList()).map { lai ->
                val appInfo = lai.applicationInfo
                val pkg = appInfo.packageName
                AppInfo(
                    label = lai.label.toString(),
                    packageName = pkg,
                    componentName = lai.componentName,
                    user = user,
                    key = keyFor(lai.componentName, user),
                    category = ApplicationInfo.getCategoryTitle(appContext, appInfo.category)?.toString(),
                    categoryId = appInfo.category,
                    lastUpdated = updatedCache.getOrPut(pkg) {
                        runCatching { pm.getPackageInfo(pkg, 0).lastUpdateTime }.getOrDefault(0L)
                    },
                )
            }
        }
            .filter { it.packageName != self } // hide Tempo itself
            .distinctBy { it.key }
            .sortedBy { it.label.lowercase() }
        _apps.value = list
    }

    // ----- launching & per-app actions -----

    fun launch(context: Context, app: AppInfo, sourceBounds: android.graphics.Rect? = null, opts: Bundle? = null) {
        try {
            val la = launcherApps
            if (la != null) {
                la.startMainActivity(app.componentName, app.user, sourceBounds, opts)
            } else {
                context.packageManager.getLaunchIntentForPackage(app.packageName)
                    ?.apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
                    ?.let { context.startActivity(it) }
            }
        } catch (_: ActivityNotFoundException) {
            toast(context, "起動できませんでした") // "couldn't launch"
        } catch (_: SecurityException) {
            toast(context, "起動できませんでした")
        }
    }

    fun openAppInfo(context: Context, app: AppInfo) {
        runCatching {
            launcherApps?.startAppDetailsActivity(app.componentName, app.user, null, null)
                ?: throw IllegalStateException()
        }.onFailure {
            context.startActivity(
                Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                    .setData(Uri.fromParts("package", app.packageName, null))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }

    fun requestUninstall(context: Context, app: AppInfo) {
        runCatching {
            context.startActivity(
                Intent(Intent.ACTION_DELETE, Uri.fromParts("package", app.packageName, null))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }.onFailure { toast(context, "アンインストールできませんでした") }
    }

    private fun toast(context: Context, message: String) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }

    private fun keyFor(component: ComponentName, user: UserHandle): String {
        val serial = userManager?.getSerialNumberForUser(user) ?: 0L
        return component.flattenToShortString() + "#" + serial
    }

    private val packageCallback = object : LauncherApps.Callback() {
        override fun onPackageRemoved(packageName: String?, user: UserHandle?) = triggerReload()
        override fun onPackageAdded(packageName: String?, user: UserHandle?) = triggerReload()
        override fun onPackageChanged(packageName: String?, user: UserHandle?) = triggerReload()
        override fun onPackagesAvailable(p: Array<out String>?, u: UserHandle?, r: Boolean) = triggerReload()
        override fun onPackagesUnavailable(p: Array<out String>?, u: UserHandle?, r: Boolean) = triggerReload()
    }

    private val localeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            triggerReload()
        }
    }

    companion object {
        @Volatile
        private var instance: AppRepository? = null

        fun getInstance(context: Context): AppRepository =
            instance ?: synchronized(this) {
                instance ?: AppRepository(context.applicationContext).also { instance = it }
            }
    }
}
