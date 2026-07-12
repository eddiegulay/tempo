package io.eddiegulay.tempo.calendar

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.CalendarContract
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

private val CALENDAR_PERMISSIONS = arrayOf(
    Manifest.permission.READ_CALENDAR,
    Manifest.permission.WRITE_CALENDAR,
)

/** True when Tempo may read the calendar — checked from outside Compose too (Home, ViewModel). */
fun hasCalendarAccess(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) ==
        PackageManager.PERMISSION_GRANTED

@Stable
class CalendarPermissionState(
    val granted: Boolean,
    /**
     * True once the system will no longer show the permission dialog, so [request] can only open
     * Settings. The UI needs to know, because a tap that silently throws the user into Settings with
     * no warning reads as a bug — the prompt has to change its words and say where it is taking them.
     */
    val permanentlyDenied: Boolean,
    /** Ask for access. Once permanently denied this routes to app settings instead. */
    val request: () -> Unit,
)

/**
 * Calendar access, as a single "ask" the UI can hang off one tap.
 *
 * Both permissions are requested together (they share a permission group, so the user sees one
 * "Calendar" dialog). Grant is re-checked on every resume, because it can be revoked in Settings
 * while the launcher is alive — same shape as the notification-listener check.
 */
@Composable
fun rememberCalendarPermissionState(
    granted: Boolean,
    onGrantedChange: (Boolean) -> Unit,
): CalendarPermissionState {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }

    // Before the very first request, shouldShowRationale is also false — indistinguishable from a
    // permanent denial without remembering that we asked. Survives process death.
    var asked by rememberSaveable { mutableStateOf(false) }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { result ->
        asked = true
        onGrantedChange(result[Manifest.permission.READ_CALENDAR] == true)
    }

    val permanentlyDenied = !granted && asked && activity != null && CALENDAR_PERMISSIONS.none {
        ActivityCompat.shouldShowRequestPermissionRationale(activity, it)
    }

    return CalendarPermissionState(
        granted = granted,
        permanentlyDenied = permanentlyDenied,
        request = {
            if (permanentlyDenied) {
                // The system will no longer show the dialog; Settings is the only way back.
                context.startActivity(
                    Intent(
                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.fromParts("package", context.packageName, null),
                    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            } else {
                launcher.launch(CALENDAR_PERMISSIONS)
            }
        },
    )
}

/**
 * Sends the user where a calendar account is added. Without one there is nothing to write an event
 * to, and no amount of retrying inside Tempo will conjure a calendar into existence.
 *
 * ACTION_SYNC_SETTINGS is the accounts list on every stock Android; ACTION_ADD_ACCOUNT is the fallback
 * for OEM builds that have dropped it. If neither resolves we do nothing rather than crash — the
 * message on screen has already told the user what is wrong, which is the part that matters.
 */
fun openAccountSettings(context: Context) {
    val candidates = listOf(
        Intent(Settings.ACTION_SYNC_SETTINGS),
        Intent(Settings.ACTION_ADD_ACCOUNT)
            .putExtra(Settings.EXTRA_AUTHORITIES, arrayOf(CalendarContract.AUTHORITY)),
    )
    candidates.firstNotNullOfOrNull { intent ->
        runCatching {
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }.getOrNull()
    }
}

private fun Context.findActivity(): Activity? = generateSequence(this) {
    (it as? ContextWrapper)?.baseContext
}.filterIsInstance<Activity>().firstOrNull()
