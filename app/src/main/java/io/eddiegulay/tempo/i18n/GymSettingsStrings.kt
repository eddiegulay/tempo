package io.eddiegulay.tempo.i18n

/**
 * 鍛錬's settings page and the safety note.
 *
 * **This file is owned by one migration unit.** Interface plus both implementations live together so
 * that adding a string is a single-file change and two people migrating different pages never touch
 * the same file. Add members here; never to `Strings.kt`, which only lists the namespaces.
 *
 * Rules for filling it, from `.planning/i18n/DECISIONS.md`:
 *
 * - A key names *what the string means*, never what it says.
 * - Japanese values are **transcribed, not authored** — the app ships them today and this move must be
 *   behaviour-neutral for Japanese.
 * - Anything built from a number or a date belongs on [Formats], not here.
 * - Do not fill a deliberate hole: several functions return null rather than invent a sentence the
 *   specs never wrote, and an English string in that position re-introduces the bug the null prevents.
 *
 * **Nothing here takes a parameter, and that is the point.** `GymSettingsCopy.kt`'s branching lives in
 * `when` expressions that *select* one of these constants — `speechRowState` over `SpeechAvailability?`
 * is four arms choosing among four members below — rather than in code that concatenates one. A `when`
 * that selects is translatable by replacing this file; a `when` that concatenates is not. Two members
 * of the page compose (`settingsSecondsLabel`, `settingsRowDescription`) and both compose through
 * [Formats], which is behaviour rather than copy.
 */
interface GymSettingsStrings {

    // ─── The page ───────────────────────────────────────────────────────────────────────────────

    val title: String
    val subtitle: String

    /**
     * The spoken label on the header's ← glyph.
     *
     * **A documented gap, carried across rather than closed here.** No spec supplies a word for this
     * glyph; the page borrows `GYM.LIBRARY.DETAIL`'s とじる so that a bare "←" is not announced as
     * punctuation. The Japanese is transcribed as it ships, and the English is the same borrowing.
     */
    val backAction: String

    // ─── The four cards ─────────────────────────────────────────────────────────────────────────

    val sectionCues: String
    val sectionProgress: String
    val sectionRestDefaults: String
    val sectionDisplay: String

    // ─── The rows ───────────────────────────────────────────────────────────────────────────────

    val rowHaptics: String
    val rowTones: String
    val rowSpeech: String
    val rowAutoAdvanceReps: String
    val rowPrepareSeconds: String
    val rowStationRest: String
    val rowRoundRest: String
    val rowKeepScreenOn: String
    val rowUnits: String

    // ─── The sub-lines, drawn only when a row needs explaining ───────────────────────────────────

    val subSpeech: String

    /**
     * The 音声 row's disabled reason when the engine has no voice for the app's language.
     *
     * **Keyed for the absence, not for the language.** The Japanese sentence names 日本語 because a
     * Japanese UI probes for a Japanese voice, and it is transcribed exactly as it ships. The English
     * must not name a language at all: the probe asks for whatever language the app is speaking, so
     * "no Japanese voice" under an English UI would report a question nobody asked. See the hazard note
     * on `GymSettingsScreen.probeResult`.
     */
    val subNoVoice: String

    val subNoTtsEngine: String
    val subAutoAdvance: String
    val subKeepScreenOn: String

    /** マナーモード is a Japan-specific device concept; English has only "silent mode". */
    val subSilentMode: String

    /** §B's `SessionLive` state: rest defaults and 支度 do not retro-apply to a compiled timeline. */
    val subNotThisSession: String

    // ─── The two lines that are not rows ────────────────────────────────────────────────────────

    val restDefaultsFootnote: String

    /** §B's `Error` state, and `DECISIONS.md` §Q6's `Rejected` copy. */
    val writeFailed: String

    // ─── Words that stand for a value ───────────────────────────────────────────────────────────

    /**
     * 入 / 切 — the toggle, which in this app is a word rather than a switch.
     *
     * Both the visible label and the `stateDescription`, from one place, because §B requires that "the
     * visible word and the state description must always agree" — two literals is how they stop
     * agreeing, and two *tables* would be the same mistake one layer up.
     */
    val toggleOn: String
    val toggleOff: String

    /** なし — zero seconds is a real, selectable wheel row, and 〇秒 would prescribe nothing. */
    val secondsNone: String

    /**
     * `Units`' display words. The enum stores `.name` (`GymPreferencesRepository.kt:82`), so these are
     * display-only and carry nothing to disk — see `Units.label(Strings)`.
     */
    val unitsMetric: String
    val unitsImperial: String

