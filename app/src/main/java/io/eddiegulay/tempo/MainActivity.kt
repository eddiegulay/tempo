package io.eddiegulay.tempo

import android.app.role.RoleManager
import android.content.Intent
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.provider.Settings
import io.eddiegulay.tempo.data.TempoTheme
import io.eddiegulay.tempo.gym.GymViewModel
import io.eddiegulay.tempo.gym.GymViewModelFactory
import io.eddiegulay.tempo.gym.LeaveReason
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.view.WindowCompat
import io.eddiegulay.tempo.ui.Screen
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

    /**
     * 鍛錬's state, and the delegate constructs **nothing** until first touched.
     *
     * That laziness is the guarantee the whole feature is built on: the gym's store is a
     * `SQLiteOpenHelper`, which runs `onCreate`/`onUpgrade` on its first read, and this is a HOME app
     * whose `onCreate` runs on every HOME press from a cold process. A user who never trains must
     * never pay for a database they do not have. Compose resolves the same instance from inside the
     * `Screen.Gym` branch — same `ViewModelStore`, same default key — so there is exactly one.
     */
    private val gymViewModel: GymViewModel by viewModels { GymViewModelFactory(applicationContext) }

    private lateinit var roleRequestLauncher: ActivityResultLauncher<Intent>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Draw under the status/navigation bars; the UI applies its own system-bar insets.
        WindowCompat.setDecorFitsSystemWindows(window, false)

        // Paint the window in the *persisted* in-app theme before Compose's first frame. The XML
        // windowBackground only tracks the system day/night setting, so a user whose chosen theme
        // differs from the system would otherwise see a flash of the wrong colour on every launch.
        val isDark = viewModel.theme.value == TempoTheme.Sumi
        window.setBackgroundDrawable(ColorDrawable(if (isDark) WINDOW_SUMI else WINDOW_PAPER))

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

    /**
     * HOME pressed while Tempo is already foreground: return to a clean Home screen.
     *
     * The gym's half of that reset runs first, so a HOME press is one atomic reset across both
     * owners rather than two frames of half-reset state. It is guarded on the screen because merely
     * *reading* [gymViewModel] constructs it — a HOME press from Search must not be what finally
     * opens the gym's database.
     *
     * `onLeaveShell` clears the gym's back stack and its transient sheets and prompts, and leaves the
     * live workout alone. A HOME press is not a quit: the clock is monotonic and keeps running, the
     * open-session row in SQLite stays as the process-death safety net, and 続ける on return resumes
     * exactly where the user was.
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (viewModel.screen.value == Screen.Gym) gymViewModel.onLeaveShell(LeaveReason.HomePress)
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
        // Pre-Compose window fills; mirror PaperColors.bgSolid / SumiColors.bgSolid.
        const val WINDOW_PAPER = 0xFFF2EEE4.toInt()
        const val WINDOW_SUMI = 0xFF1A1814.toInt()
    }
}
