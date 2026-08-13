package io.eddiegulay.tempo.i18n

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The copy for the four small launcher namespaces — `filter`, `focus`, `block`, `notifications`.
 *
 * Two jobs, and they pull in opposite directions on purpose.
 *
 * **The Japanese half asserts that nothing moved.** These strings shipped as Kotlin literals in
 * `FilterScreen`, `FocusScreen`, the two block dialogs and `NotificationsScreen`; the migration
 * relocated them and is supposed to be invisible on a Japanese device. A transcription slip — a
 * dropped 、, a full-width space that became a half-width one — is exactly the kind of thing that
 * survives review and fails silently, so the exact strings are pinned here.
 *
 * **The English half asserts the things English needs and Japanese does not**: that counters
 * pluralise, that word order is per-language rather than a prefix glued on, and that the kana
 * super-title is *absent* rather than translated.
 *
 * Plain JUnit, no Compose and no `Context` — which is the whole reason the copy is a Kotlin table and
 * not `res/values-en/strings.xml` (§L2).
 */
class FilterFocusBlockNotificationsTest {

    private val ja = stringsFor(Lang.Ja)
    private val en = stringsFor(Lang.En)

    // ─── filter ─────────────────────────────────────────────────────────────────────────────────

    @Test
    fun `the hidden-apps subtitle is built from the block constant, not from a typed-in number`() {
        // The page hard-coded 10 here while both dialogs read BlockadeRepository.BLOCK_DAYS, so the
        // rule on screen and the rule enforced could disagree. Passing a different number is the
        // cheapest possible proof that it no longer can.
        assertEquals("非表示にすると三日間は解除できません", ja.filter.subtitle(3))
        assertEquals("Hiding an app locks it for 3 days", en.filter.subtitle(3))
    }

    @Test
    fun `the day count in the subtitle pluralises in English and does not in Japanese`() {
        assertEquals("Hiding an app locks it for 1 day", en.filter.subtitle(1))
        assertEquals("非表示にすると一日間は解除できません", ja.filter.subtitle(1))
    }

    @Test
    fun `the kana super-title is absent in English rather than translated`() {
        // ひひょうじ is the *reading* of 非表示 printed above it. English has no reading to gloss, and
        // "hidden" over "Hidden apps" is the device broken rather than the device translated.
        assertNotNull(ja.filter.kana)
        assertNull(en.filter.kana)
    }

    @Test
    fun `the remaining-time wrapper sits on the side of the number its language puts it`() {
        assertEquals("あと三日", ja.filter.rowRemaining(ja.fmt.days(3)))
        assertEquals("3 days left", en.filter.rowRemaining(en.fmt.days(3)))
    }

    // ─── focus ──────────────────────────────────────────────────────────────────────────────────

    @Test
    fun `the pomodoro phases read as they did`() {
        assertEquals("集中", ja.focus.phaseFocus)
        assertEquals("休憩", ja.focus.phaseShortBreak)
        assertEquals("長休憩", ja.focus.phaseLongBreak)
    }

    @Test
    fun `the gesture hints keep their separator and their exact Japanese`() {
        // Composed against fmt.separator rather than holding a CJK middle dot, so this also pins that
        // the composition still produces the string the app shipped, spaces included.
        assertEquals("タップで秒 ・ 長押しでポモドーロ", ja.focus.hintClock)
        assertEquals("タップで開始 / 一時停止 ・ 長押しで時計", ja.focus.hintPomodoro)
        assertEquals("Tap for seconds · Hold for Pomodoro", en.focus.hintClock)
    }

    // ─── block ──────────────────────────────────────────────────────────────────────────────────

    @Test
    fun `the confirm heading keeps the span counter in Japanese and pluralises in English`() {
        // 十日間 is a *span* of days; fmt.days gives 十日, a count. The 間 is the difference and it
        // has to survive, because 「十日ふうじる」 is not what the dialog said.
        assertEquals("十日間ふうじる", ja.block.confirmHeading(10))
        assertEquals("Hide for 10 days", en.block.confirmHeading(10))
        assertEquals("Hide for 1 day", en.block.confirmHeading(1))
    }

    @Test
    fun `the confirm body quotes the app name and states the consequence twice over`() {
        assertEquals(
            "「Chrome」を非表示にすると、十日間は元に戻せません。" +
                "アプリを削除して入れ直しても、期間が終わるまで解除されません。",
            ja.block.confirmBody("Chrome", 10),
        )
        assertEquals(
            "Hiding “Chrome” locks it for 10 days. " +
                "Deleting and reinstalling the app will not unlock it early.",
            en.block.confirmBody("Chrome", 10),
        )
    }

    @Test
    fun `the blocked dialog's lead line still ends where the countdown picks it up`() {
        assertEquals("「Chrome」のふうじが解けるまで", ja.block.blockedBody("Chrome"))
        assertEquals("Until “Chrome” unlocks", en.block.blockedBody("Chrome"))
    }

    // ─── notifications ──────────────────────────────────────────────────────────────────────────

    @Test
    fun `the swipe-dismiss action is named in both languages`() {
        // Drawn nowhere, and the sole naming of the only way a screen-reader user can clear a row.
        assertEquals("消去", ja.notifications.rowDismiss)
        assertEquals("Dismiss", en.notifications.rowDismiss)
    }

    @Test
    fun `the group header joins the app name and the count with its own list separator`() {
        assertEquals("Gmail、三件", ja.notifications.groupHeader("Gmail", 3))
        assertEquals("Gmail, 3 items", en.notifications.groupHeader("Gmail", 3))
    }

    @Test
    fun `the collapse toggle counts what is hidden`() {
        assertEquals("他 三件", ja.notifications.more(3))
        assertEquals("3 more", en.notifications.more(3))
        assertEquals("1 more", en.notifications.more(1))
        assertEquals("折りたたむ", ja.notifications.collapse)
    }

    @Test
    fun `the undo strip pluralises its past-tense claim`() {
        assertEquals("一件を消去", ja.notifications.undoCount(1))
        assertEquals("三件を消去", ja.notifications.undoCount(3))
        assertEquals("1 item cleared", en.notifications.undoCount(1))
        assertEquals("3 items cleared", en.notifications.undoCount(3))
    }

    @Test
    fun `the quiet state and the permission gate read as they did`() {
        assertEquals("通知はありません", ja.notifications.empty)
        assertEquals("通知へのアクセス", ja.notifications.accessTitle)
        assertEquals("タップして許可", ja.notifications.accessAction)
    }
}
