package io.eddiegulay.tempo.i18n

/**
 * Home: the clock, its reading, the corner cluster and the 静 seal.
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
 * Home is four strings and a layout. Everything the corner draws is a date or a time and therefore
 * lives on [Formats]; the 静 seal is a *mark* rather than a word and stays a literal at its draw site
 * in both languages (see `HomeScreen.Seal`). What is left is three accessibility strings the user
 * never reads and one word — 終日 — that is drawn.
 */
interface HomeStrings {

    /**
     * The long-press on the clock, which is the deliberate way out of the launcher and into 集中 or
     * 鍛錬.
     *
     * Accessibility-only: the gesture has no visual affordance whatsoever, so this label is the sole
     * naming of the app's main entry point. Left in Japanese under an English UI it is not merely
     * untranslated, it hides the control.
     */
    val chooseMode: String

    /** The corner's `onClick` label — it is the only way into the Calendar page. TalkBack-only. */
    val openSchedule: String

    /**
     * An event with no clock time, drawn in the corner where the time would be.
     *
     * The same word as `calendar.event.allDay`, duplicated rather than shared: two namespaces owning
     * one literal is cheaper than one namespace reaching into another, and the day someone shortens
     * Home's corner they should not have to think about the Calendar page.
     */
    val allDay: String

    /**
     * Opens the corner's spoken description, which is then composed with the day, the time and the
     * event's own title — user data, in whatever language it was written.
     *
     * A prefix rather than a whole sentence because three of the four parts come from elsewhere; the
     * joins use `fmt.listSeparator`, which is 、 in Japanese and a comma in English.
     */
    val nextEventPrefix: String
}

internal object JaHome : HomeStrings {
    override val chooseMode = "モードを選ぶ"
    override val openSchedule = "予定"
    override val allDay = "終日"
    override val nextEventPrefix = "次の予定"
}

internal object EnHome : HomeStrings {
    override val chooseMode = "Choose a mode"
    override val openSchedule = "Schedule"
    override val allDay = "All day"
    override val nextEventPrefix = "Next event"
}
