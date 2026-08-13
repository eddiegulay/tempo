# 鍛錬 — Part 1: shell, navigation, GYM.HOME, GYM.SETTINGS

Read `00-plan.md` first. Merge decisions in its §2 override anything here.

Owns: the launcher↔gym boundary, the nav architecture, `GymViewModel`, `GYM.HOME`, `GYM.SETTINGS`,
`GYM.SAFETY`. Names `GYM.LIBRARY.*`, `GYM.RECORDS.*`, `GYM.SESSION.*` only as navigation targets.

---

## A. Navigation architecture

### A.1 One launcher enum value, a nested graph beneath it

```kotlin
// ui/TempoApp.kt — the only change to the launcher's enum
enum class Screen { Home, Search, Notifications, Filter, Focus, Calendar, EventCompose, Gym }
```

`GymRoute` and `GymTab` are defined in `00-plan.md` §3.1. Navigation is four pure functions:

```kotlin
// gym/GymNavigation.kt — pure, Android-free, unit-tested
sealed interface BackOutcome {
    data class Pop(val stack: List<GymRoute>) : BackOutcome
    data class Rebase(val stack: List<GymRoute>) : BackOutcome
    data object ExitShell : BackOutcome
}

fun back(stack: List<GymRoute>): BackOutcome = when {
    stack.size > 1 -> BackOutcome.Pop(stack.dropLast(1))
    // A non-Train tab root falls back to the Train root before it will consider leaving.
    // Back is then never a surprise ejection from a side tab.
    stack.single() != GymRoute.Home -> BackOutcome.Rebase(listOf(GymRoute.Home))
    else -> BackOutcome.ExitShell
}

fun push(stack: List<GymRoute>, route: GymRoute): List<GymRoute> =
    if (route.singleTop && stack.lastOrNull() == route) stack else stack + route

/** A tab tap always returns to that tab's root, dropping whatever was stacked above it. */
fun selectTab(stack: List<GymRoute>, tab: GymTab): List<GymRoute> = listOf(rootOf(tab))

fun rootOf(tab: GymTab): GymRoute = when (tab) {
    GymTab.Train -> GymRoute.Home
    GymTab.Library -> GymRoute.Library
    GymTab.Records -> GymRoute.Records
}

/** One rule, no per-page opt-outs to forget. */
fun tabBarVisible(stack: List<GymRoute>): Boolean = stack.size == 1
fun currentTab(stack: List<GymRoute>): GymTab? = stack.first().tab
```

**Single stack, not per-tab stacks.** Per-tab stacks (`Map<GymTab, List<GymRoute>>`) are right when
tabs are deep independent worlds you shuttle between mid-task. Here 型 and 記録 are two-deep at most,
and every meaningful journey ends in the session player, which is outside the tabs entirely. Per-tab
stacks would buy a restored scroll depth in 記録 at the cost of three times the state, three times the
process-death serialisation, and a back button whose behaviour depends on invisible history. Single
stack plus rebase is one list, one rule, four tests.

### A.2 `GymViewModel` — separate, Activity-scoped, lazily constructed

`LauncherViewModel` is 495 lines and already holds theme, onboarding, screen, search, the app
inventory, the blockade, notifications with an undo window, and the calendar read/write pipeline with
its own `Loadable`/`PendingWrite` state machine. Three reasons harder than line count:

1. **Cold-start cost.** This is a HOME app; `MainActivity.onCreate` runs on every HOME press from a
   cold process, and the first frame *is* the user's home screen. Opening `SQLiteOpenHelper` — which
   runs `onCreate`/`onUpgrade` on first `getReadableDatabase` — belongs behind a lazy door that a user
   who never touches 鍛錬 never opens.
2. **Failure isolation.** A corrupt gym database must degrade to a fault panel inside the shell. As a
   constructor parameter of `LauncherViewModel`, a throw in its `init` takes down Home, Search and
   Notifications with it.
3. **Lifetime honesty.** Launcher state is "what is on screen." Gym state includes a *live workout*
   that must outlive both.

```kotlin
// gym/GymViewModel.kt
class GymViewModel(
    private val repository: GymRepository,
    private val preferences: GymPreferencesRepository,
) : ViewModel() {

    private val _stack = MutableStateFlow<List<GymRoute>>(listOf(GymRoute.Home))
    val stack: StateFlow<List<GymRoute>> = _stack.asStateFlow()

    /**
     * The live workout. Deliberately NOT derived from [stack]: a session's existence is a fact about
     * the user's body, not about what is on screen. Leaving the player, leaving the shell, and a HOME
     * press must all leave this untouched — see [onLeaveShell].
     */
    private val _activeSession = MutableStateFlow<ActiveSession?>(null)
    val activeSession: StateFlow<ActiveSession?> = _activeSession.asStateFlow()

    fun go(route: GymRoute) { _stack.update { push(it, route) } }
    fun selectTab(tab: GymTab) { _stack.update { selectTab(it, tab) } }

    /** @return true if the gym consumed Back; false means the caller must exit the shell. */
    fun onBack(): Boolean = when (val outcome = back(_stack.value)) {
        is BackOutcome.Pop -> { _stack.value = outcome.stack; true }
        is BackOutcome.Rebase -> { _stack.value = outcome.stack; true }
        BackOutcome.ExitShell -> false
    }

    /** Called on every exit from the shell — Back at the root, or a HOME press. */
    fun onLeaveShell(reason: LeaveReason) {
        _stack.value = listOf(GymRoute.Home)
        clearTransientState()          // sheets, prompts, drafts, faults — see §A.5
        // _activeSession is untouched. Always.
    }
}

enum class LeaveReason { Back, HomePress }

/** Manual factory, mirroring LauncherViewModelFactory. No DI framework. */
class GymViewModelFactory(context: Context) : ViewModelProvider.Factory {
    private val appContext = context.applicationContext
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = GymViewModel(
        repository = GymRepository.getInstance(appContext),
        preferences = GymPreferencesRepository(appContext),
    ) as T
}
```

**Scoping.** Activity-scoped, resolved lazily, from two places that must agree on one instance:

