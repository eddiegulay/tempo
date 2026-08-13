package io.eddiegulay.tempo.i18n

import androidx.compose.runtime.staticCompositionLocalOf

/**
 * The languages Tempo speaks.
 *
 * Two, and the store knows it (`.planning/i18n/DECISIONS.md` §L1). The database's `name_ja`/`name_en`
 * column pair, the bundled fonts' Latin coverage and one/other pluralisation all assume exactly this
 * pair; a third language is a schema migration and a third [Strings] implementation, and pretending
 * otherwise would have bought generality nobody has asked for.
 *
 * [tag] is what goes in DataStore. It is a BCP-47 primary subtag rather than an ordinal because an
 * ordinal silently changes meaning the day someone reorders the enum, and this value is on disk.
 */
enum class Lang(val tag: String) {
    Ja("ja"),
    En("en"),
    ;

    companion object {
        /** Round-trips [tag]. Unknown or absent input is the caller's problem to default — see §L5. */
        fun fromTag(raw: String?): Lang? = entries.firstOrNull { it.tag == raw }
    }
}

/**
 * Every user-visible string in Tempo, in one place, resolved by language.
 *
 * **Why an interface and not `res/values-en/strings.xml`** (§L2). The domain layer has no `Context`:
 * 138 user-visible strings live in `gym/` — `RecordCopy`, `EngineRows`, `ChartGeometry` — and 21 more
 * in `data/`, all pure Kotlin covered by plain JUnit tests. Reading a string resource needs a
 * `Context`, and reading one in a unit test needs Robolectric, which `CONTRIBUTING.md` forbids.
 * Resources would push `Context` into the domain layer and take the copy tests off the JVM.
 *
 * The property that buys: **a missing translation does not compile.** An unimplemented interface
 * member is a build error. Android resources fall back to the default language silently, which in
 * this app means shipping Japanese to an English user — the exact failure this migration exists to
 * prevent, arriving quietly.
 *
 * Grouped into one nested interface per namespace so 787 strings stay navigable and two people can
 * migrate different pages without touching the same file.
 *
 * Composables read this from [LocalStrings]. **Domain code takes it as a parameter** — it is a plain
 * object with no Compose dependency, and that is what keeps the copy tests on the JVM.
 */
interface Strings {

    /** Which language this table is. Callers branch on it only for genuine behaviour splits (§L7). */
    val lang: Lang

    val app: AppStrings
    val onboarding: OnboardingStrings
    val search: SearchStrings
    val dialog: DialogStrings
    val gym: GymStrings
}

/** App-wide chrome: the dock, and the names of the two modes. */
interface AppStrings {
    /**
     * The dock's four buttons, every one of them icon-only.
     *
     * These are `contentDescription`s, not labels — nothing is drawn. That makes them *more*
     * load-bearing rather than less: they are the sole naming of their control, so a dock left in
     * Japanese under an English UI is unusable with TalkBack rather than merely untranslated.
     */
    val dockHome: String
    val dockSearch: String
    val dockNotifications: String
    val dockGym: String

    /** The long-press that claims the default-home role. It has no visual affordance at all. */
    val dockSetDefault: String
}

/** The first-launch permission walkthrough. */
interface OnboardingStrings {
    val welcome: String
    val preamble: String

    val defaultHomeTitle: String
    val defaultHomeRationale: String

    val notificationAccessTitle: String
    val notificationAccessRationale: String

    val grant: String
    val later: String
    val granted: String

    /** The settled state after deferring — distinct from [later], which is still an action. */
    val laterSet: String
    val begin: String

    /** The language row, which sits above 「ようこそ」 and shows exactly once (§L6). */
    val languageLabel: String
}

/** Search, which doubles as the app drawer and hosts the app's global settings. */
interface SearchStrings {
    val heading: String
    val placeholder: String
    val empty: String

    /**
     * Shown while the app inventory is still loading — distinct from [empty], which means the search
     * found nothing. Loading is not empty; conflating them is the mistake this codebase has caught
     * five times in the gym feature alone.
     *
     * It is punctuation rather than a word, and still belongs here: 「・・・」 is three CJK middle dots,
     * which Latin typography does not spell that way.
     */
    val loading: String

    val hiddenApps: String
    val toLightTheme: String
    val toDarkTheme: String
    val language: String

    /** Row actions: the tap target and the long-press menu, both announced only to TalkBack. */
    val launch: String
    val menu: String

    /** The per-app long-press menu. */
    val appInfo: String
    val hideApp: String
    val uninstall: String

    /**
     * Prefix on a row's last-updated date, e.g. 更新 6月10日 / Updated 10 Jun.
     *
     * A prefix rather than a formatted sentence because the date beside it comes from a different
     * layer; the two are joined at the call site. The date format itself is a `fmt.*` concern and is
     * still the one hard-coded `DateTimeFormatter` in the app.
     */
    val updatedPrefix: String
}

/** Modal dialogs. */
interface DialogStrings {
    val modeFocusTitle: String
    val modeFocusSubtitle: String
    val modeGymTitle: String
    val modeGymSubtitle: String
    val dismiss: String

    /**
     * The language picker's own copy.
     *
     * Deliberately thin: the two rows name themselves in their own language and are *not* translated
     * (§L6). A row reading "Japanese" under an English UI is useless to the person who needs it.
     */
    val languageTitle: String
}

/**
 * 鍛錬. Opened here with its first three strings; the rest of the feature's 546 arrive with the page
 * migrations.
 *
 * The tier words are first because they are load-bearing elsewhere: `Tier` used to carry them as
 * constructor arguments *and* store them in the database (§L3). The enum keeps storing 入門 as an
 * opaque token so the schema's CHECK constraint and every existing row still work; only the display
 * word moved here.
 */
interface GymStrings {
    val tierBeginner: String
    val tierIntermediate: String
    val tierAdvanced: String
}

/**
 * The active table.
 *
 * `staticCompositionLocalOf`, exactly like `LocalTempoColors` and for the same reason: it does not
 * track reads, so changing it recomposes the provider's whole content lambda. That is what makes a
 * language switch instant and total rather than a slow wave of individually-invalidated `Text`s.
 *
 * Defaulting to [StringsJa] rather than throwing keeps previews and any composable rendered outside
 * `TempoApp` working, and matches what the app shipped before it spoke two languages.
 */
val LocalStrings = staticCompositionLocalOf<Strings> { StringsJa }

/** Resolve a table without Compose — for domain code, services and tests. */
fun stringsFor(lang: Lang): Strings = when (lang) {
    Lang.Ja -> StringsJa
    Lang.En -> StringsEn
}
