package io.eddiegulay.tempo.ui.gym

import io.eddiegulay.tempo.calendar.Loadable
import io.eddiegulay.tempo.data.GymFault
import io.eddiegulay.tempo.data.TempoFault
import io.eddiegulay.tempo.gym.BestMetric
import io.eddiegulay.tempo.gym.Engine
import io.eddiegulay.tempo.gym.GymHomeFeed
import io.eddiegulay.tempo.gym.LastResult
import io.eddiegulay.tempo.gym.PersistedClock
import io.eddiegulay.tempo.gym.Phase
import io.eddiegulay.tempo.gym.ResumableSession
import io.eddiegulay.tempo.gym.Resumability
import io.eddiegulay.tempo.gym.RoutineBest
import io.eddiegulay.tempo.gym.RoutineCard
import io.eddiegulay.tempo.gym.SegmentResult
import io.eddiegulay.tempo.gym.data.recoveredActiveMs
import io.eddiegulay.tempo.gym.displayName
import io.eddiegulay.tempo.gym.homeBuiltIn
import io.eddiegulay.tempo.i18n.Strings
import io.eddiegulay.tempo.ui.faultHasAction
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/*
 * Every word and every number `GYM.HOME` and the resume prompt draw, decided here rather than in a
 * composable — `00-plan.md` §4.1 rule 10, and the same bargain `GymMotion.kt` and `KeepAwake.kt` make
 * two files over: a decision made inside a `@Composable` can only be checked by launching an emulator.
 *
 * Android-free, so `GymHomeCopyTest` asserts the copy tables directly. `GymPageState.kt` is the
 * sibling that shapes the same page's *data* and deliberately returns no strings; this file is the
 * other half and deliberately owns nothing but strings and the arithmetic behind them. Both were kept
 * apart because the ViewModel unit owns one and the page unit owns the other.
 *
 * **Every string below is quoted from a spec table** — `01-shell.md` §B's layout and pure-logic list,
 * `03-player.md` §A's resume prompt, `04-library-records.md` §6, `exercise-design.md` §12 — and where a
 * case has no documented copy the function returns null so the surface omits the line. Nothing here is
 * invented, and the three places that came closest are named in KDoc where they occur ([bestLine]'s
 * `HIGHEST_STEP`, [stalenessLabel]'s bucket edges, [lastResultLine]'s engine split).
 *
 * **No word below is a literal any more, and none of them was a constant to begin with**
 * (`.planning/i18n/DECISIONS.md` §L2, §L4). This file used to be seventeen functions gluing a numeral
 * to a suffix with `+`; every one of them now *selects* a whole composed string from
 * `strings.gymHome` and hands it a value that came from `strings.fmt`. The numerals, the durations,
 * the separators and the relative days are all the formatter's — a `when` that selects is translatable
 * by replacing a table, and a `when` that concatenates is not.
 *
 * **Every function takes `strings: Strings` as a parameter**, never a global and never a composition
 * local: `GymHomeCopyTest` is plain JUnit with no Compose and no `Context`, and passing the table is
 * what keeps it there.
 */

// ─── The page's sections ────────────────────────────────────────────────────────────────────────

/**
 * Which of `GYM.HOME`'s three lists a section is.
 *
 * The kind rather than the label is what the page branches on, because two of the three differ in more
 * than their heading: 型 carries すべて見る, and 自分の型 has an inline empty state that the other two
 * must never render (`01-shell.md` §B's `Empty` state is **section-scoped only** — the page is never
 * globally empty, since the built-in 型 are always seeded).
 */
enum class HomeSectionKind { Frequent, BuiltIn, User }