```kotlin
// MainActivity — the delegate constructs nothing until first touched.
private val gymViewModel: GymViewModel by viewModels { GymViewModelFactory(applicationContext) }

override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    setIntent(intent)
    // Only touch the gym VM if the gym was actually entered. Otherwise it stays unconstructed for
    // the lifetime of a user who never trains, and no database is ever opened.
    if (viewModel.screen.value == Screen.Gym) gymViewModel.onLeaveShell(LeaveReason.HomePress)
    viewModel.resetToHome()
}
```

```kotlin
// TempoApp — resolved INSIDE the Screen.Gym branch, never above it.
// LocalViewModelStoreOwner inside setContent is MainActivity, so this is the same instance the
// Activity's delegate returns, and it survives the shell unmounting.
Screen.Gym -> GymShell(gym = viewModel(factory = GymViewModelFactory(LocalContext.current)))
```

`GymViewModelFactory` must therefore be cheap to construct and `create` must be idempotent — both hold,
since the repositories are singletons behind `getInstance`, in the existing `BlockadeRepository` /
`AppRepository` style.

### A.3 BackHandler composition — and why Back can never leave the launcher

Three facts constrain this: `MainActivity` is `launchMode="singleTask"` and the task root; it is a
`category.HOME` activity; and `android:enableOnBackInvokedCallback="true"` is set application-wide.
Together they mean **any moment where no `BackHandler` is enabled hands the gesture to the system**,
which runs a predictive-back preview of leaving the task and finishes the root activity. From a home
app that is a black screen or a bounce to the previous app. `TempoApp.kt:111`'s
`BackHandler(enabled = screen == Screen.Home) { }` exists precisely to prevent this.

**Launcher side** — one line, adding `Gym` to the generic handler's exclusion set:

```kotlin
BackHandler(enabled = screen == Screen.Filter) { viewModel.goSearch() }
BackHandler(enabled = screen == Screen.EventCompose) { viewModel.cancelCompose() }
BackHandler(
    enabled = screen != Screen.Home &&
        screen != Screen.Filter &&
        screen != Screen.EventCompose &&
        screen != Screen.Gym,          // ← the gym handles its own Back, at every depth
) { viewModel.goHome() }
BackHandler(enabled = screen == Screen.Home) { /* stay on home */ }
```

**Gym side** — exactly one handler, `enabled = true` unconditionally, registered by the shell *before*
it composes page content:

```kotlin
@Composable
fun GymShell(gym: GymViewModel, onExit: () -> Unit, modifier: Modifier = Modifier) {
    val stack by gym.stack.collectAsStateWithLifecycle()

    // ALWAYS enabled while the shell is mounted. Never `enabled = stack.size > 1` — a disabled
    // handler at the gym root is a frame in which the system owns Back, and for a HOME activity
    // that is a task-finish. The decision lives inside the lambda, not in the enabled flag.
    BackHandler(enabled = true) { if (!gym.onBack()) onExit() }
    // ... tab bar + AnimatedContent over stack.last() ...
}
```

**Nesting.** Compose registers `BackHandler` callbacks in composition order and the most recently
added enabled callback wins. Because `GymShell` registers above the `AnimatedContent` that composes the
page, any handler a page registers is added later and takes priority. That is the mechanism by which:

- `GYM.SESSION.*` registers `BackHandler(enabled = true) { showQuitSheet() }` — Back opens the
  three-way sheet instead of popping.
- `GYM.LIBRARY.BUILDER` registers `BackHandler(enabled = isDirty) { showDiscardPrompt() }` — a clean
  builder falls through to the shell's ordinary pop.
- A quit sheet or discard prompt rendered as a Compose `Dialog` consumes Back through its own window
  before either.

The only rule pages must follow: **never register `BackHandler(enabled = false)` expecting the shell to
be reached "faster."** It already is the fallback, and a disabled handler is free.

**Predictive back.** An always-enabled `BackHandler` (rather than `PredictiveBackHandler`) opts this
surface out of the system back preview. Correct here: a home app must never render a "you are leaving"
peek, because there is nothing behind it. The shell's own enter/exit motion supplies the spatial
feedback instead.

**AnimatedContent and Back.** Do not try to make Back cancel an in-flight route transition.
`AnimatedContent` retargets cleanly from a mid-transition state; a rapid Back-Back lands on the right
route.

### A.4 The tab bar

```
├──────────────────────────────────────────────────┤ ← 1.dp c.hair, full-bleed top hairline
│                    ·                             │ ← active dot: 4.dp c.accent, 7.dp above label
│      鍛錬          型          記録              │ ← Mincho 13.sp ls 3.sp
│                                                  │   active c.accent · inactive c.inkFaint
└──────────────────────────────────────────────────┘ ← 64.dp + navigationBarsPadding()
                                                      background c.card over the washi (NOT wetPaper)
```

- **Words, not glyphs.** There is no `TempoIcons` entry for training, forms or records, and inventing
  three line-paths is more invention than this needs. 鍛錬 / 型 / 記録 are the feature's vocabulary and
  reading them is the point.
- **Flush and full-width, not floating.** A pill says "chrome hovering over a page"; a seated bar with
  a hairline says "this window has a different frame."
- **No frosting.** `wetPaper` is the dock's material; reusing it undoes the distinction.
- Each tab is a full-height 64.dp × ≥96.dp click region.
- Content lists use `contentPadding(bottom = 88.dp)`.
- Hidden when `stack.size > 1`. Animates out with a 160ms fade + 8.dp downward slide.

### A.5 What `resetToHome()` must and must not clear

`LauncherViewModel.resetToHome()` (`LauncherViewModel.kt:205`) stays launcher-only and gains **no** gym
fields. The gym half is `GymViewModel.onLeaveShell(LeaveReason.HomePress)`, invoked from
`MainActivity.onNewIntent` immediately before it, so a HOME press is one atomic reset across both
owners. The launcher line changes by one rename: `_pendingFocus` → `_pendingMode`.

