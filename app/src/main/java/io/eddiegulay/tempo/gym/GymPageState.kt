package io.eddiegulay.tempo.gym

/*
 * The three decisions `GYM.HOME` and `GYM.LIBRARY.*` make *before* they draw anything: which つづき
 * affordance is owed, which of the resume prompt's three doors are open, and which routines survive
 * the index's search and filters.
 *
 * They live here, Android-free and beside the shell's `back`/`push`/`selectTab`, for the reason that
 * file states: a decision made inside a composable is a decision that can only be checked by
 * launching an emulator. Every function here is total over its inputs and returns a *shape*, never a
 * string — all UI copy for these pages is the page's, from `01-shell.md` §B and
 * `04-library-records.md` §3/§6, and a data-shaping file that also chose wording would be a second
 * place for the string tables to drift.
 *
 * `GymViewModel` is the only production caller. It is thin over these on purpose (`00-plan.md` §4.1
 * rule 10): the ViewModel owns flows, scopes and writes; this file owns the arithmetic.
 */

// ─── GYM.HOME's preview widths ──────────────────────────────────────────────────────────────────

/** `homeFeed(recentLimit = 3, …)` — `01-shell.md` §B's signature, named so the call site reads. */
const val HOME_RECENT_LIMIT = 3

/** 型's preview, once the user has trained (`01-shell.md` §B's layout, `builtInPreview = 4`). */
const val HOME_BUILTIN_PREVIEW = 4

/**
 * 型's preview on a first run, where よく使う is absent because there is no history to rank and the
 * space it would have taken goes to two more 型 (`01-shell.md` §B's `FirstRun` state).
 */
const val HOME_BUILTIN_PREVIEW_FIRST_RUN = 6

/**
 * The 型 cards `GYM.HOME` should draw, at the width the feed's own [GymHomeFeed.everTrained] asks for.
 *
 * The feed is always read at the wider figure and narrowed here, rather than re-subscribed when
 * `everTrained` flips: the flip happens exactly once in a user's life, at the end of their first
 * workout, and re-running a five-way join to *shorten* a list already in memory would be work done at
 * the one moment the page has something better to do.
 *
 * `builtInTotal` is untouched by either width, so すべて見る appears on the same condition
 * (`builtInTotal > preview`) whichever shape is in force.
 */
fun homeBuiltIn(feed: GymHomeFeed): List<RoutineCard> =
    feed.builtIn.take(if (feed.everTrained) HOME_BUILTIN_PREVIEW else HOME_BUILTIN_PREVIEW_FIRST_RUN)

// ─── The つづき affordance and the resume prompt ─────────────────────────────────────────────────

/**
 * Which つづき treatment `GYM.HOME` owes: none, the live banner, or the banner **plus** the
 * three-way prompt (`01-shell.md` §B's `ResumableSession` / `StaleSession` / `StaleAfterReboot`
 * states).
 *
 * [StalePromptNoResume] is a separate case rather than a flag on [StalePrompt] because the
 * difference is which buttons exist at all: a rebooted device lost the monotonic timebase, so
 * 続ける is **removed, not disabled** — an option that will never be available is chrome, not
 * information (`03-player.md` §A).
 */
enum class ResumeAffordance { None, LiveBanner, StalePrompt, StalePromptNoResume }

/**
 * The affordance for a given pair of truths: what is running in memory, and what the store found.
 *
 * **The live session always wins and suppresses the prompt.** `01-shell.md` §B edge case 1 — a
 * process that died mid-session and a session started before returning can both be true at once, and
 * two つづき banners would be the page contradicting itself. The stale row is not lost; it is asked
 * about once the live session ends.
 *
 * *Rejected* — §B's literal `resumeAffordance(live, stale, bootAnchorValid)`. The third parameter is
 * the boot-anchor comparison, and the shipped [ResumableSession] already carries its answer in
 * [ResumableSession.resumability], computed once by `resumabilityOf` at the point the row became a
 * model (`02-data.md` §C.2). Taking it again as an argument would let a caller pass a second,
 * independently-wrong copy of a check `03-player.md` §E.3 was careful to define exactly once.
 *
 * A `null` [stale] is the resolved answer "there is no open session" — a *Loading* read must be
 * mapped to [None] by the caller, because the prompt does not appear until the query resolves
 * (`03-player.md` §A, the prompt's `Loading` state: no flash).
 */
fun resumeAffordance(live: ActiveSession?, stale: ResumableSession?): ResumeAffordance = when {
    live != null -> ResumeAffordance.LiveBanner
    stale == null -> ResumeAffordance.None
    resumeOptions(stale).canResume -> ResumeAffordance.StalePrompt
    else -> ResumeAffordance.StalePromptNoResume
}

/**
 * Which of 続ける / 記録する / 捨てる the prompt may offer for one open session.
 *
 * 捨てる is always offered and there is no fourth field for the dismiss: dismissing decides nothing
 * and is therefore never absent (`00-plan.md` §2 row 8).
 *
 * Three removals, each from a different sentence of the spec:
 *
 * 1. **[Resumability.REBOOTED] and [Resumability.STALE] remove 続ける.** The elapsed clock is
 *    unreconstructable across a boot and a four-hour-old session is not one the user is still in the
 *    middle of; resuming either would fabricate duration (`03-player.md` §E.3).
 * 2. **A deleted routine removes 続ける.** There is nothing to resume *into* — the banner renders
 *    from the session row's denormalised name, which is the one case the version foreign key cannot
 *    serve (`01-shell.md` §B edge case 2, `00-plan.md` §2 row 10). 記録する and 捨てる remain.
 * 3. **[Resumability.NOTHING_TO_SAVE] removes 記録する**, leaving 捨てる and the dismiss. Auto
 *    discarding is tempting and wrong: the user should learn that this app never deletes silently
 *    (`03-player.md` §A).
 *
 * Point 3 also takes 続ける with it, and that is the spec read literally rather than charitably:
 * both `03-player.md` §A's state list and [Resumability]'s own KDoc enumerate what survives as
 * "捨てる and a dismiss", naming neither 続ける nor a fourth option. A session with no closed segment
 * is at most a few seconds of 支度, so what is lost by re-starting it is nothing; if that reading is
 * ever revisited, it is revisited in the spec first.
 */