/**
 * One heading and the cards under it.
 *
 * [keyPrefix] exists because a routine legitimately appears **twice on this page** — 七分間 under
 * よく使う and again under 型 — so a bare routine id is a duplicate `LazyColumn` key and Compose will
 * throw. `01-shell.md` §B edge case 6 fixes the shape as `"builtin:$id"` / `"user:$id"`; よく使う needs
 * the same treatment for the same reason, and it is the section that supplies the prefix rather than
 * the call site so it cannot be got wrong twice.
 *
 * @param seeAll whether すべて見る belongs beside the heading — 型 only, and only when the preview is
 *   genuinely hiding something.
 */
data class HomeSection(
    val kind: HomeSectionKind,
    val label: String,
    val cards: List<RoutineCard>,
    val seeAll: Boolean = false,
) {
    val keyPrefix: String
        get() = when (kind) {
            HomeSectionKind.Frequent -> "frequent"
            HomeSectionKind.BuiltIn -> "builtin"
            HomeSectionKind.User -> "user"
        }
}

/**
 * `GYM.HOME`'s body, section by section, in the order §B's layout draws them.
 *
 * Three rules, each from a different line of the spec:
 *
 * 1. **よく使う is absent on a first run** — there is no history to rank, and §B's `FirstRun` state
 *    spends the space it would have taken on two more 型 instead (which [homeBuiltIn] has already
 *    done by the time the feed reaches here). It is also absent when the ranking came back empty,
 *    because a heading over nothing is a heading that says the app is broken.
 * 2. **自分の型 is always present**, even at zero cards. Its emptiness is the one thing on this page
 *    that gets said out loud (型はまだありません, inline, 56.dp, *inside* the section), and a section
 *    that vanished when empty would leave a first-time user with no 作る anywhere but the header.
 * 3. **すべて見る appears only when the preview is short of the total.** `builtInTotal` is untouched
 *    by either preview width, so this asks the same question in the first-run and ordinary shapes.
 */
fun homeSections(feed: GymHomeFeed, strings: Strings): List<HomeSection> {
    val builtIn = homeBuiltIn(feed)
    val copy = strings.gymHome
    return buildList {
        if (feed.everTrained && feed.recent.isNotEmpty()) {
            add(HomeSection(HomeSectionKind.Frequent, copy.sectionFrequent, feed.recent))
        }
        add(
            HomeSection(
                kind = HomeSectionKind.BuiltIn,
                label = copy.sectionBuiltIn,
                cards = builtIn,
                seeAll = feed.builtInTotal > builtIn.size,
            ),
        )
        add(HomeSection(HomeSectionKind.User, copy.sectionUser, feed.userRoutines))
    }
}

// ─── A card's three lines ───────────────────────────────────────────────────────────────────────

/**
 * 前回 六分五十秒 ・ 三日前 — or 前回 十五巡 ・ 六日前, because what "last time" *means* is
 * engine-dependent (`01-shell.md` §B's layout draws both, and [LastResult] carries an [Engine] for no
 * other reason).
 *
 * **The engine split is [RecordCopy]'s, reused rather than re-derived.** `comparisonCopy` suppresses a
 * *time* comparison for AMRAP and EMOM because both run to a fixed cap and are scored in rounds; those
 * are exactly the two engines whose last outing is a round count here. `EMOM_ASCENDING` is not one of
 * them — a デス・バイ is scored on how long you survived, so its duration is the number that moved.
 *
 * Zero rounds fall back to the duration rather than printing 〇巡 (§B edge case 3's rule, applied to
 * the fragment rather than the line: zero is an inapplicability, not a result).
 *
 * @param today the day to measure 三日前 against. The caller passes one derived from the same clock
 *   the header's date is drawn from, so the card and the header can never disagree about what day it is.
 * @param strings the active table. A parameter rather than a lookup, so this stays a JVM test (§L4).
 */
fun lastResultLine(result: LastResult?, today: LocalDate, zone: ZoneId, strings: Strings): String? {
    if (result == null) return null
    val roundsEngine = result.engine == Engine.AMRAP || result.engine == Engine.EMOM
    val value = if (roundsEngine && result.roundsCompleted > 0) {
        strings.fmt.rounds(result.roundsCompleted)
    } else {
        strings.fmt.durationFromMs(result.activeMs)
    }
    val day = Instant.ofEpochMilli(result.startedAt).atZone(zone).toLocalDate()
    return strings.gymHome.lastResult(value, strings.fmt.relativeDay(day, today))
}

