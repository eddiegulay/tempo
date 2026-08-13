package io.eddiegulay.tempo.i18n

/**
 * 集中 — the full-screen pomodoro clock.
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
 */
interface FocusStrings {

    /**
     * The three pomodoro phases.
     *
     * These used to be constructor arguments on `PomodoroPhase` — resolved once at class-init, which
     * means they would have kept whatever language the app started in across a language switch (§L3).
     * They are read through a `PomodoroPhase.label(Strings)` extension now, exactly as `Tier` is.
     *
     * They are also the app's tightest typographic slot for Latin: the label draws at 26.sp with
     * `letterSpacing = 8.sp`, which is 31% tracking. That is ordinary CJK heading typesetting for a
     * two-glyph word and it is unreadable applied to `Focus`, so the page makes the tracking
     * language-conditional rather than deleting it.
     */
    val phaseFocus: String
    val phaseShortBreak: String
    val phaseLongBreak: String

    /**
     * The gesture hint along the bottom edge — the only place either gesture is named.
     *
     * Both hold their own field separator, so both are composed against `fmt.separator` rather than
     * hard-coding a CJK middle dot into a sentence.
     */
    val hintClock: String
    val hintPomodoro: String

    /** The three controls under the clock. [controlRunning] / [controlPaused] are one toggle. */
    val controlReset: String
    val controlRunning: String
    val controlPaused: String
    val controlSkip: String
}

internal object JaFocus : FocusStrings {
    override val phaseFocus = "集中"
    override val phaseShortBreak = "休憩"
    override val phaseLongBreak = "長休憩"

    override val hintClock = "タップで秒" + JaFormats.separator + "長押しでポモドーロ"
    override val hintPomodoro = "タップで開始 / 一時停止" + JaFormats.separator + "長押しで時計"

    override val controlReset = "リセット"
    override val controlRunning = "計測中"
    override val controlPaused = "停止中"
    override val controlSkip = "スキップ"
}

internal object EnFocus : FocusStrings {
    override val phaseFocus = "Focus"

    // 休憩 / 長休憩 are "break" / "long break". The short one stays the bare word: it is the common
    // case, it sits under a 88.sp clock, and "Short break" beside "Long break" makes the reader
    // compare two adjectives when the clock beneath already says which one this is.
    override val phaseShortBreak = "Break"
    override val phaseLongBreak = "Long break"

    override val hintClock = "Tap for seconds" + EnFormats.separator + "Hold for Pomodoro"
    override val hintPomodoro = "Tap to start / pause" + EnFormats.separator + "Hold for the clock"

    override val controlReset = "Reset"
    override val controlRunning = "Running"
    override val controlPaused = "Paused"
    override val controlSkip = "Skip"
}