    // ─── GYM.SAFETY ─────────────────────────────────────────────────────────────────────────────

    val safetyTitle: String

    /**
     * The one line `GYM.SAFETY` has. Its four paragraphs are missing because no spec contains them, and
     * safety prose is the last copy in this app that may be improvised — in either language.
     */
    val safetyLine: String
}

internal object JaGymSettings : GymSettingsStrings {

    override val title = "設定"
    override val subtitle = "鍛錬のふるまい"
    override val backAction = "とじる"

    override val sectionCues = "合図"
    override val sectionProgress = "進行"
    override val sectionRestDefaults = "休息の初期値"
    override val sectionDisplay = "表示"

    override val rowHaptics = "振動"
    override val rowTones = "音"
    override val rowSpeech = "音声"
    override val rowAutoAdvanceReps = "目安で自動的に進む"
    override val rowPrepareSeconds = "支度の長さ"
    override val rowStationRest = "種目の間"
    override val rowRoundRest = "巡の間"
    override val rowKeepScreenOn = "画面を消さない"
    override val rowUnits = "単位"

    override val subSpeech = "種目の名前を読み上げる"
    override val subNoVoice = "日本語の音声が入っていません"
    override val subNoTtsEngine = "読み上げ機能がありません"
    override val subAutoAdvance = "回数の種目でも時間が来たら次へ"
    override val subKeepScreenOn = "運動中だけ"
    override val subSilentMode = "マナーモードでも鳴ります"
    override val subNotThisSession = "いまの鍛錬には反映されません"

    override val restDefaultsFootnote = "これから作る型に使われます"
    override val writeFailed = "保存できませんでした"

    override val toggleOn = "入"
    override val toggleOff = "切"

    override val secondsNone = "なし"

    override val unitsMetric = "メートル法"
    override val unitsImperial = "ヤード・ポンド法"

    override val safetyTitle = "安全のために"
    override val safetyLine = "痛みを感じたらやめる"
}

internal object EnGymSettings : GymSettingsStrings {

    override val title = "Settings"

    // 鍛錬のふるまい — "how 鍛錬 behaves". 鍛錬 is Training in the dock, and the subtitle names the same
    // thing the tab does rather than inventing a second word for it.
    override val subtitle = "How training behaves"

    override val backAction = "Close"

    override val sectionCues = "Cues"
    override val sectionProgress = "Progress"

    // 休息の初期値 is "the initial value of rest" — a default, which is the whole point of the footnote
    // below. "Rest defaults" reads as a category; "Default rest" reads as the thing being set.
    override val sectionRestDefaults = "Default rest"
    override val sectionDisplay = "Display"

    override val rowHaptics = "Vibration"
    override val rowTones = "Sound"
    override val rowSpeech = "Speech"

    // 目安で自動的に進む — "advance automatically, by the estimate". The estimate half is what the
    // sub-line below explains, so the row keeps the short half: this row and its value word share a
    // 56.dp line and the label is the only part that can grow.
    override val rowAutoAdvanceReps = "Advance automatically"

    override val rowPrepareSeconds = "Get-ready time"
    override val rowStationRest = "Between stations"
    override val rowRoundRest = "Between rounds"
    override val rowKeepScreenOn = "Keep the screen on"
    override val rowUnits = "Units"

    override val subSpeech = "Speaks each station's name"

    // Deliberately does not name a language — see the interface KDoc.
    override val subNoVoice = "No voice installed"

    override val subNoTtsEngine = "No speech engine"
    override val subAutoAdvance = "Moves on when the time is up, even on rep stations"
    override val subKeepScreenOn = "Only while exercising"
    override val subSilentMode = "Plays even in silent mode"
    override val subNotThisSession = "Not applied to the workout in progress"

    // 型 is a routine. The sentence exists to say that changing a default never rewrites a routine that
    // already exists, so "from now on" is the load-bearing half and stays.
    override val restDefaultsFootnote = "Used by routines you make from now on"

    // 保存できませんでした. It does not apologise in Japanese and it does not apologise here.
    override val writeFailed = "Couldn't save"

    override val toggleOn = "On"
    override val toggleOff = "Off"

    override val secondsNone = "None"

    override val unitsMetric = "Metric"
    override val unitsImperial = "Imperial"

    // 安全のために — "for the sake of safety". A page title rather than a warning; the page is careful
    // rather than alarming, and an English title in the imperative would change that.
    override val safetyTitle = "Staying safe"
    override val safetyLine = "Stop if it hurts"
}