/**
 * 最高 十七巡 — the one record line a home card carries, or null when there is nothing to say.
 *
 * The units are each metric's own documented word: 巡 and 回 from design §12's tiles, 最速 from
 * `04-library-records.md` §6's best-tile list for `BEST_TIME` (最高 六分十四秒 would read as the
 * *longest* session, which is the opposite of the record), and 最高負荷 for `MOST_VOLUME` — deliberately
 * unitless, as §6 says of it.
 *
 * **`HIGHEST_STEP` returns null and that is a ruling, not an omission.** `DECISIONS.md` §Q9: no
 * Japanese label exists for it anywhere in the specs, nothing seeds it in v1, and none is to be
 * invented. The step a ladder has reached is rendered by `stepFor` as 第九段 / 十八段のうち on the
 * routine's own page, which is where a ladder belongs.
 *
 * A non-positive value is also null. 最高 〇巡 is not a record, and a home card with no PR simply has
 * one line fewer — card heights are not fixed (§B edge case 4).
 */
fun bestLine(best: RoutineBest?, strings: Strings): String? {
    if (best == null || best.value <= 0.0) return null
    val copy = strings.gymHome
    return when (best.metric) {
        BestMetric.MOST_ROUNDS -> copy.bestMost(strings.fmt.rounds(best.value.roundToInt()))
        BestMetric.MOST_REPS -> copy.bestMost(strings.fmt.reps(best.value.roundToInt()))
        BestMetric.BEST_TIME -> copy.bestFastest(strings.fmt.durationFromMs(best.value.roundToLong()))
        BestMetric.MOST_VOLUME -> copy.bestVolume(strings.fmt.count(best.value.roundToInt()))
        BestMetric.HIGHEST_STEP -> null
    }
}

/**
 * Whether 最高 draws in `c.accent` — the record was set **this calendar month** (§B edge case 5).
 *
 * Read from [RoutineBest.localDate], the day the store bucketed in Kotlin at write time from its own
 * monotonic-guarded clock, rather than re-deriving one from `achievedAt` and a zone. Re-bucketing a
 * timestamp SQLite has already bucketed is how a session lands on the wrong day (`00-plan.md` §2 row
 * 14), and doing it here would mean this page and the records page could disagree about which month a
 * PR belongs to.
 *
 * **A record dated in the future is never "this month".** That is the half of §B edge case 5 this page
 * can enforce alone: `GymRepository` exposes no guarded clock — `GymStore.now()` is private and on no
 * interface — so [today] is only as trustworthy as the system clock. Winding the clock *forward* past
 * a PR and back again therefore still repaints it, and closing that needs a repository surface this
 * unit does not own. Reported, not worked around.
 */
fun isPrThisMonth(best: RoutineBest?, today: LocalDate): Boolean {
    if (best == null) return false
    val set = best.localDate
    if (set.isAfter(today)) return false
    return set.year == today.year && set.month == today.month
}

/**
 * 十四回 — a card's outing count, or **null at zero**: the line is omitted, never rendered 〇回
 * (§B edge case 3).
 *
 * `fmt.times` rather than `fmt.reps`: 回 counts *occasions* here, not repetitions of a movement, and
 * English has two different words for the two.
 */
fun timesDoneLabel(count: Int, strings: Strings): String? =
    if (count <= 0) null else strings.fmt.times(count)

