package io.eddiegulay.tempo.i18n

/**
 * The block/unblock commitment dialogs.
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
interface BlockStrings {

    /**
     * The confirm dialog's title, which is also its confirm button — one string in two places, so it
     * is one member and cannot drift.
     *
     * `days` is `BlockadeRepository.BLOCK_DAYS`.
     */
    fun confirmHeading(days: Int): String

    /**
     * The consequence, spelled out before the checkbox unlocks the button. Two sentences: what is
     * lost, and that reinstalling does not buy it back.
     *
     * `appLabel` is another app's own name and is never translated — it is quoted rather than
     * inflected into the sentence for exactly that reason.
     */
    fun confirmBody(appLabel: String, days: Int): String

    val confirmAcknowledge: String
    val confirmCancel: String

    /** The dialog raised by tapping an app whose block has not elapsed. */
    val blockedHeading: String

    /**
     * The lead line above the live countdown.
     *
     * In Japanese the clause ends on まで and is *finished by the countdown below it* — the two are one
     * sentence laid out on two lines. English keeps that shape ("Until X unlocks", then the clock).
     */
    fun blockedBody(appLabel: String): String

    val blockedFootnote: String
    val blockedDismiss: String
}

internal object JaBlock : BlockStrings {

    // 間 is appended to the counter, not part of it: fmt.days gives 十日 (a count) where these
    // sentences want 十日間 (a span).
    override fun confirmHeading(days: Int) = "${JaFormats.days(days)}間ふうじる"

    override fun confirmBody(appLabel: String, days: Int) =
        "「$appLabel」を非表示にすると、${JaFormats.days(days)}間は元に戻せません。" +
            "アプリを削除して入れ直しても、期間が終わるまで解除されません。"

    override val confirmAcknowledge = "理解しました"
    override val confirmCancel = "やめる"

    override val blockedHeading = "まだ解除できません"
    override fun blockedBody(appLabel: String) = "「$appLabel」のふうじが解けるまで"
    override val blockedFootnote = "アプリを削除しても期間は続きます"
    override val blockedDismiss = "わかりました"
}

internal object EnBlock : BlockStrings {

    override fun confirmHeading(days: Int) = "Hide for ${EnFormats.days(days)}"

    // 「」 corner brackets around another app's name become typographic double quotes, which is what
    // they are for. The Japanese is two sentences and this is two sentences; it does not apologise
    // and it does not repeat the rule a third time.
    override fun confirmBody(appLabel: String, days: Int) =
        "Hiding “$appLabel” locks it for ${EnFormats.days(days)}. " +
            "Deleting and reinstalling the app will not unlock it early."

    override val confirmAcknowledge = "I understand"
    override val confirmCancel = "Cancel"

    override val blockedHeading = "Still hidden"
    override fun blockedBody(appLabel: String) = "Until “$appLabel” unlocks"
    // "it" is the countdown directly above this line, which is why the line can be this short.
    override val blockedFootnote = "Deleting the app does not stop it"
    override val blockedDismiss = "OK"
}