| state | HOME press | why |
|---|---|---|
| gym back stack | **cleared** → `[Home]` | Re-entering lands on the gym's own home, not a stale `Records` two deep. |
| mode dialog | **cleared** | Already true for `_pendingFocus`. |
| quit sheet visibility | **cleared** | Transient. |
| routine-builder draft | **cleared** | Unsaved by definition; HOME is a deliberate abandonment. |
| stale-session prompt | **cleared** | A question, not an answer; it re-asks from the same DB row. |
| rep-adjust sheet, long-press menu, station picker | **cleared** | Transient modals. |
| gym fault banner | **cleared** | Re-derived from the next query. |
| **the live `ActiveSession`** | **UNTOUCHED** | The whole point. Clock, timeline, `pausedAccumulatedMs`, completed and open segments all survive. **HOME is not a quit.** |
| the open-session row in SQLite | **UNTOUCHED** | It is the process-death safety net. Deleting it on a HOME press is how you lose a workout to a fat thumb. |
| an in-flight write | **UNTOUCHED** | Completes on `viewModelScope`; cancelling a half-written session is worse than finishing it. |
| gym preferences | **UNTOUCHED** | Durable settings. |
| the `GymViewModel` itself | **never cleared or recreated** | `onLeaveShell` is a state reset, not a teardown. There must be no `clear()` that nulls repositories. |

Two consequences to write into the code comment:

- A HOME press mid-session must **not** commit the session as partial. Only 記録する in the quit sheet
  does that.
- On re-entry, `GYM.HOME`'s つづき banner is fed by `activeSession ?: interruptedRow` — the in-memory
  session takes precedence, so HOME-and-return resumes exactly, with the monotonic clock having kept
  running.

### A.6 Motion

The gym renders in `TempoApp`'s **outer** branch alongside Focus, never inside the dock layer:

```kotlin
private enum class Layer { Onboarding, Launcher, Focus, Gym }

val layer = when {
    !onboardingComplete -> Layer.Onboarding
    screen == Screen.Focus -> Layer.Focus
    screen == Screen.Gym -> Layer.Gym
    else -> Layer.Launcher
}
```

Internal route transitions need a depth cue, which `AnimatedContent` cannot infer from the top route
alone. Carry depth in the target state:

```kotlin
@Immutable data class GymFrame(val route: GymRoute, val depth: Int)

AnimatedContent(
    targetState = GymFrame(stack.last(), stack.size),
    transitionSpec = {
        val forward = targetState.depth >= initialState.depth
        val enter = fadeIn(tween(240, delayMillis = 30, easing = LinearOutSlowInEasing)) +
            scaleIn(
                initialScale = if (forward) 0.98f else 1.02f,   // push settles in, pop settles out
                animationSpec = tween(280, delayMillis = 30, easing = LinearOutSlowInEasing),
            )
        enter togetherWith fadeOut(tween(90, easing = FastOutLinearInEasing))
    },
    label = "gym-route",
)
```

| what | duration | notes |
|---|---|---|
| mode dialog → shell | 280ms fade + `scaleIn(0.98f)`, 40ms delay | one step gentler than the launcher's 0.97f — a bigger move should feel calmer, not sharper |
| shell → launcher Home | 120ms fade out; launcher enters on its own 260ms spec | leaving is faster than arriving, as everywhere in this app |
| gym route push | 240ms fade + `scaleIn(0.98f)` | |
| gym route pop | 240ms fade + `scaleIn(1.02f)` | the only direction signal; no horizontal slide anywhere |
| tab switch | 200ms cross-fade, **no scale** | lateral moves get no depth cue, which is what makes push/pop legible |
| tab bar hide / show | 160ms fade + 8.dp vertical slide | |
| entering the player | 320ms fade, bar leaves over 160ms | |

Everything is `LinearOutSlowInEasing`. Nothing bounces, overshoots or loops.
`ANIMATOR_DURATION_SCALE = 0` is honoured by drawing every end state immediately — the shell must be
correct on the first frame with animations disabled, which is also how Compose UI tests will drive it.

### A.7 Window flags, insets, immersion

- **Insets.** `Modifier.statusBarsPadding()` on the content column, `Modifier.navigationBarsPadding()`
  on the tab bar — *not* a single `systemBarsPadding()` at the top, so the bar's background can bleed
  under the nav bar while its labels stay clear. `immersive` routes receive no shell padding and
  manage their own.
- **System bar icons.** `TempoApp.kt:82`'s theme-driven `SideEffect` already covers the shell.
- **Orientation.** The shell does not touch `requestedOrientation`. Only the session player does, and
  it locks **portrait** — the opposite of `FocusScreen.kt:89`'s landscape lock — restoring
  `originalOrientation` on dispose exactly as Focus does.
- **Keep-screen-on.** Per `00-plan.md` §2 row 1, the gym owns this, hardened against the exact bug
  commit `1f49dfc` fixed. That failure was a *disposal-scoped* release: HOME pressed while Tempo is
  not the default launcher backgrounds the window without unmounting, so `onDispose` never runs.
  Release on **`ON_PAUSE`**, the moment the window stops being visible:

```kotlin
// Inside GymShell. FLAG_KEEP_SCREEN_ON only — never a SCREEN_BRIGHT_WAKE_LOCK.
val keepAwake = session != null && prefs.keepScreenOn
DisposableEffect(keepAwake, lifecycleOwner) {
    val window = (view.context as? Activity)?.window
    fun apply(on: Boolean) =
        if (on) window?.addFlags(FLAG_KEEP_SCREEN_ON) else window?.clearFlags(FLAG_KEEP_SCREEN_ON)
    apply(keepAwake)
    val observer = LifecycleEventObserver { _, e ->
        when (e) {
            Lifecycle.Event.ON_PAUSE -> apply(false)       // closes the 1f49dfc class of leak
            Lifecycle.Event.ON_RESUME -> apply(keepAwake)
            else -> Unit
        }
    }
    lifecycleOwner.lifecycle.addObserver(observer)
    onDispose { lifecycleOwner.lifecycle.removeObserver(observer); apply(false) }
}
```

Because `MainActivity` declares `configChanges` for orientation, screenSize, density and uiMode, config
changes do not recreate the Activity, so this effect does not thrash on rotation.

`GYM.SESSION.COMPLETE`, `GYM.LIBRARY.DETAIL` and the resume prompt are excluded from `keepAwake` — the
workout is over or has not started.

