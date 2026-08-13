package io.eddiegulay.tempo.i18n

/**
 * The notification shade page.
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
 * Note what is *not* here: every notification's title, body, app name and action labels are the source
 * app's own text. None of it is ours and none of it follows this toggle (§L10).
 */
interface NotificationsStrings {

    val title: String

    /**
     * The swipe-to-dismiss action, announced to TalkBack and **drawn nowhere**.
     *
     * That makes it more load-bearing than a label, not less: the swipe gesture is invisible to
     * accessibility services, so this string is the only naming of the only way a screen-reader user
     * can clear a notification. Left in Japanese under an English UI the row is not merely
     * untranslated, it is unclearable.
     */
    val rowDismiss: String

    /** The row's activate action, also announcement-only. */
    val rowOpen: String

    val replyDescription: String
    val replyPlaceholder: String

    /**
     * A per-app group header, collapsed into one TalkBack announcement: app name, then how many.
     *
     * The count goes through `fmt.items` (件) — there is no notification-specific counter, and 件 is
     * the generic one the rest of the app uses for "how many things in this list".
     */
    fun groupHeader(appLabel: String, count: Int): String

    /** The expander on an over-long app bucket, and its counterpart once expanded. */
    fun more(hiddenCount: Int): String
    val collapse: String

    val clearAll: String

    /** The undo strip's claim about what just happened — past tense, and about N things. */
    fun undoCount(count: Int): String
    val undo: String

    /** The permission gate shown until notification-listener access is granted. */
    val accessTitle: String
    val accessAction: String

    val empty: String
}

internal object JaNotifications : NotificationsStrings {

    override val title = "通知"

    override val rowDismiss = "消去"
    override val rowOpen = "開く"

    override val replyDescription = "返信を入力"
    override val replyPlaceholder = "返信"

    override fun groupHeader(appLabel: String, count: Int) =
        appLabel + JaFormats.listSeparator + JaFormats.items(count)

    override fun more(hiddenCount: Int) = "他 " + JaFormats.items(hiddenCount)
    override val collapse = "折りたたむ"

    override val clearAll = "すべて消去"

    override fun undoCount(count: Int) = JaFormats.items(count) + "を消去"
    override val undo = "元に戻す"

    override val accessTitle = "通知へのアクセス"
    override val accessAction = "タップして許可"

    override val empty = "通知はありません"
}

internal object EnNotifications : NotificationsStrings {

    override val title = "Notifications"

    override val rowDismiss = "Dismiss"
    override val rowOpen = "Open"

    override val replyDescription = "Type a reply"
    override val replyPlaceholder = "Reply"

    override fun groupHeader(appLabel: String, count: Int) =
        appLabel + EnFormats.listSeparator + EnFormats.items(count)

    // A bare count, not "3 items more": the row it sits under already says what is being counted,
    // and this is a 13.sp line centred in a card list.
    override fun more(hiddenCount: Int) = "${EnFormats.count(hiddenCount)} more"
    override val collapse = "Show less"

    override val clearAll = "Clear all"

    override fun undoCount(count: Int) = "${EnFormats.items(count)} cleared"
    override val undo = "Undo"

    override val accessTitle = "Notification access"
    override val accessAction = "Tap to allow"

    override val empty = "No notifications"
}
