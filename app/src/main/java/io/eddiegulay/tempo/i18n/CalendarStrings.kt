package io.eddiegulay.tempo.i18n

/**
 * The calendar page and the event composer.
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
 * One thing to know before adding to this namespace: **the calendar is the one surface where Tempo's
 * copy leaves Tempo.** `Events.TITLE` is written to a provider the Google sync adapter pushes to the
 * user's account, their other devices and every guest on the invite (§L10). Nothing here may ever
 * become the *value* of a write — see [untitled], which was exactly that bug.
 */
interface CalendarStrings {

    // ─── the page ───────────────────────────────────────────────────────────────────────────────

    /**
     * The page's own name. Not [headingView]: 予定 names the agenda here and one event there, and the
     * two words part company the moment they are translated.
     */
    val title: String

    /** The header action that opens an empty composer. */
    val add: String

    /**
     * "Add an event" — the header action's `contentDescription` *and* the composer's create heading.
     *
     * One member for two call sites because it is one phrase saying one thing; the button is icon-less
     * but silent to TalkBack without it, and the heading it leads to should agree word for word.
     */
    val addEvent: String

    /** The agenda is still being read. Distinct from [empty], which is a claim about the user's day. */
    val loading: String

    /**
     * There is nothing in the next fortnight.
     *
     * Only ever shown for a read that *succeeded*. A failed read renders a fault (`CalendarFeedback`),
     * because telling someone they have no meetings when we simply could not look is how they miss one.
     */
    val empty: String

    // ─── an event card ──────────────────────────────────────────────────────────────────────────

    /** An event with no clock: the card's time slot, and the composer's toggle label. */
    val allDay: String

    /**
     * A multi-day all-day event: 終日 ・ 六月十九日まで / All day · until 19 June.
     *
     * A function rather than a string because the date sits *inside* it and the word order does not
     * survive translation — Japanese suffixes まで to the date, English prefixes "until" to it, and no
     * separator-and-concatenation at the call site can be both. The date itself is still [Formats]'
     * work; only the words around it are copy.
     *
     * @param lastDay an already-formatted date, from `fmt.monthDay`.
     */
    fun allDayUntil(lastDay: String): String

    /** Flagged on the card because a recurring event opens a read-only composer. */
    val recurring: String

    /** This event is running right now — the page's one use of the accent. */
    val ongoing: String

    /**
     * What an event with no title *looks like*. **Never what an event with no title is.**
     *
     * This string was, until recently, substituted into `CalendarEvent.title` as the provider row was
     * read. From there it prefilled the composer, it was what made 保存 enabled, and it went into the
     * draft — so editing the *time* of an untitled event wrote `（無題）` into `Events.TITLE`, and the
     * sync adapter carried a Japanese word Tempo invented to the user's account, their other devices
     * and every guest on the invite. Language had nothing to do with it: translating the placeholder
     * would only have written an English one.
     *
     * It is now resolved at the point of drawing — `CalendarEvent.displayTitle`, the composer's
     * read-only title, and the confirmation dialog's summary — and nothing that can be saved holds it.
     * Keep it that way: if you find yourself putting this value into an [io.eddiegulay.tempo.calendar.EventDraft],
     * the bug is back.
     *
     * The Japanese uses **full-width** parentheses (U+FF08 / U+FF09), not ASCII.
     */
    val untitled: String

    // ─── the access gate ────────────────────────────────────────────────────────────────────────

    /** The tap-to-grant prompt's heading. */
    val accessTitle: String

    /**
     * The heading once the system will no longer show its dialog.
     *
     * A different sentence rather than a different button, because at that point the tap can only open
     * Settings, and a tap that throws the user out of the app has to say so before it does.
     */
    val accessDeniedTitle: String

    /** The gate's action while permission can still be asked for. */
    val accessGrant: String

    /** The gate's action once it can only hand off to Settings. */
    val accessOpenSettings: String

    // ─── the composer ───────────────────────────────────────────────────────────────────────────