### A.8 Process death

`GymViewModel` survives rotation and configuration changes but not process death, and a launcher is
killed often. Two layers:

1. **Navigation position is not restored.** On a cold start the gym is not on screen at all (`Screen`
   seeds to `Home`), and re-entry always lands on `GymRoute.Home`. Deliberate: restoring a user into a
   builder they have no memory of opening is worse than a clean index. `rememberSaveable` inside pages
   handles scroll and field state within a live process, which is all it is good for.
2. **The session is restored from the repository** — persisted on every phase transition with both
   `SystemClock.elapsedRealtime()` and a `System.currentTimeMillis()` anchor so a reboot is
   detectable. `GYM.HOME` surfaces it. The shell holds no restoration logic. See `03-player.md` §E.

### A.9 The entry / exit boundary

**Entry.** The Home clock long-press dialog becomes a two-mode chooser. `FocusConfirmDialog` becomes
`ModeDialog` with the same `AlertDialog` chrome, `containerColor = c.bgSolid`, Mincho throughout;
`HomeScreen.kt:117`'s `onLongClick(label = "集中モード")` becomes `label = "モードを選ぶ"`.

```
        ┌───────────────────────────────┐
        │   集中                    →   │   ← Mincho 17.sp, c.ink
        │   時計だけの画面                │      Gothic 12.sp, c.inkFaint
        │   ─────────────────────────   │   ← 1.dp c.hair
        │   鍛錬                    →   │
        │   体を動かす                   │
        │                     やめる     │   ← Mincho 13.sp, c.inkFaint
        └───────────────────────────────┘
```

```kotlin
enum class LauncherMode { Focus, Gym }

private val _pendingMode = MutableStateFlow(false)
val pendingMode: StateFlow<Boolean> = _pendingMode.asStateFlow()

fun requestMode() { _pendingMode.value = true }
fun cancelMode() { _pendingMode.value = false }
fun confirmMode(mode: LauncherMode) {
    _pendingMode.value = false
    _screen.value = when (mode) {
        LauncherMode.Focus -> Screen.Focus
        LauncherMode.Gym -> Screen.Gym
    }
}
```

The dialog is the only entry. No dock tab, no Home glyph, no Search route — design §1.1 argued all
three down and "app within the app" strengthens the argument. Back while the dialog is up is consumed
by the dialog window and returns to Home with nothing entered.

**Exit.** Three ways out, all converging on one method:

| exit | trigger | path |
|---|---|---|
| Back at the gym root | `back(stack) == ExitShell` | `gym.onLeaveShell(Back)` → `launcher.goHome()` |
| HOME press | `MainActivity.onNewIntent` | `gym.onLeaveShell(HomePress)` → `launcher.resetToHome()` |
| an app launch from a gym surface | none in Phase 1 | — |

There is **no** exit affordance in the gym chrome — no ✕ in the shell header. Back and HOME are the
doors, as with Focus. The player's ✕ is a session control, not a shell control.

### A.10 Preferences live in DataStore, not SQLite

Behind a new `GymPreferencesRepository` over the existing `tempo_settings` DataStore, because:

- it exists, is transactional, and is already in `res/xml/backup_rules.xml`;
- it offers the synchronous first-frame read (`loadInitialSettings`) the cue engine and the prepare
  countdown need *before* the player's first frame, which an IO-dispatched SQLite read cannot give
  without a flash of defaults;
- SQLite is for routines, sessions and results — rows the records screens query and aggregate. A
  nine-row key/value table would be a schema liability for no gain.

The one exception is `training_plan`, which is date-versioned (`02-data.md` §A.9).

---

## B. Page specs

### GYM.HOME — 鍛錬 (train)

**Purpose** — The gym's front door: resume an interrupted workout, or choose what to train today.

**Entered from** — ModeDialog 鍛錬 · the 鍛錬 tab · Back-rebase from `GYM.LIBRARY`/`GYM.RECORDS` roots ·
pops from `GYM.LIBRARY.DETAIL`, `GYM.SETTINGS`, `GYM.LIBRARY.BUILDER`, `GYM.RECORDS.SESSION_DETAIL`
when 鍛錬 was the root beneath them · `GYM.SESSION.COMPLETE` 閉じる.

**Exits to** — `GYM.SESSION.PREPARE` (続ける) · `GYM.LIBRARY.DETAIL` (tap a card) ·
`GYM.LIBRARY.BUILDER` (作る, or long-press → 写して作る) · `GYM.SETTINGS` (設定) · `GYM.LIBRARY.INDEX`
(すべて見る or the 型 tab) · `GYM.RECORDS.INDEX` (記録 tab) · `GYM.RECORDS.SESSION_DETAIL` (記録する on
the stale prompt) · `LAUNCHER.Home` (Back or HOME).

**Back behaviour** — `stack == [Home]`, so `back()` returns `ExitShell`: the shell's always-enabled
handler calls `onExit`, which runs `onLeaveShell(Back)` then `launcher.goHome()`. The launcher's
generic handler is excluded on `Screen.Gym`, so it cannot double-fire. Back is never a no-op here and
never reaches the system. An open modal consumes Back first.

**Tab bar** — visible.

**Data in** — one query, not five. An N+1 over routines for last-result and PR lines would issue a
dozen SQLite reads per frame; a single joined read on `Dispatchers.IO` produces one `Loadable` and one
recomposition.

```kotlin
fun homeFeed(recentLimit: Int = 3, builtInPreview: Int = 4): Flow<Loadable<GymHomeFeed>>
fun interruptedSession(): Flow<InterruptedSession?>          // null when clean; cheap, always fresh
suspend fun discardInterrupted(key: String)
suspend fun commitInterruptedAsPartial(key: String): WriteOutcome<String>   // → sessionKey
suspend fun touchRoutine(routineId: String)                  // recency bump

@Immutable data class GymHomeFeed(
    val recent: List<RoutineCard>,        // ≤ recentLimit, most-recently-used first
    val builtIn: List<RoutineCard>,       // ≤ builtInPreview, shipped order
    val builtInTotal: Int,                // drives すべて見る
    val userRoutines: List<RoutineCard>,  // all of them; typically 0–5
    val everTrained: Boolean,             // false ⇒ first-run shape
)

@Immutable data class RoutineCard(
    val routineId: String, val name: String,
    val summary: String,                  // 十二種目 ・ 三十秒 / 十秒 ・ 約七分
    val timesDone: Int,                   // 0 hides the line
    val lastResult: LastResult?,          // null ⇒ never done
    val best: RoutineBest?,               // null ⇒ no PR yet
    val builtIn: Boolean, val origin: String?,
)
```