/*
 * **`formatElapsedJa` and `relativeDayJa` were deleted rather than translated**, and both had `Ja` in
 * their names for the reason the migration found them: they were this page's own copies of behaviour
 * that belongs to the formatter layer (§L8).
 *
 * - `formatElapsedJa` already delegated to `durationKanjiFromMs`, which is `fmt.durationFromMs`.
 * - `relativeDayJa` is `fmt.relativeDay`, which implements both languages — and where Japanese keeps
 *   two vocabularies on purpose (hiragana きょう/きのう looking backwards, kanji 今日/明日 looking
 *   forwards, both documented), **English has one word for both**. That collapse is §L7's ruling: a
 *   documented distinction deleted rather than translated, because there is nowhere for it to go.
 */

// ─── The つづき banner ──────────────────────────────────────────────────────────────────────────

/**
 * The fault the つづき slot owes words to, or null when there is nothing to say.
 *
 * **This function exists because the page used to answer a failed read with silence.** `resumable` is
 * a real [Loadable] — `GymStore` wraps `resumableSession()` in `read {}`, which folds any
 * `SQLiteException` into [Loadable.Failed] — but the page unwrapped it with `valueOrNull()` and
 * `homeResume` collapses both `Loading` and `Failed` to `ResumeAffordance.None`. An unreadable
 * open-session row therefore drew **exactly the pixels of a clean one**: no banner, no prompt, no
 * fault. That is not a degraded page, it is a page asserting the user has no interrupted workout, and
 * it is what `00-plan.md` §4.1 rule 1 exists to forbid.
 *
 * It is reachable on its own, not only as a symptom of a broken feed: `homeFeed` is a
 * `stateIn(WhileSubscribed)` flow that keeps its last `Ready` value, so a transient `SQLITE_BUSY` on
 * this one-shot read leaves a fully populated, entirely convincing page.
 *
 * **`Loading` is deliberately silent.** The row arrives in milliseconds and a strip that flashed on
 * every cold start would be the noise §A's "no flash" rule spends the `Loading` state on. Only
 * [Loadable.Failed] gets words — 記録を読めません, from `DECISIONS.md` §Q6 — and `refreshResumable()`
 * is what もう一度 re-runs, since the feed's own `retry()` does not touch this read.
 */
fun resumeFault(resumable: Loadable<ResumableSession?>): TempoFault? =
    (resumable as? Loadable.Failed)?.fault

/** 八種目まで進んだ — how far a session got, or null before the first station closed. */
fun progressLine(stations: Int, strings: Strings): String? =
    if (stations <= 0) null else strings.gymHome.progress(strings.fmt.stations(stations))

/**
 * How many stations a session actually reached, counted from the segments it persisted.
 *
 * Work and reps only: a rest is not a station, and 支度 never reaches `session_result` at all. A
 * **skipped** station still counts, because the sentence is まで進んだ — how far you got — and a
 * station you passed over is one you passed.
 */
fun stationsProgressed(results: List<SegmentResult>): Int =
    results.count { it.phase == Phase.WORK || it.phase == Phase.REPS }

/**
 * The elapsed time of a session that is **still running**, from the monotonic clock it was started on.
 *
 * The mirror of `recoveredActiveMs`, and the difference between them is the whole doctrine: a session
 * that stopped is credited only to its last persisted transition, because the gap after it is
 * unknowable and may have been spent in a pocket (`03-player.md` §A edge case 6). A session that is
 * *live in this process* has no such gap — the clock never stopped, `elapsedRealtime()` is the same
 * timebase it was anchored on, and §B's `ResumableSession` state asks for exactly this ("the clock kept
 * running while the shell was away").
 *
 * A paused session freezes at its pause anchor rather than continuing to climb.
 */
fun liveElapsedMs(clock: PersistedClock, nowElapsedMs: Long): Long {
    val at = clock.pausedAtElapsedMs ?: nowElapsedMs
    return (at - clock.startedAtElapsedMs - clock.pausedAccumulatedMs).coerceAtLeast(0L)
}

