package io.eddiegulay.tempo.i18n

/**
 * The hidden-apps page and its commitment copy.
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
interface FilterStrings {

    /**
     * The faint kana super-title above the page name, and the one string here that is **null in
     * English on purpose**.
     *
     * 「ひひょうじ」 is the *reading* of 非表示 set above it — furigana used as an eyebrow. It is a
     * typographic device, not a second sentence: the two lines say the same word in two scripts, and
     * the whole point is that one of them is a pronunciation guide. English has no reading to gloss,
     * so translating it literally prints "hidden" above "Hidden apps", which is not the device — it is
     * the device broken. The page drops the line instead.
     *
     * `SearchScreen`'s 「けんさく」 is the same construction and wants the same answer; that file
     * belongs to another unit.
     */
    val kana: String?

    val title: String

    /**
     * The rule under the title, e.g. 非表示にすると十日間は解除できません.
     *
     * Takes the day count rather than baking it in. The page shipped a hard-coded `10` here while both
     * block dialogs interpolated `BlockadeRepository.BLOCK_DAYS`, so the two could disagree the moment
     * the constant moved — pre-existing drift that a literal translation would have set in two
     * languages instead of one.
     */
    fun subtitle(days: Int): String

    /** A row whose block has elapsed: tapping it restores the app. */
    val rowUnlockable: String

    /**
     * A row still inside its block, wrapped around an already-formatted remaining-time fragment.
     *
     * A function rather than a prefix string because the position is not the same in both languages:
     * Japanese leads with あと, English trails with "left".
     */
    fun rowRemaining(label: String): String
}

internal object JaFilter : FilterStrings {
    override val kana = "ひひょうじ"
    override val title = "非表示アプリ"

    // 間 is appended to the counter rather than being part of it: fmt.days gives 十日 (a count of
    // days) and this sentence wants 十日間 (a span of days). Same word, different counter.
    override fun subtitle(days: Int) = "非表示にすると${JaFormats.days(days)}間は解除できません"

    override val rowUnlockable = "解除できます"
    override fun rowRemaining(label: String) = "あと$label"
}

internal object EnFilter : FilterStrings {
    override val kana: String? = null
    override val title = "Hidden apps"
    override fun subtitle(days: Int) = "Hiding an app locks it for ${EnFormats.days(days)}"
    override val rowUnlockable = "Ready to unhide"
    override fun rowRemaining(label: String) = "$label left"
}