**Data out** — `touchRoutine` on every navigation to a routine (the only happy-path write) ·
`discardInterrupted` · `commitInterruptedAsPartial`. The つづき banner for a *live* session writes
nothing; resuming it is pure navigation.

**Layout**

```
 ┌────────────────────────────────────────────────────────┐
 │  鍛錬                              設定        作る     │ ← Mincho 26.sp ls 3.sp c.ink
 │  令和八年 ・ 六月十七日                                 │   actions Mincho 13.sp ls 2.sp
 └────────────────────────────────────────────────────────┘   設定 c.inkSoft · 作る c.accent
   ────────────────────────────────────────────────────────  ← 1.dp c.hair, full width
                                                              pad start 28 end 22 top 24 bottom 10
   つづき                                                     ← Mincho 12.sp ls 3.sp c.inkFaint
   ┌──────────────────────────────────────────────────┐       section label pad start 18 top 18 bot 6
   │  七分間                          六分十四秒 経過  │       ← name Mincho 16.sp c.ink
   │  八種目まで進んだ                     続ける →    │       ← Gothic 13.sp c.inkSoft
   └──────────────────────────────────────────────────┘       ← 続ける Mincho 14.sp ls 2.sp c.accent
                                                               card 18.dp radius, c.card, pad h18 v16,
                                                               item pad v5, 0.5.dp c.accent@0.35 border
                                                               — the ONLY bordered card on the page
   よく使う
   ┌──────────────────────────────────────────────────┐
   │  七分間                                           │      ← Mincho 16.sp c.ink
   │  十二種目 ・ 三十秒 / 十秒 ・ 約七分               │      ← Gothic 13.sp lh 19.5.sp c.inkSoft
   │  前回 六分五十秒 ・ 三日前              十四回     │      ← Gothic 11.sp c.inkFaint (both)
   └──────────────────────────────────────────────────┘
   ┌──────────────────────────────────────────────────┐
   │  シンディ                                         │
   │  懸垂五 ・ 腕立て十 ・ スクワット十五 ・ 二十分     │
   │  前回 十五巡 ・ 六日前            最高 十七巡      │      ← PR line Mincho 11.sp ls 2.sp
   └──────────────────────────────────────────────────┘         c.accent if set this month,
                                                                else c.inkFaint
   型                                              すべて見る  ← すべて見る Mincho 12.sp ls 2.sp
   ┌──────────────────────────────────────────────────┐         c.accent, 48.dp target
   │  タバタ                                           │
   │  二十秒 / 十秒 ・ 八本 ・ 四分                     │
   │                                        中級       │      ← tier Gothic 11.sp c.inkFaint
   └──────────────────────────────────────────────────┘
   … (builtInPreview = 4) …

   自分の型
   ┌──────────────────────────────────────────────────┐
   │            型はまだありません                      │      ← Mincho 17.sp ls 4.sp c.inkFaint
   │                作る →                             │      ← Mincho 14.sp ls 2.sp c.accent
   └──────────────────────────────────────────────────┘         56.dp tall, INSIDE the section —
                                                                not a full-page empty state
   ────────────────────────────────────────────────────────
   痛みを感じたらやめる                                        ← Gothic 11.sp c.inkFaint, centred,
                                                                pad v 20 — first-run only
 ┌────────────────────────────────────────────────────────┐
 │    鍛錬 ·        型            記録                     │ ← §A.4, 64.dp + nav inset
 └────────────────────────────────────────────────────────┘
```

`LazyColumn(Modifier.padding(horizontal = 22.dp, vertical = 6.dp), contentPadding =
PaddingValues(bottom = 88.dp))` — geometry from `CalendarScreen.kt:144-147`, bottom reserve adjusted
for the seated bar.

**States**

| state | condition | render |
|---|---|---|
| **Loading** | `homeFeed is Loading` | Header and hairline draw immediately (they need no data); body shows `読み込み中`, Mincho 17.sp ls 4.sp `c.inkFaint`, centred. Tab bar live throughout. |
| **Ready** | `Ready`, `everTrained` | The full layout. |
| **Empty** | `Ready`, `userRoutines.isEmpty()` | **Section-scoped only.** The page is never globally empty — built-in 型 are always seeded. 自分の型 renders its inline empty state. |
| **Error** | `Failed` | `FaultPanel` in the body slot with もう一度 → `retry()`. **Never** an empty state. Header, hairline and tab bar stay usable so 設定 is still reachable. |
| **FirstRun** | `Ready`, `!everTrained` | よく使う absent (no history to rank). 型 preview expands to 6. The `痛みを感じたらやめる` line and the one-time "not medical advice" note appear at the foot, dismissible, never shown again. |
| **ResumableSession** | `activeSession != null` | つづき banner above よく使う. Elapsed ticks once per second from `SessionClock.elapsedMs()` — the clock kept running while the shell was away. 続ける pushes `Session(routineId, resume = true)`. No prompt: it is the user's own live session. |
| **StaleSession** | `activeSession == null && interruptedSession() != null` | つづき banner from the DB row, **plus** the three-way prompt on first sight: 続ける / 記録する / 捨てる, as a `Dialog` over the page matching `BlockConfirmDialog`. Elapsed shown as "at the time it stopped", never live-ticking. |
| **StaleAfterReboot** | boot anchor mismatch (see `03-player.md` §E.3) | Same prompt with 続ける **absent** — a rebooted device lost the monotonic timebase and resuming would fabricate duration. Two options only. |
| **Writing** | a commit/discard in flight | The prompt's buttons disable (`c.inkFaint`) rather than the dialog dismissing. A failed write leaves the row and shows the fault inline, so nothing is lost. |