/**
 * The banner's single TalkBack node: `"つづき、七分間、六分十四秒 経過、八種目まで進んだ、続ける"`
 * (`01-shell.md` §B's accessibility section, verbatim).
 *
 * One node, never five, and the ticking figure announces **on focus only** — it is deliberately not an
 * `accessibilityLiveRegion`, because a per-second announcement is unusable and steals the TTS engine
 * the cues need (`00-plan.md` §4.1 rule 5).
 *
 * @param resumable whether 続ける is on the banner at all. It is removed, not disabled, when there is
 *   nothing to resume into, so the description must not promise it.
 */
fun resumeBannerDescription(
    routineName: String,
    elapsed: String?,
    progress: String?,
    resumable: Boolean,
    strings: Strings,
): String {
    val copy = strings.gymHome
    return listOfNotNull(
        copy.resumeBanner,
        routineName,
        elapsed?.let(copy::elapsed),
        progress,
        copy.resumeAction.takeIf { resumable },
    ).joinToString(strings.fmt.listSeparator)
}

/**
 * A card's single TalkBack node:
 * `"七分間、十二種目 ・ 三十秒 / 十秒 ・ 約七分、前回 六分五十秒、三日前、十四回、最高 十七巡"`.
 *
 * §B's example separates 前回 六分五十秒 from 三日前 with a comma where the visible line uses ・ — a
 * reading pause where the eye gets a separator — so the one line is re-punctuated rather than composed
 * twice. Composing it twice is how the two drift.
 *
 * The re-punctuation is `fmt.separator` → `fmt.listSeparator` rather than 「 ・ 」→「、」, which is why
 * [lastResultLine] must build its line through the formatter: a literal here would find nothing to
 * replace in English, where the same two marks are ` · ` and `, `.
 */
fun routineCardDescription(
    card: RoutineCard,
    lastLine: String?,
    bestLine: String?,
    strings: Strings,
): String = listOfNotNull(
    card.displayName(strings),
    routineCardSummary(card, strings),
    lastLine?.replace(strings.fmt.separator, strings.fmt.listSeparator),
    timesDoneLabel(card.timesDone, strings),
    bestLine,
).joinToString(strings.fmt.listSeparator)

// ─── 削除's confirmation ────────────────────────────────────────────────────────────────────────

/**
 * The two-branch delete dialog, `04-library-records.md` §6 verbatim.
 *
 * The branch is on whether anything readable would be destroyed, which is why it is the *session
 * count* and not the routine's kind: a routine with history is archived and its records survive
 * (§1 rule 2), one without is purged outright (§1 rule 4). The copy has to say which, because "削除"
 * means two different things and only one of them is reversible.
 *
 * **The words are `strings.gymLibrary.delete*`, not this namespace's.** Three surfaces raise this one
 * dialog — here, `GYM.LIBRARY.INDEX` and `GYM.LIBRARY.DETAIL` — from the same two rows of §6, and
 * three independent implementations of one sentence is the divergence `DECISIONS.md` §Q7 argues about
 * for numerals. The library's two pages already converged on one key set and named this function as
 * the remaining third copy; this is that convergence from the other end. The *shape* stays here
 * because the call site is this page's, and [DeleteRoutineCopy] carries three strings where the
 * library's own model carries five.
 */
data class DeleteRoutineCopy(val title: String, val body: String, val confirm: String)

fun deleteRoutineCopy(routineName: String, timesDone: Int, strings: Strings): DeleteRoutineCopy {
    val copy = strings.gymLibrary
    return DeleteRoutineCopy(
        title = copy.deleteTitle(routineName),
        body = if (timesDone > 0) {
            copy.deleteBodyArchive(strings.fmt.times(timesDone))
        } else {
            copy.deleteBodyPurge
        },
        confirm = if (timesDone > 0) copy.deleteConfirmArchive else copy.deleteConfirmPurge,
    )
}