    /** Heading for an event that can only be looked at — a recurring one, or a read-only calendar. */
    val headingView: String

    /** Heading when an existing event is open for editing. */
    val headingEdit: String

    /** Leaves a read-only event. Nothing was changed, so nothing is being abandoned. */
    val close: String

    /** Abandons a draft. */
    val cancel: String

    val save: String

    /** The save label while the write is in flight. Its description stays [save]. */
    val saving: String

    /** Why this event cannot be edited here, said before the user tries. */
    val recurringNotice: String

    /** The 終日 row's two states — drawn, and read out as the switch's `stateDescription`. */
    val toggleOn: String
    val toggleOff: String

    val fieldStart: String
    val fieldEnd: String

    /** The title field's placeholder and its `contentDescription`. */
    val fieldTitle: String

    val fieldLocation: String

    /** The chip group's label, shown only when the device has more than one writable calendar. */
    val fieldCalendar: String

    /** A chip's `stateDescription` when it is the chosen calendar; unselected chips say nothing. */
    val chipSelected: String

    /** The pressure valve: everything this composer deliberately cannot do lives in the calendar app. */
    val openInCalendarApp: String

    val delete: String
}

internal object JaCalendar : CalendarStrings {

    override val title = "予定"
    override val add = "加える"
    override val addEvent = "予定を加える"
    override val loading = "読み込み中"
    override val empty = "予定はありません"

    override val allDay = "終日"
    override fun allDayUntil(lastDay: String) = "終日 ・ ${lastDay}まで"
    override val recurring = "繰り返し"
    override val ongoing = "いま"
    override val untitled = "（無題）"

    override val accessTitle = "予定へのアクセス"
    override val accessDeniedTitle = "設定から許可してください"
    override val accessGrant = "タップして許可"
    override val accessOpenSettings = "設定を開く"

    override val headingView = "予定"
    override val headingEdit = "予定を編集"
    override val close = "とじる"
    override val cancel = "やめる"
    override val save = "保存"
    override val saving = "保存中"
    override val recurringNotice = "繰り返しの予定"

    override val toggleOn = "する"
    override val toggleOff = "しない"

    override val fieldStart = "開始"
    override val fieldEnd = "終了"
    override val fieldTitle = "題名"
    override val fieldLocation = "場所"
    override val fieldCalendar = "カレンダー"
    override val chipSelected = "選択中"

    override val openInCalendarApp = "カレンダーで開く"
    override val delete = "削除"
}

internal object EnCalendar : CalendarStrings {

    override val title = "Calendar"
    override val add = "Add"
    override val addEvent = "Add event"
    override val loading = "Loading"
    override val empty = "No events"

    override val allDay = "All day"
    override fun allDayUntil(lastDay: String) = "All day · until $lastDay"
    override val recurring = "Repeats"
    override val ongoing = "Now"

    /** Google Calendar's own English word for the same absence, so the two agree on the user's device. */
    override val untitled = "(No title)"

    override val accessTitle = "Calendar access"
    override val accessDeniedTitle = "Allow access in Settings"
    override val accessGrant = "Tap to allow"
    override val accessOpenSettings = "Open Settings"

    override val headingView = "Event"
    override val headingEdit = "Edit event"
    override val close = "Close"
    override val cancel = "Cancel"
    override val save = "Save"
    override val saving = "Saving"
    override val recurringNotice = "Repeating event"

    // The 終日 row is a two-state word, not a Material Switch, and it is announced with `Role.Switch`.
    // On/Off is what a switch says in English, spoken and drawn alike.
    override val toggleOn = "On"
    override val toggleOff = "Off"

    override val fieldStart = "Start"
    override val fieldEnd = "End"
    override val fieldTitle = "Title"
    override val fieldLocation = "Location"
    override val fieldCalendar = "Calendar"
    override val chipSelected = "Selected"

    override val openInCalendarApp = "Open in calendar"
    override val delete = "Delete"
}