**Actions**

| Trigger | Precondition | Effect | Persists? | Navigates to |
|---|---|---|---|---|
| Tap 設定 | always | — | no | `GYM.SETTINGS` |
| Tap 作る | `homeFeed` Ready | new empty draft | no (in-memory until 保存) | `GYM.LIBRARY.BUILDER(null)` |
| Tap つづき 続ける (live) | `activeSession != null` | none — already running | no | `GYM.SESSION.PREPARE` (resume, 3s) |
| Tap つづき 続ける (stale, non-reboot) | anchor intact | rehydrates `ActiveSession` from the row | no | `GYM.SESSION.PREPARE` |
| Tap 記録する (stale) | row exists | `commitInterruptedAsPartial` | **yes** — one session row, `complete = 0` | `GYM.RECORDS.SESSION_DETAIL` |
| Tap 捨てる (stale) | row exists | `discardInterrupted` after a second confirm | **yes** — deletes | stays |
| Tap a routine card | always | `touchRoutine` | **yes** | `GYM.LIBRARY.DETAIL` |
| Long-press a built-in card | `builtIn` | 写して作る menu | duplicate on select | `GYM.LIBRARY.BUILDER(newId)` |
| Long-press a user card | `!builtIn` | 編集 / 削除 / 写して作る | delete **yes** (with confirm) | `GYM.LIBRARY.BUILDER(id)` for 編集 |
| Tap すべて見る | `builtInTotal > builtInPreview` | `selectTab(Library)` | no | `GYM.LIBRARY.INDEX` |
| Tap a tab | always | `selectTab` | no | that tab's root |
| Back | always | `onLeaveShell(Back)` + `goHome()` | no | `LAUNCHER.Home` |
| Tap もう一度 (Error) | `Failed` | re-runs `homeFeed()` | no | stays |

**Edge cases**

1. **A live session *and* a stale row both exist** — possible when the process died mid-session and the
   user started a new one before returning. The live session wins the banner; the stale prompt is
   suppressed until the live session ends, then shown once. Never two banners.
2. **The routine behind a stale session was deleted** — the banner renders the stored routine *name
   snapshot* from the session row, not a join. 続ける is removed (nothing to resume into); 記録する and
   捨てる remain. This is why the session row carries a denormalised name.
3. **`timesDone == 0`** — the 十四回 line is omitted, not rendered as 〇回.
4. **Never-done routine** — no 前回 and no 最高 line; the card is two rows tall. Card heights must not
   be fixed.
5. **PR set this month vs earlier** — 最高 renders `c.accent` only when `setAt` is in the current
   calendar month, computed against the **guarded** clock, so winding the system clock back cannot
   repaint a stale PR as fresh.
6. **A user routine named identically to a built-in** — both render; the user's card has no tier chip
   and the built-in has its `origin` line. List keys are `"builtin:$id"` / `"user:$id"`, never the name.
7. **Very long routine name** — `maxLines = 1`, `TextOverflow.Ellipsis`, `Modifier.weight(1f, fill =
   true)`, exactly as `EventCard` (`CalendarScreen.kt:262`). The summary takes `maxLines = 2`.
8. **The write behind 捨てる fails** — the row stays, the banner stays, an inline fault appears. Never
   optimistically remove the banner: a resumable workout vanishing because a write failed is the worst
   possible failure here.
9. **HOME pressed with the prompt open** — prompt dismissed, row untouched, re-asked on next entry.
10. **The feed emits while a long-press menu is open** — the menu is keyed on `routineId`; if that
    routine left the feed the menu closes silently rather than acting on a stale target.
11. **Font scale 200%** — the three-line card grows; nothing clips because no card height is fixed and
    section labels use padding rather than fixed spacers.
12. **Dark theme** — every value from `LocalTempoColors`. The つづき border is
    `c.accent.copy(alpha = 0.35f)` in both themes, matching the dock's border treatment.

**Accessibility**

- **Cards** use `clearAndSetSemantics` with one composed description and `role = Role.Button`, exactly
  as `EventCard` (`CalendarScreen.kt:236`): `"七分間、十二種目 ・ 三十秒 / 十秒 ・ 約七分、前回 六分五十秒、
  三日前、十四回、最高 十七巡"`. Never five nodes per card.
- **Long-press** declares `onLongClick(label = "型の操作")` in `semantics`, following `Dock.kt:71` — a
  gesture that exists only as a raw `pointerInput` is invisible to TalkBack. Menu items also appear as
  `customActions`.
- **つづき banner** is one node: `"つづき、七分間、六分十四秒 経過、八種目まで進んだ、続ける"`,
  `Role.Button`. The ticking elapsed time announces only on focus, never as a live region.
- **Section labels** are `semantics { heading() }`.
- **Tab bar** items carry `role = Role.Tab` and `selected`; TalkBack then reads "鍛錬、タブ、3の1、選択中"
  with no hand-written description.
- **Targets**: cards ≥ 72.dp; 設定 / 作る / すべて見る via `HeaderAction`'s 48.dp minimum; tab items
  64.dp × ≥96.dp.
- **The stale prompt** takes focus on appearance; options are ordered 続ける, 記録する, 捨てる — least to
  most destructive, so a screen-reader user hits the safe option first.
- `c.inkFaint` is the contrast floor and is used only for 11–12.sp secondary lines that always have an
  `inkSoft` or `ink` sibling carrying the same meaning. **No information is `inkFaint`-only.**

**Pure logic to unit test**

```kotlin
fun back(stack: List<GymRoute>): BackOutcome
fun push(stack: List<GymRoute>, route: GymRoute): List<GymRoute>
fun selectTab(stack: List<GymRoute>, tab: GymTab): List<GymRoute>
fun tabBarVisible(stack: List<GymRoute>): Boolean

enum class ResumeAffordance { None, LiveBanner, StalePrompt, StalePromptNoResume }
fun resumeAffordance(live: ActiveSession?, stale: InterruptedSession?, bootAnchorValid: Boolean): ResumeAffordance

fun homeSections(feed: GymHomeFeed, recentLimit: Int, builtInPreview: Int): List<HomeSection>
fun lastResultLine(result: LastResult?, now: Long): String?      // "前回 六分五十秒 ・ 三日前"
fun bestLine(best: RoutineBest?): String?                        // "最高 十七巡"
fun isPrThisMonth(best: RoutineBest?, now: Long, zone: ZoneId): Boolean
fun timesDoneLabel(count: Int): String?                          // null at 0
fun formatElapsedJa(millis: Long): String                        // "六分十四秒"
fun relativeDayJa(then: Long, now: Long, zone: ZoneId): String    // "三日前" / "きのう" / "きょう"
```

