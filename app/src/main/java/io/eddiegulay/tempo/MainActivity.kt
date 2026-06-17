package io.eddiegulay.tempo

import android.app.role.RoleManager
import android.content.Intent
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.provider.Settings
import io.eddiegulay.tempo.data.TempoTheme
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.view.WindowCompat
import io.eddiegulay.tempo.ui.TempoApp

/**
 * The launcher's only Activity. Registered as a HOME activity, it draws edge-to-edge behind the
 * system bars and hosts the Compose UI. It owns the [LauncherViewModel] so it can drive launcher
 * lifecycle from outside Compose:
 *
 *  - every HOME press ([onNewIntent]) resets the UI to a clean Home screen;
 *  - the default-home status is refreshed on each resume and can be requested via [RoleManager].
 */
class MainActivity : ComponentActivity() {

    private val viewModel: LauncherViewModel by viewModels { LauncherViewModelFactory(applicationContext) }

    private lateinit var roleRequestLauncher: ActivityResultLauncher<Intent>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Draw under the status/navigation bars; the UI applies its own system-bar insets.
        WindowCompat.setDecorFitsSystemWindows(window, false)

        // Paint the window in the *persisted* in-app theme before Compose's first frame. The XML
        // windowBackground only tracks the system day/night setting, so a user whose chosen theme
        // differs from the system would otherwise see a flash of the wrong colour on every launch.
        val isDark = viewModel.theme.value == TempoTheme.Amoled
        window.setBackgroundDrawable(ColorDrawable(if (isDark) WINDOW_AMOLED else WINDOW_PAPER))

        roleRequestLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult(),
        ) { refreshDefaultLauncherState() }

        setContent {
            TempoApp(
                viewModel = viewModel,
                onRequestDefault = ::requestDefaultLauncher,
            )
        }
    }

    /** HOME pressed while Tempo is already foreground: return to a clean Home screen. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        viewModel.resetToHome()
    }

    override fun onResume() {
        super.onResume()
        // Default-home status can change in Settings while we're away.
        refreshDefaultLauncherState()
    }

    private fun refreshDefaultLauncherState() {
        viewModel.setDefaultLauncher(isDefaultLauncher())
    }

    private fun isDefaultLauncher(): Boolean {
        val roleManager = getSystemService(RoleManager::class.java) ?: return false
        return roleManager.isRoleAvailable(RoleManager.ROLE_HOME) &&
            roleManager.isRoleHeld(RoleManager.ROLE_HOME)
    }

    private fun requestDefaultLauncher() {
        val roleManager = getSystemService(RoleManager::class.java)
        if (roleManager != null &&
            roleManager.isRoleAvailable(RoleManager.ROLE_HOME) &&
            !roleManager.isRoleHeld(RoleManager.ROLE_HOME)
        ) {
            roleRequestLauncher.launch(roleManager.createRequestRoleIntent(RoleManager.ROLE_HOME))
        } else {
            // Fallback: drop the user on the system's Home-app picker.
            startActivity(Intent(Settings.ACTION_HOME_SETTINGS))
        }
    }

    private companion object {
        // Pre-Compose window fills; mirror PaperColors.bgSolid / AmoledColors.bgSolid.
        const val WINDOW_PAPER = 0xFFF2EEE4.toInt()
        const val WINDOW_AMOLED = 0xFF000000.toInt()
    }
}
