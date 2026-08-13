package io.eddiegulay.tempo.i18n

/**
 * Failure copy, app-wide. A user meets these when something is already wrong.
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
 * **The one rule that is specific to this namespace: loading ≠ empty ≠ failed.** A read that failed
 * and a read that returned nothing are the same picture on screen and opposite claims about the user's
 * data, and this codebase has caught the conflation five separate times. Every message here that means
 * "we could not read it" is phrased so that it *cannot* be misread as "there is none" — 記録を読めません
 * carries no ありません, and "Cannot read your records" carries no *no*, *none*, *empty* or *nothing*.
 * That is not a style preference; `CalendarFeedbackTest` fails the build over it, in both languages.
 */
interface FaultStrings {

    /**
     * The way out offered by every fault a retry could actually clear. One word, four call sites.
     *
     * Deliberately *not* offered by the faults it could only fail against again — a full disk, a
     * refused row, a routine that is gone. A button that cannot work is worse than no button.
     */
    val retry: String

    /**
     * The catch-all for a fault family nobody has written yet.
     *
     * Unreachable today and not a placeholder: `TempoFault`'s doctrine permits a third family to be
     * vague but never silent, so this exists to keep "every fault says something" true through a
     * change that has not happened.
     */
    val unknownFamily: String

    /**
     * Launching or uninstalling an app failed.
     *
     * These are toasts raised from `AppRepository`, which is the one place in the app where copy is
     * needed outside both Compose and a ViewModel — so the table is threaded in as a parameter rather
     * than read from a local. Neither is a [TempoFault]: they are terminal, there is nothing to
     * retry, and the launcher has no idea why the system refused.
     */
    val launchFailed: String
    val uninstallFailed: String

    val calendar: CalendarFaultStrings
    val gym: GymFaultStrings
}

/**
 * The calendar's failures, phrased as the remedy rather than the exception.
 *
 * Each `…Action` is the word on the tap target beside its message. Where there is no `…Action` there
 * is genuinely nothing to press, and the message still has to explain the dead end — an explained
 * dead end is not a dead end, because the user knows to go elsewhere.
 */
interface CalendarFaultStrings {

    /** Access refused or revoked. The action asks again; `CalendarScreen` overrides the word when the system will no longer prompt. */
    val permissionLost: String
    val permissionLostAction: String

    /** No account, or every calendar on the device is read-only. Adding an account is the only real fix. */
    val noWritableCalendar: String
    val noWritableCalendarAction: String

    /** Deleted elsewhere while the composer held it. Two sentences: what happened, and the likely why. */
    val eventGone: String
    val eventGoneAction: String

    /** The provider refused the values. Retrying is still worth it, so this takes [FaultStrings.retry]. */
    val rejected: String

    /** Nothing on the device can display an event. The one calendar fault with no action at all. */
    val noCalendarApp: String

    /** Anything unforeseen. Shown rather than swallowed, and retryable. */
    val unknown: String
}

/**
 * 鍛錬's failures.
 *
 * [storeUnreadable] is the load-bearing sentence in this whole namespace, and the reason is what it
 * must not be mistaken for. A store we could not read and a store with nothing in it are one pixel
 * apart on screen and worlds apart in meaning: told 記録はありません, a user believes their training
 * history does not exist and stops looking for it. So the sentence says *we* failed, and says it in
 * words that carry no claim about existence at all — 読めません with no ありません, "cannot read" with
 * no *no*. Four different faults share it because the remedy is the same for all four.
 */
interface GymFaultStrings {

    /** `StoreCorrupt`, `StoreUnavailable`, `StoreReset`, `Unknown` — the store is there and we failed to read it. */
    val storeUnreadable: String

    /** Split from [storeUnreadable] because the remedy is free space, and no retry can supply it. */
    val storeFull: String

    /** Deleted while its page was open. The page leaves, so there is nothing left here to press. */
    val routineGone: String

    /** Deleted from another page while this one held its key. */
    val sessionGone: String