---

### GYM.SETTINGS — 設定 (settings)

**Purpose** — One page for everything that changes how a workout *behaves*, so no preference ever has
to be found mid-session.

**Entered from** — `GYM.HOME` 設定 (the only entry).
**Exits to** — `GYM.HOME` (Back or ←) · `GYM.SAFETY` (安全のために).

**Back behaviour** — Depth 2, so `back()` returns `Pop`. The page registers **no** `BackHandler`: every
change commits on the tap that made it, so there is no dirty state. Back from an open wheel sheet is
consumed by the sheet, which commits its current value — a wheel is direct manipulation, and one that
discards on Back is a trap.

**Tab bar** — hidden (depth 2). Full height with `contentPadding(bottom = 32.dp)`.

**Data in / out**

```kotlin
class GymPreferencesRepository(context: Context) {
    val preferences: Flow<GymPreferences>
    /** One synchronous read for the player's first frame — mirrors ThemeRepository.loadInitialSettings. */
    fun loadInitial(): GymPreferences

    suspend fun setHaptics(on: Boolean)
    suspend fun setTones(on: Boolean)
    suspend fun setSpeech(on: Boolean)
    suspend fun setAutoAdvanceReps(on: Boolean)
    suspend fun setPrepareSeconds(seconds: Int)        // clamped 0..15
    suspend fun setDefaultStationRest(seconds: Int)    // clamped 0..300
    suspend fun setDefaultRoundRest(seconds: Int)      // clamped 0..600
    suspend fun setUnits(units: Units)
    suspend fun setKeepScreenOn(on: Boolean)
    suspend fun setSafetyNoteAcknowledged()
}

@Immutable data class GymPreferences(
    val haptics: Boolean = true,
    val tones: Boolean = true,             // design §3.6 default on
    val speech: Boolean = false,           // default off; auto-enabled under TalkBack (03 §D.6)
    val autoAdvanceReps: Boolean = false,  // deliberately off — "they should have to ask for it"
    val prepareSeconds: Int = 5,
    val defaultStationRest: Int = 15,
    val defaultRoundRest: Int = 60,
    val units: Units = Units.Metric,
    val keepScreenOn: Boolean = true,
    val safetyNoteAcknowledged: Boolean = false,
)

enum class Units(val label: String) { Metric("メートル法"), Imperial("ヤード・ポンド法") }

/** Whether a Japanese TTS voice exists. Probed once, cached; not a preference. */
fun speechAvailability(): SpeechAvailability   // Available | NoJapaneseVoice | NoEngine
```

Every row writes its own key immediately on tap. No 保存 button, no draft, no confirmation. 安全のために
navigates and writes nothing.

**Layout**

```
 ┌────────────────────────────────────────────────────────┐
 │  ←   設定                                               │ ← ← is a 48.dp target, Mincho 20.sp
 │      鍛錬のふるまい                                      │   c.inkSoft
 └────────────────────────────────────────────────────────┘   title Mincho 26.sp ls 3.sp c.ink
   ────────────────────────────────────────────────────────   subtitle Mincho 13.sp ls 4.sp c.inkFaint

   合図                                                      ← section Mincho 12.sp ls 3.sp c.inkFaint
   ┌──────────────────────────────────────────────────┐        pad start 18 top 22 bottom 6
   │  振動                                        入   │      ← label Mincho 16.sp c.ink
   │  ──────────────────────────────────────────────  │      ← state Mincho 15.sp ls 2.sp
   │  音                                          入   │        c.accent when 入, c.inkFaint when 切
   │  ──────────────────────────────────────────────  │      ← 0.5.dp c.hair divider, inset 18.dp
   │  音声                                        切   │
   │  種目の名前を読み上げる                            │      ← Gothic 12.sp c.inkFaint, only when
   └──────────────────────────────────────────────────┘        the row needs explaining
                                                               card 18.dp radius, c.card, rows 56.dp
   進行
   ┌──────────────────────────────────────────────────┐
   │  目安で自動的に進む                          切   │
   │  回数の種目でも時間が来たら次へ                    │
   │  ──────────────────────────────────────────────  │
   │  支度の長さ                              五秒 →   │      ← value Mincho 15.sp c.inkSoft
   └──────────────────────────────────────────────────┘        opens a TempoValueWheel sheet

   休息の初期値
   ┌──────────────────────────────────────────────────┐
   │  種目の間                              十五秒 →   │
   │  ──────────────────────────────────────────────  │
   │  巡の間                                六十秒 →   │
   └──────────────────────────────────────────────────┘
   これから作る型に使われます                                ← Gothic 11.sp c.inkFaint, a footnote,
                                                              not a card row
   表示
   ┌──────────────────────────────────────────────────┐
   │  画面を消さない                              入   │
   │  運動中だけ                                       │
   │  ──────────────────────────────────────────────  │
   │  単位                             メートル法 →    │
   └──────────────────────────────────────────────────┘

   ┌──────────────────────────────────────────────────┐
   │  安全のために                                 →   │      ← Mincho 16.sp c.ink, 64.dp tall
   └──────────────────────────────────────────────────┘
```

**The toggle control.** The project has no `Switch` idiom — the only Material control anywhere is one
`Checkbox` in `BlockConfirmDialog.kt:70`, and `FocusScreen` already expresses binary state as a *word*
(計測中 / 停止中). So a toggle here is `入` / `切` in Mincho 15.sp ls 2.sp, right-aligned: `c.accent` for
入, `c.inkFaint` for 切. The whole 56.dp row is the target. No new chrome, reads in the app's own
language, animates as a 120ms colour cross-fade with no thumb to slide.