/**
 * The count 削除's dialog may branch on — [RoutineCard.timesDone], **except** for the routine an open
 * session belongs to.
 *
 * The two numbers are not the same question. `timesDone` is `COUNT(*) … WHERE finished_at IS NOT
 * NULL`, so a routine whose only session is the one the user walked away from counts **zero**, and the
 * dialog then promised 「やった記録はありません。完全に消えます。」 over a 完全に削除 button. The purge
 * behind that button counts *all* sessions (`GymStore.purgeRoutine`'s guard) and refuses with
 * `RoutineHasHistory` — so the write was safe and the sentence was not. The dialog said the wrong
 * thing and the tap then failed.
 *
 * Raising the count to one for that routine puts it on the archive branch, where 「これまでの一回の
 * 記録は残ります」 is true of the row that made the purge refuse: the interrupted session is a record,
 * it survives the archive, and it is exactly the thing the user is being told about.
 *
 * [openRoutineId] is null when there is no open session **and** when the read that would have found
 * one failed. Unknown is not treated as "there is one": coercing every routine onto the archive branch
 * would print 一回 for routines that genuinely have no sessions at all, which trades a false
 * emptiness claim for a false count. The failed read is answered where a failed read belongs — the
 * つづき fault strip ([resumeFault]) — rather than by inventing history here.
 */
fun deleteRoutineCount(card: RoutineCard, openRoutineId: String?): Int =
    card.timesDone.coerceAtLeast(if (card.routineId == openRoutineId) 1 else 0)

// ─── A failed write ─────────────────────────────────────────────────────────────────────────────

/**
 * Whether a write fault leaves the user with **nothing to press**, and so needs a dismiss of its own.
 *
 * `DECISIONS.md` §Q6 gives `Rejected`, `RoutineGone`, `SessionGone` and `StoreFull` no action word,
 * and `FaultStrip` renders its tap target only when one exists. On `GYM.HOME` that pinned an
 * undismissable strip to the top of the feed for the rest of the visit — a failed 写して作る on a
 * routine that has left the library sets `RoutineGone`, and only leaving the shell cleared it.
 *
 * Delegates to [faultHasAction] rather than re-listing the four cases: a second table is how the two
 * drift, and the day §Q6 gains an action for one of them this predicate has to follow it. That helper
 * is `faultCopy`'s own language-independent half, so this stays a question about layout and takes no
 * `Strings` of its own; both are pure functions with a JVM test (`CalendarFeedbackTest`).
 */
fun writeFaultStuck(fault: GymFault): Boolean = !faultHasAction(fault)

// ─── The resume prompt ──────────────────────────────────────────────────────────────────────────

/** さっき (<10m) — the first of `03-player.md` §A edge case 4's five buckets, and its only stated edge. */
private const val JUST_NOW_MS = 10 * 60 * 1000L
private const val ONE_HOUR_BUCKET_MS = 2 * 60 * 60 * 1000L

/**
 * Where 二時間前 stops: the **resumability horizon**, `GymMath.STALE_AFTER_MS`.
 *
 * Not a new number — 4h is the edge `03-player.md` §A already draws between `Resumable` and `STALE`,
 * and past it the session has stopped being a workout you can walk back into. Restated here rather
 * than imported because `GymMath`'s copy is `private` and this file must not reach into it; the value
 * is pinned in `GymHomeCopyTest` against `resumability`'s own edge so the two cannot drift.
 */
private const val RESUMABLE_HORIZON_MS = 4 * 60 * 60 * 1000L
// No 48h constant: past the 4h horizon the day words come from `fmt.relativeDay`, which counts
// calendar days rather than elapsed hours. An hour-count edge there is precisely the bug review found.

