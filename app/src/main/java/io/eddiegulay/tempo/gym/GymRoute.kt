package io.eddiegulay.tempo.gym

import java.time.YearMonth

/**
 * The gym's three tabs, carrying their own labels because the bar renders **words, not glyphs**.
 *
 * There is no `TempoIcons` entry for training, forms or records, and inventing three line-paths is
 * more invention than this needs — 鍛錬 / 型 / 記録 are the feature's vocabulary and reading them is
 * the point. The label living on the enum rather than in the bar is what keeps the tab's name and its
 * TalkBack node from ever disagreeing (`00-plan.md` §3.2, `01-shell.md` §A.4).
 *
 * Declaration order is display order.
 */
enum class GymTab(val label: String) { Train("鍛錬"), Library("型"), Records("記録") }

/**
 * Every place inside 鍛錬, as data.
 *
 * The launcher gains exactly one `Screen` value for the whole feature; where you are *inside* the gym
 * is not the launcher's business, exactly as `Screen.Focus` does not encode "clock vs pomodoro". That
 * trade buys a whole shell — own back stack, own tab bar, own ViewModel — for one enum entry
 * (`00-plan.md` §1.1).
 *
 * *Rejected* — `navigation-compose` and a `NavHost`. It is a new dependency, which `CONTRIBUTING.md`
 * forbids, and it would put the one rule this shell actually has to be right about — Back at the root
 * of a HOME activity — behind a library's own back dispatcher. As a `List<GymRoute>` mutated by four
 * pure functions the whole model is JVM-testable with no Android on the classpath, which is the same
 * bargain the repo already makes for `groupByApp` and `layoutTategaki`.
 *
 * *Rejected* — per-tab stacks (`Map<GymTab, List<GymRoute>>`). Right when tabs are deep independent
 * worlds you shuttle between mid-task; 型 and 記録 are two deep at most, and every meaningful journey
 * ends in the session player, which is outside the tabs entirely. Per-tab stacks would buy a restored
 * scroll depth at the cost of three times the state, three times the process-death surface, and a
 * Back whose behaviour depends on history the user cannot see (`01-shell.md` §A.1).
 *
 * **Two invariants hold over every stack the shell can produce**: it is never empty, and
 * `stack.first().tab` is never null. `GymNavTest` pins both, because the shell reads `stack.last()`
 * and `stack.first().tab` on the frame that draws the screen and has nowhere to put a null.
 */
sealed interface GymRoute {

    /** Non-null only for the three tab roots; the bar's selection is read from the stack's first. */
    val tab: GymTab? get() = null

    /** Takes the whole window: no tab bar, no shell insets, its own back handling. */
    val immersive: Boolean get() = false

    /** Whether pushing this route on top of an equal one is a no-op. True for everything but one. */
    val singleTop: Boolean get() = true

    data object Home : GymRoute {
        override val tab = GymTab.Train
    }

    data object Library : GymRoute {
        override val tab = GymTab.Library
    }

    data object Records : GymRoute {
        override val tab = GymTab.Records
    }

    data class RoutineDetail(val routineId: String) : GymRoute

    /**
     * The one route that may stack on itself. 写して作る opens a builder for a copy from inside a
     * builder, and collapsing that to a single instance would silently discard the draft already
     * being edited — the worst possible outcome for the only page in the gym that holds unsaved work.
     */
    data class Builder(val editingId: String? = null) : GymRoute {
        override val immersive = true
        override val singleTop = false
    }

    data class StationPicker(val index: Int?) : GymRoute {
        override val immersive = true
    }

    data object ExerciseIndex : GymRoute

    data class ExerciseDetail(val exerciseId: String) : GymRoute

    /**
     * The session player, from 支度 through to 記録. One route for every phase: the phase is the
     * player's own state machine and putting it in the route would make Back a phase control, which
     * it must never be — Back inside a live session opens the quit sheet.
     */
    data class Session(val routineId: String, val resume: Boolean = false) : GymRoute {
        override val immersive = true
    }

    data class Record(val sessionKey: String) : GymRoute

    data class History(val routineId: String? = null, val anchorMonth: YearMonth? = null) : GymRoute

    data object Bests : GymRoute

    data object Charts : GymRoute

    data object Settings : GymRoute

    data object Safety : GymRoute
}