    /** A CHECK constraint refused the row: the same draft will be refused again, so no retry. */
    val rejected: String
}

internal object JaFault : FaultStrings {

    override val launchFailed = "起動できませんでした"
    override val uninstallFailed = "アンインストールできませんでした"


    override val retry = "もう一度"
    override val unknownFamily = "うまくいきませんでした"

    override val calendar = object : CalendarFaultStrings {
        override val permissionLost = "カレンダーへのアクセスが必要です"
        override val permissionLostAction = "許可する"

        override val noWritableCalendar = "書き込めるカレンダーがありません"
        override val noWritableCalendarAction = "アカウントを追加"

        override val eventGone = "この予定はもうありません。ほかの端末で削除されたようです"
        override val eventGoneAction = "予定へ戻る"

        override val rejected = "この予定を保存できませんでした"
        override val noCalendarApp = "カレンダーのアプリが見つかりません"
        override val unknown = "カレンダーにつながりません"
    }

    override val gym = object : GymFaultStrings {
        override val storeUnreadable = "記録を読めません"
        override val storeFull = "空き容量が足りません"
        override val routineGone = "この型は削除されています"
        override val sessionGone = "この記録は削除されています"
        override val rejected = "保存できませんでした"
    }
}

internal object EnFault : FaultStrings {

    override val launchFailed = "Couldn't open that app"
    override val uninstallFailed = "Couldn't uninstall that app"


    // もう一度 is literally "once more". "Try again" is the English of the *gesture*, which is what the
    // word is doing here — it labels a tap target, not a sentiment.
    override val retry = "Try again"

    // うまくいきませんでした does not apologise and does not exclaim. Neither does this: no "Oops", no
    // "Sorry", no exclamation mark. It is the vaguest string in the app and it is still a statement.
    override val unknownFamily = "Something went wrong"

    override val calendar = object : CalendarFaultStrings {
        override val permissionLost = "Tempo needs access to your calendar"
        override val permissionLostAction = "Allow"

        // The Japanese names the *condition* — no calendar that can be written to — and the action
        // names the fix. Both halves are kept: "no calendar" here is a true claim about the device's
        // accounts, not a claim about the user's events, which is the distinction this namespace
        // exists to police.
        override val noWritableCalendar = "No calendar can accept new events"
        override val noWritableCalendarAction = "Add an account"

        // Two sentences in Japanese and two here. ようです is a hedge — the app infers the deletion
        // from an absent row and does not know it — so "seems to have been" keeps the hedge rather
        // than upgrading a guess to a fact.
        override val eventGone = "This event is gone. It seems to have been deleted on another device"
        override val eventGoneAction = "Back to events"

        override val rejected = "This event could not be saved"
        override val noCalendarApp = "No calendar app found"

        // つながりません is "cannot connect". "Cannot reach" says the same thing without implying the
        // network, which is only one of the ways this fault arrives.
        override val unknown = "Cannot reach the calendar"
    }

    override val gym = object : GymFaultStrings {
        /*
         * **The sentence the doctrine is about.** Rejected phrasings, each of which reads as emptiness
         * at a glance and would re-introduce the exact bug `GymFault` exists to prevent:
         *
         * - "No records available" — indistinguishable from an empty history.
         * - "Records unavailable" — ambiguous between "we failed" and "you have none".
         * - "Nothing to show" — a claim about the data, made by code that could not read the data.
         *
         * "Cannot read your records" is the same shape as 記録を読めません: the records are the object,
         * the failure is ours, and nothing in it denies that they exist. It shares its subject noun
         * with [sessionGone] on purpose — one word for 記録 throughout.
         */
        override val storeUnreadable = "Cannot read your records"

        override val storeFull = "Not enough free space"
        override val routineGone = "This routine has been deleted"
        override val sessionGone = "This record has been deleted"

        // The calendar's twin says "This event could not be saved"; the gym's Japanese is the bare
        // 保存できませんでした with no subject, and the bare English keeps that difference.
        override val rejected = "Could not be saved"
    }
}