**Value rows** open the extracted `TempoValueWheel` (prerequisite P2). 単位 cycles on tap rather than
opening a picker — two options do not deserve a sheet.

**States**

| state | condition | render |
|---|---|---|
| **Loading** | never observed | `GymPreferences` has total defaults and `loadInitial()` seeds synchronously, so the first frame is already correct. Renders defaults; never a spinner that then jumps. |
| **Ready** | always | the layout above |
| **Empty** | n/a | a settings page has no empty state |
| **Error** | a DataStore write throws | the row **reverts to its stored value** and one line appears under the card: `保存できませんでした`, Gothic 12.sp `c.accent`. A row is never left showing a value that was not persisted. |
| **SpeechUnavailable** | `speechAvailability() != Available` | 音声 shown but disabled: label and state both `c.inkFaint`, sub-line becomes `日本語の音声が入っていません` or `読み上げ機能がありません`. Semantics carry `disabled()`. Cues fall back to tones. |
| **SessionLive** | `activeSession != null` | 支度の長さ and 休息の初期値 carry a `c.inkFaint` footnote `いまの鍛錬には反映されません`. They stay editable — they simply do not retro-apply to a compiled timeline. 合図 and 表示 **do** take effect immediately, mid-session; this is the main reason Settings is reachable at all. |
| **Writing** | a setter in flight | optimistic — single-key DataStore edits are sub-millisecond and a spinner would flash. Failure reverts. |

**Actions** — every toggle row toggles its key and persists; every value row opens a wheel and persists
on settle; 単位 cycles; 安全のために navigates; ← and Back pop.

**Edge cases**

1. **支度の長さ = 0** is legal. The timeline compiler must emit **no** 支度 segment rather than a
   zero-length one — a zero-duration segment divides by zero in the ensō sweep. (Flagged to `03`.)
2. **All three cues off** is permitted and legitimate (a silent phone in a shared gym). No warning, no
   nag. The screen is then the only interface, which is why `画面を消さない` defaults on.
3. **音 on with the device in silent mode** — cues still fire, because `VibrationAttributes.USAGE_ALARM`
   and `ToneGenerator(STREAM_NOTIFICATION, …)` are alarm-class. Say so in the sub-line **on first
   enable only**: `マナーモードでも鳴ります`.
4. **音声 enabled, then the voice is uninstalled** — availability is re-probed on `ON_RESUME` of this
   page and of the player. The stored preference stays `true`, so reinstalling restores speech without
   the user re-finding the switch.
5. **Rest defaults changed while the builder is open** — impossible; the builder is `immersive` and
   Settings is unreachable from it. Stated so the invariant is not accidentally broken later.
6. **Rest defaults are defaults, not overrides.** Changing them must never rewrite an existing
   routine's stored rests. The label 休息の初期値 and the これから作る型に使われます footnote both exist
   to make that unambiguous — "default rest" is the most commonly misread setting in every logging app.
7. **`画面を消さない` off mid-session** — the flag clears next frame and the screen may sleep
   immediately. Expected: the clock is monotonic so nothing is lost, and the user just asked for it.
8. **Imperial with no imperial content** — Phase 1's only affected value is マーフ's mile, a Phase 2
   preset. The row ships in Phase 1 so it is not a schema-or-UI change later, but carries no false
   promise: it changes displayed distance only, never weights, which the app does not track.
9. **A wheel opened and dismissed without moving** writes nothing. The setter fires only when the
   settled value differs from the stored one.
10. **DataStore corruption at read** — `preferences` falls back to `GymPreferences()` rather than
    throwing. A settings page that cannot render is worse than one showing defaults, and every default
    here is safe.

**Accessibility**

- Toggle rows: `semantics { role = Role.Switch; toggleableState = …; stateDescription = if (on) "入"
  else "切" }` on the whole row, label as node text. TalkBack reads "振動、スイッチ、入". The visible
  word and the state description must always agree.
- Disabled 音声: `disabled()` plus the reason folded into the description —
  `"音声、切、日本語の音声が入っていません"` — so the user learns why rather than tapping a dead row.
- Value rows: `Role.Button`, `"支度の長さ、五秒"`; the wheel announces its title on open and the settled
  value on close.
- Section labels: `heading()`.
- Footnotes merge into the preceding card's semantics rather than being separate focus stops.
- Targets: rows 56.dp, 安全のために 64.dp, ← 48.dp. Dividers carry `clearAndSetSemantics {}`.
- Focus order is visual order; ← is the first focusable node.
- Nothing is conveyed by colour alone — the 入/切 word carries the state, the accent reinforces it.

**Pure logic to unit test**

```kotlin
fun clampPrepareSeconds(raw: Int): Int          // 0..15
fun clampStationRest(raw: Int): Int             // 0..300
fun clampRoundRest(raw: Int): Int               // 0..600
fun wheelSteps(range: IntRange, step: Int): List<Int>
fun secondsLabelJa(seconds: Int): String        // 0 → "なし", 5 → "五秒", 90 → "一分三十秒"
fun effectiveCues(prefs: GymPreferences, speech: SpeechAvailability): Set<CueChannel>
fun settingsRowStates(prefs: GymPreferences, speech: SpeechAvailability, sessionLive: Boolean): Map<SettingRow, RowState>
fun nextUnits(current: Units): Units
```

---

### GYM.SAFETY — 安全のために (for your safety)

Four paragraphs and a heading, not a feature.

- **Purpose** — the one place the app says plainly that it is not medical advice.
- **Entered from** `GYM.SETTINGS`; also shown once inline on `GYM.HOME` in the FirstRun state.
- **Exits to** the caller — Back or ← pops. **Tab bar** hidden (depth 3).
- **Data** — none, except `setSafetyNoteAcknowledged()` when reached from the first-run note.
- **Layout** — header idiom (`安全のために` Mincho 26.sp ls 3.sp), then body in Mincho 15.sp,
  lineHeight 26.sp, `c.inkSoft`, `padding(horizontal = 28.dp)`, leading with 「痛みを感じたらやめる」 as a
  standalone 18.sp `c.accent` line.
- **Accessibility** — one scrollable text region, `heading()` on the title, no interactive elements but
  the ←.
