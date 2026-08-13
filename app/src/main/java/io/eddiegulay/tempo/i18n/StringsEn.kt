package io.eddiegulay.tempo.i18n

/**
 * English.
 *
 * Written to Tempo's voice rather than translated word for word. The Japanese copy is quiet, plain
 * and short — it does not exclaim, it does not say "Oops", and it does not explain twice. English
 * that reads like a product announcement would be a faithful translation of the words and a wrong
 * translation of the app.
 *
 * Two standing constraints from the survey, which apply to every string added here later:
 *
 * - **Length is functional, not aesthetic.** Japanese is roughly half the width of English for the
 *   same content, and several slots are measured in glyphs — the player's hero cap divides by 4.2
 *   ("four glyphs plus slack") and clips rather than ellipsises. Short is not a style preference.
 * - **Do not fill the deliberate holes.** Four functions in the gym return `null` or `""` rather than
 *   invent a sentence for a case the specs never covered. An English string in those positions
 *   re-introduces the bug the null prevents.
 *
 * The counterpart is [StringsJa].
 */
object StringsEn : Strings {

    override val lang = Lang.En

    override val app = object : AppStrings {
        override val dockHome = "Home"
        override val dockSearch = "Search"
        override val dockNotifications = "Notifications"
        override val dockGym = "Training"
        override val dockSetDefault = "Set Tempo as the default home app"
    }

    override val onboarding = object : OnboardingStrings {
        override val welcome = "Welcome"

        // The Japanese says access is used "only inside the device" and never transmitted. That
        // promise is the point of the paragraph, so it stays in the same position and stays plain.
        override val preamble = "Before you begin, here is what Tempo needs access to. " +
            "Everything stays on your device and is never sent anywhere."

        override val defaultHomeTitle = "Default home"
        override val defaultHomeRationale = "Opens Tempo when you press the home button. " +
            "Required for it to work as a launcher."

        override val notificationAccessTitle = "Notification access"
        override val notificationAccessRationale = "Reads incoming notifications and shows them " +
            "quietly on the Notifications screen. Nothing leaves your device."

        // 許可 / 後で are two and two characters. "Allow" / "Later" are the shortest honest English
        // equivalents; they sit side by side in a Row with 28.dp between them and no width bound.
        override val grant = "Allow"
        override val later = "Later"
        override val granted = "Allowed"
        override val laterSet = "Set up later"
        override val begin = "Begin"

        override val languageLabel = "Language"
    }

    override val search = object : SearchStrings {
        // けんさく is 検索 written in hiragana — the same word, softened. English has no orthographic
        // equivalent of that softening (§L7 is the same problem in miniature), so the heading carries
        // it as lowercase against the capitalised placeholder below.
        override val heading = "search"
        override val placeholder = "Search"
        override val empty = "Nothing found"
        override val loading = "· · ·"

        override val hiddenApps = "Hidden apps"
        override val toLightTheme = "Switch to light theme"
        override val toDarkTheme = "Switch to dark theme"
        override val language = "Language"

        override val launch = "Launch"
        override val menu = "Menu"

        override val appInfo = "App info"
        override val hideApp = "Hide"
        override val uninstall = "Uninstall"

        override val updatedPrefix = "Updated "
    }

    override val dialog = object : DialogStrings {
        override val modeFocusTitle = "Focus"
        override val modeFocusSubtitle = "Just a clock"
        override val modeGymTitle = "Training"
        override val modeGymSubtitle = "Move your body"

        // やめる here means "leave this dialog", not "abandon a workout" — the gym's own やめる is a
        // different string with a different key, and the two must not be merged.
        override val dismiss = "Cancel"

        override val languageTitle = "Language"
    }

    override val gym = object : GymStrings {
        override val tierBeginner = "Beginner"
        override val tierIntermediate = "Intermediate"
        override val tierAdvanced = "Advanced"
    }
}