/**
 * How stale the open session is, in the five words `03-player.md` §A edge case 4 permits:
 * `さっき` · `一時間前` · `二時間前` · `昨日` · `三日前`. **Never `2時間14分前`** — a number here invites
 * the user to do arithmetic about a workout they have already left.
 *
 * The edges are **10m / 2h / 4h / 48h**, and the two upper ones were moved here after review.
 *
 * **What was rejected, and why.** The first version bucketed `2h–24h → 二時間前` and `24h–48h → 昨日`,
 * defended as "the coarsest label that is not a lie". It is a lie: a session abandoned at 08:00 and
 * picked up at 22:00 the same day was announced as *two hours ago*, out by a factor of seven. §A edge
 * case 4's rule refuses false **precision** (`2時間14分前`); it does not license a false **statement**.
 * A coarse word may round; it may not multiply. So 二時間前 now stops at the resumability horizon —
 * past 4h the session is `STALE`, the prompt already says 続きからは できません, and the label is
 * context rather than a measurement — and the day words take over from there.
 *
 * **The 4h–24h gap is closed by delegation, not by a new word** (`DECISIONS.md` §Q13). Between four
 * and twenty-four hours none of §A edge case 4's five words is true — 昨日 is wrong for a five-hour-old
 * session begun this morning, exactly as 二時間前 was. The sixth word was not invented; it already
 * existed. `01-shell.md` :642 declares this feature's own relative-day formatter as
 * きょう / きのう / 三日前, and `fmt.relativeDay` implements it (in both languages). So past the horizon
 * this function stops measuring and asks *that* function what day it was.
 *
 * That also settles a split the two specs would otherwise have shipped side by side on one screen:
 * §A edge case 4 writes 昨日, `01` :642 writes きのう. **One formatter is authoritative**, the same
 * resolution `kanjiExtended` and `ensoSweep` got rather than being duplicated.
 *
 * **Past the horizon the boundary is a calendar boundary, never an hour count** — the reason this
 * takes wall-clock instants and a zone rather than a bare delta. `00-plan.md` §2 row 14 makes the same
 * argument about every date bucket in the feature. The ordering it buys: a **five**-hour-old session
 * reads きのう while a **fourteen**-hour-old one reads きょう, because midnight separates them and
 * duration does not. A delta-only signature cannot express that at any set of edges.
 *
 * Below the horizon elapsed time still wins, deliberately. Twenty minutes after midnight, きのう is
 * true and useless — the fact the user wants is that they stopped a moment ago. The day words take
 * over only once the elapsed number has stopped being the more informative of the two.
 *
 * **The three bucket words differ between the languages, and that is the point.** The Japanese is
 * transcribed from §A — 一時間前 over a 10m–2h bucket and 二時間前 over 2h–4h are the spec's words and
 * ship unchanged. The English is authored, and an authored "2 hours ago" over a three-and-a-half-hour
 * session would restate the very falsehood the bucket edges above were moved to remove. So the English
 * arms round ("A few hours ago") where the Japanese names. See `GymHomeStrings.stalenessJustNow`.
 */
fun stalenessLabel(startedAtWallMs: Long, nowWallMs: Long, zone: ZoneId, strings: Strings): String {
    val deltaMs = (nowWallMs - startedAtWallMs).coerceAtLeast(0L)
    val copy = strings.gymHome
    return when {
        deltaMs < JUST_NOW_MS -> copy.stalenessJustNow
        deltaMs < ONE_HOUR_BUCKET_MS -> copy.stalenessEarlier
        deltaMs < RESUMABLE_HORIZON_MS -> copy.stalenessSeveralHours
        else -> strings.fmt.relativeDay(
            then = Instant.ofEpochMilli(startedAtWallMs).atZone(zone).toLocalDate(),
            today = Instant.ofEpochMilli(nowWallMs).atZone(zone).toLocalDate(),
        )
    }
}

/**
 * The prompt's three text lines (`03-player.md` §A's mock).
 *
 * @param note `続きからは できません`, present exactly when 続ける has been removed by the clock rather
 *   than by the data — a rebooted or four-hour-old session. A deleted routine also removes 続ける and
 *   gets **no** note, because §A writes that sentence only for the reboot case and the banner is
 *   already rendering the session's stored name; saying more would be inventing an explanation.
 */
data class ResumePromptCopy(
    val title: String,
    val detail: String,
    val staleness: String,
    val note: String?,
)