data class ResumeOptions(
    val canResume: Boolean,
    val canRecord: Boolean,
    val canDiscard: Boolean = true,
)

fun resumeOptions(stale: ResumableSession): ResumeOptions = ResumeOptions(
    canResume = stale.routineExists && stale.resumability == Resumability.RESUMABLE,
    canRecord = stale.resumability != Resumability.NOTHING_TO_SAVE,
)

// ─── 始める's preconditions ──────────────────────────────────────────────────────────────────────

/**
 * Why 始める is not available on `GYM.LIBRARY.DETAIL`, or [None] when it is.
 *
 * Only the reasons that are properties of the *routine*. A live session elsewhere is deliberately not
 * a member: it does not disable 始める, it raises the resume prompt and the start proceeds after the
 * user resolves it (`03-player.md` §A's start guard), which is a different behaviour and belongs to
 * the ViewModel rather than to a disabled-button predicate.
 *
 * The copy for each case is `04-library-records.md` §3's — 削除済み for [Archived],
 * 種目が見つからないため 始められません for [UnknownExercise] — and stays on the page.
 *
 * [NoStations] is unreachable through the seeder and the builder, and is checked anyway because it is
 * the one failure the compiler throws on (`compile` requires a non-empty station list). Catching it
 * here is what makes it a disabled button on a page rather than an exception a tap away from a
 * workout.
 */
enum class StartBlock { None, Archived, NoStations, UnknownExercise }

fun startBlock(detail: RoutineDetail): StartBlock = when {
    detail.archived -> StartBlock.Archived
    detail.snapshot.stations.isEmpty() -> StartBlock.NoStations
    detail.snapshot.hasUnknownExercise -> StartBlock.UnknownExercise
    else -> StartBlock.None
}

// ─── GYM.LIBRARY.INDEX's three sections ─────────────────────────────────────────────────────────

/**
 * The library index's list, already searched, filtered, ranked and split into its three sections.
 *
 * One value rather than three flows because the sections are three views of a single filtered list
 * and computing them apart would run `applyFilters` three times per keystroke. [matched] is the size
 * of that shared list and is what separates §3's `NoMatch` from `Ready`; whether to add
 * 絞り込みを外す on top (`FilteredToNothing`) is a question about the *filter*, which the page already
 * holds.
 *
 * A routine legitimately appears in both [frequent] and its home section — that is what
 * `01-shell.md` §B's layout draws, 七分間 in よく使う and again under 型. List keys must therefore be
 * section-scoped, as §B edge case 6 requires (`"builtin:$id"` / `"user:$id"`), or Compose will see
 * one id twice.
 */
data class LibraryIndexRoutines(
    val frequent: List<RoutineSummary>,
    val builtIn: List<RoutineSummary>,
    val user: List<RoutineSummary>,
) {
    val matched: Int get() = builtIn.size + user.size
}

/**
 * Search, filters, よく使う ranking and the built-in/user split, in the one order that is correct.
 *
 * **Filter first, then rank.** よく使う is a section *of the index*, so a chip that hides a routine
 * has to hide it everywhere — a filtered page whose top section still offers the excluded routine is
 * the filter appearing not to work. Ranking a list that has already been narrowed also keeps
 * `rankFrequent`'s cap meaningful: four of the matches, not four of everything.
 *
 * **[LibraryIndexRoutines.frequent] is emptied below [FREQUENT_MINIMUM].** `04-library-records.md`
 * §3's `Ready` state omits the section rather than drawing a one-item "frequently used", which is
 * noise. `LibraryFilters` owns the number and states that the *rule* lives with the caller; this is
 * that caller, so both halves are in one place and cannot disagree.
 *
 * @param stationNames resolved per routine by the caller, because a [RoutineSummary] is a projection
 *   that deliberately does not inflate its stations (`04-library-records.md` §3's one-query rule).
 *   Defaulted to empty so a surface with no search box does not have to fabricate one — note that
 *   the default silently gives up §3 edge case 2, where 懸垂 must surface シンディ.
 */
fun libraryIndexRoutines(
    routines: List<RoutineSummary>,
    filter: RoutineFilter,
    recentUsage: Map<String, Int>,
    usageCounts: Map<String, Int>,
    stationNames: (RoutineSummary) -> List<String> = { emptyList() },
): LibraryIndexRoutines {
    val matched = applyFilters(routines, filter, stationNames)
    val frequent = rankFrequent(matched, recentUsage, usageCounts)
    val builtIn = matched.filter { it.builtIn }
    val user = matched.filterNot { it.builtIn }
    return LibraryIndexRoutines(
        frequent = if (frequent.size >= FREQUENT_MINIMUM) frequent else emptyList(),
        builtIn = builtIn,
        user = user,
    )
}