/**
 * 途中の 鍛錬があります — or 途中の 鍛錬が 残っています when the session can no longer be continued.
 *
 * The title shifts on the *clock*, not on the option set: `03-player.md` §A gives the second title to
 * `RebootedOrStale` alone. `NOTHING_TO_SAVE` keeps the first one — it lost 記録する, not 続ける's
 * premise — and a session whose routine was deleted keeps it too, because the workout is still there
 * even though there is nothing left to resume into.
 *
 * @param nowWallMs wall-clock now, for the staleness bucket only. Elapsed comes from the monotonic
 *   anchors and never from this.
 */
fun resumePromptCopy(
    session: ResumableSession,
    nowWallMs: Long,
    strings: Strings,
    zone: ZoneId = ZoneId.systemDefault(),
): ResumePromptCopy {
    val copy = strings.gymHome
    val stopped = session.resumability == Resumability.REBOOTED || session.resumability == Resumability.STALE
    val elapsed = recoveredElapsedMs(session)
    val stations = stationsProgressed(session.results)
    val detail = listOfNotNull(
        // **Never translated.** `session.routine_name` is a snapshot frozen at `startSession` so the
        // prompt survives the routine's deletion, and whatever the routine was called that day, in
        // whatever language, is what that session performed (§L10, `CatalogDisplay.kt`).
        session.routineName,
        strings.fmt.durationFromMs(elapsed).takeIf { elapsed > 0L },
        strings.fmt.stations(stations).takeIf { stations > 0 },
    ).joinToString(strings.fmt.separator)
    return ResumePromptCopy(
        title = if (stopped) copy.promptTitleStopped else copy.promptTitleOpen,
        detail = detail,
        staleness = stalenessLabel(session.lastWriteWallMs, nowWallMs, zone, strings),
        note = if (stopped) copy.promptNoResume else null,
    )
}

/**
 * What an interrupted session is worth: the elapsed time **at its last persisted transition**.
 *
 * `recoveredActiveMs` needs an `elapsedRealtime` figure and [ResumableSession] carries no
 * `lastWriteElapsedMs`, so the last result's `closedAtElapsedMs` is the conservative stand-in — it is
 * the last moment the store knows the session was running. Legitimately zero for a row with no results,
 * which is exactly the `NOTHING_TO_SAVE` shape, and the detail line then omits the figure rather than
 * printing 〇秒.
 */
fun recoveredElapsedMs(session: ResumableSession): Long {
    val lastWrite = session.results.maxByOrNull { it.ordinal }?.closedAtElapsedMs ?: return 0L
    return recoveredActiveMs(session.clock, lastWrite)
}

/**
 * The card's one-line shape: 「十二種目 ・ 三十秒 / 十秒 ・ 約 八分」 / `12 stations · 30s / 10s · ~8m`.
 *
 * Composed here rather than in `GymStore`, which is where the numbers come from, because a rendered
 * sentence in the data layer cannot follow a language switch — that feed re-emits on a table change,
 * so the card would keep its old wording until something wrote to the database.
 *
 * One deliberate change to Japanese: the estimate now reads 約 八分 with the space, matching
 * `RoutineEstimate` and the specs' own sample, where this line alone used to write 約八分. The two
 * surfaces disagreed; routing both through `fmt.approx` settles it rather than preserving the split.
 */
fun routineCardSummary(card: RoutineCard, strings: Strings): String = buildList {
    add(strings.fmt.stations(card.stationCount))
    card.workSeconds?.let { work ->
        val rest = if (card.restBetweenStations > 0) " / " + strings.fmt.seconds(card.restBetweenStations) else ""
        add(strings.fmt.seconds(work) + rest)
    }
    if (card.estimatedDurationSeconds > 0) {
        val minutes = (card.estimatedDurationSeconds / 60.0).roundToInt().coerceAtLeast(1)
        add(strings.fmt.approx(strings.fmt.minutes(minutes)))
    }
}.joinToString(strings.fmt.separator)
