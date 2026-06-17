# Tempo Launcher — Lifecycle, Persistence & UX Audit + Implementation Plan

Scope: make Tempo behave like a correct, well-mannered Android **home app** — proper launcher
lifecycle, durable data, and a complete end-to-end UX — without abandoning its "pure minimalism"
ethos. This document is an audit of the current code, a research summary of what Android launchers
must obey, and a phased implementation plan.

---

## Part 1 — Audit of the current implementation

Status legend: 🔴 broken/missing for a real launcher · 🟡 works but fragile/non-idiomatic · 🟢 fine.

### A. Launcher lifecycle & system integration

| # | Finding | Sev | Where |
|---|---------|-----|-------|
| A1 | **No `onNewIntent` handling.** Pressing Home while Tempo is already foreground does nothing. A launcher must reset to the Home screen (and clear search + dismiss keyboard) on every HOME press. | 🔴 | `MainActivity.kt` |
| A2 | **Clock coroutine is not lifecycle-aware.** `LaunchedEffect(Unit){ while(true){…delay(1s)} }` keeps ticking while the launcher is backgrounded (Activity stopped, composition retained), waking the CPU every second for nothing. Should be `repeatOnLifecycle(STARTED)`. | 🔴 | `TempoApp.kt:57` |
| A3 | **Clock state hoisted at the root** (`now` in `TempoApp`) → every tick recomposes the whole tree (Dock, Search, Notifications), not just the Home clock. | 🟡 | `TempoApp.kt` |
| A4 | **Ticks every second though only minutes are shown.** `HH:mm` + the kanji reading change once a minute; per-second ticks are wasted wakeups. | 🟡 | `TempoApp.kt` / `JapaneseDate` |
| A5 | **No way to become / detect the default home app.** No `RoleManager.ROLE_HOME` request, no "set as default" affordance. Users must find the system setting unaided. | 🔴 | (absent) |
| A6 | **Screen state persists across app-switches.** `screen` is `rememberSaveable`; returning from a launched app can land on Search/Notifications instead of Home. Tied to A1. | 🟡 | `TempoApp.kt` |
| A7 | **Predictive back not enabled.** No `enableOnBackInvokedCallback`; back is handled but not future-proofed for the platform's predictive-back animation. | 🟡 | manifest / `TempoApp.kt` |
| A8 | `configChanges`, `singleTask`, `excludeFromRecents`, `stateNotNeeded` set; HOME+DEFAULT filter present. | 🟢 | manifest |

### B. Data persistence & architecture

| # | Finding | Sev | Where |
|---|---------|-----|-------|
| B1 | **No ViewModel / no architecture layer.** All state lives in composables, so nothing survives process death cleanly, and the app list/search/notifications can't be shared or tested. | 🔴 | (absent) |
| B2 | **App list is re-queried on every Search entry.** `produceState(…context)` reruns `loadInstalledApps` (PackageManager query **+ decode every app icon into an ImageBitmap**) each time Search opens — slow, allocation-heavy, GC churn. | 🔴 | `SearchScreen.kt:53`, `AppRepository.kt:26` |
| B3 | **No live package updates.** Installs/uninstalls/updates/locale changes aren't observed, so the Search list goes stale until the screen is destroyed and re-created. | 🔴 | `AppRepository.kt` |
| B4 | **Theme uses synchronous SharedPreferences on the main thread.** Works for one flag, but DataStore (async, Flow-based) is the current recommendation and composes with the ViewModel layer. | 🟡 | `TempoApp.kt` |
| B5 | **All icons held in memory at full size.** `loadInstalledApps` eagerly decodes every launcher icon into an `ImageBitmap`; on a device with 150+ apps that's a large, unbounded heap cost. Needs a bounded cache + lazy/sized loading. | 🟡 | `AppRepository.kt:36` |
| B6 | `NotificationStore` (process-singleton `StateFlow`) is a reasonable service→UI bridge and the listener repopulates on reconnect. | 🟢 | `NotificationStore.kt` |

### C. End-to-end UX

| # | Finding | Sev | Where |
|---|---------|-----|-------|
| C1 | **Search doesn't auto-focus or raise the keyboard.** Entering 検索 should focus the field and show the IME; today it's an extra tap. | 🔴 | `SearchScreen.kt` |
| C2 | **No IME action.** Pressing the keyboard's Go/Search should launch the top result. | 🟡 | `SearchScreen.kt` |
| C3 | **Keyboard can cover the results list.** `adjustResize` is set but the list has no `imePadding()`, so bottom rows hide behind the IME. | 🟡 | `SearchScreen.kt` |
| C4 | **Search query isn't reset when leaving Search.** Re-entering shows the stale query. | 🟡 | `SearchScreen.kt` |
| C5 | **No app long-press actions.** App info / uninstall / app-shortcuts are baseline launcher expectations; none exist. | 🟡 | `SearchScreen.kt` |
| C6 | **No launch animation / source bounds**, and `launch()` silently no-ops on a null intent with no user feedback. | 🟡 | `AppRepository.kt:43` |
| C7 | **Notifications are read-only.** Tapping a row doesn't fire its `contentIntent`; there's no swipe-to-dismiss (`cancelNotification`). A notifications surface that can't open or clear is half a feature. | 🔴 | `NotificationsScreen.kt` |
| C8 | **No notification ordering.** Items are shown in `activeNotifications` order, not by ranking/post-time. | 🟡 | `TempoNotificationListener.kt` |
| C9 | Notification access prompt + re-check on resume + empty state are handled well. | 🟢 | `NotificationsScreen.kt` |

### D. Accessibility

| # | Finding | Sev | Where |
|---|---------|-----|-------|
| D1 | **Dock touch targets are ~39 dp** (23 dp icon + 8 dp padding), under the 48 dp minimum. | 🟡 | `Dock.kt` |
| D2 | **Dock buttons have no content descriptions / semantics** → TalkBack reads nothing meaningful. | 🟡 | `Dock.kt` |
| D3 | **Fixed `sp` sizes** (104sp clock, etc.) are fine, but no review against large font-scale / display-size settings; vertical date may clip. | 🟡 | `HomeScreen.kt` |

### E. Performance & battery

| # | Finding | Sev | Where |
|---|---------|-----|-------|
| E1 | Per-second clock wakeups (A2/A4) are the biggest avoidable battery cost. | 🟡 | `TempoApp.kt` |
| E2 | Cold-start does no heavy work on the main thread (good), but icon decode (B2/B5) will dominate first Search-open latency. | 🟡 | `AppRepository.kt` |
| E3 | Grain noise bitmap is cached by `drawWithCache` keyed on size — fine. | 🟢 | `Background.kt` |

---

## Part 2 — What an Android launcher must handle (research)

The canonical responsibilities of a home app, with the APIs Tempo should use. (Compiled from
Android platform behaviour and AOSP Launcher3 conventions; current as of the Android 15/16 era.)

1. **Be a well-behaved HOME activity.** `MAIN` + `CATEGORY_HOME` + `CATEGORY_DEFAULT` (✓),
   `singleTask` (✓), fast cold start, survive low-memory kills, restore on relaunch, and **reset to
   the home page on every HOME press** via `onNewIntent`.
2. **Default-home role.** Offer to become default with `RoleManager.createRequestRoleIntent(ROLE_HOME)`
   (API 29+) and detect current default via `RoleManager.isRoleHeld(ROLE_HOME)` / resolving the HOME
   intent. Provide a discreet entry point (long-press home, or a settings affordance).
3. **App inventory via `LauncherApps`, not bare `PackageManager`.** `LauncherApps.getActivityList(null, user)`
   gives launchable activities per `UserHandle` (handles **work profiles** and secondary users),
   badged icons (`getBadgedIcon`), and a **`LauncherApps.Callback`** for add/remove/change/availability —
   the correct way to keep the list live. Also react to **locale changes** (labels) and managed-profile
   availability.
4. **Launching apps.** Prefer `LauncherApps.startMainActivity(component, user, sourceBounds, opts)` (or
   `getLaunchIntentForPackage` for the primary user), pass `ActivityOptions.makeScaleUpAnimation`/
   `makeClipRevealAnimation` + source bounds for a polished launch, and **handle exceptions**
   (`ActivityNotFoundException`, `SecurityException`) with user-visible feedback.
5. **Per-app actions (long-press).** App info (`ACTION_APPLICATION_DETAILS_SETTINGS`), uninstall
   (`ACTION_DELETE` / `LauncherApps` for managed users), and **app shortcuts** (`getShortcuts` +
   `startShortcut`, plus pin requests). App info + uninstall are the minimum users expect.
6. **Notifications (only because Tempo surfaces them).** `NotificationListenerService` (✓); **tap →
   `contentIntent.send()`**, **dismiss → `cancelNotification(key)`**, order by `Ranking`, collapse
   groups, distinguish ongoing vs clearable, handle access **revocation**, and `requestRebind` after
   the service is disabled/re-enabled.
7. **Edge-to-edge & insets.** Transparent system bars, draw behind them (✓), apply system-bar insets
   (✓), apply **IME insets** for the search field, and respect **display cutouts** (✓ via theme).
8. **Theme & system UI.** Correct light/dark **status/nav icon** contrast per theme (✓ runtime),
   applied early to avoid a flash; decide whether to **follow system dark mode** or stay manual; persist
   the choice; set a matching `windowBackground` (✓).
9. **Lifecycle-aware work.** Pause clocks/animations/observers when `STOPPED` (`repeatOnLifecycle`),
   refresh volatile state (notification-access, default-home status) on `RESUME`.
10. **Accessibility.** ≥48 dp targets, content descriptions / semantics on every control, support font
    scale & display size, sufficient contrast, and honour reduce-motion.
11. **Performance & battery.** Sub-second cold start, **bounded icon cache** (e.g. `LruCache`) with
    lazy/sized decode off the main thread, minimal recomposition, small steady-state heap, and **no
    background polling** (minute-aligned clock, no wakelocks).
12. **Configuration & form factors.** Survive rotation/density/locale/uiMode (✓ `configChanges`),
    behave in multi-window / on foldables, and degrade gracefully on tablets.
13. **Wallpaper.** Decide `windowShowWallpaper`. *Tempo is intentionally opaque washi paper → no
    wallpaper.* Document this as a deliberate product choice.
14. **Predictive back (Android 13+).** Opt in (`android:enableOnBackInvokedCallback="true"`) and use
    Compose's predictive-back-aware handlers so the system back gesture animates correctly.
15. **Privacy & security.** Treat notification content as untrusted data, never log PII, keep package
    visibility scoped (✓ `<queries>`).

**Deliberately out of scope** (would contradict the minimalist brief): app-drawer grid, home-screen
widgets, folders, icon packs, multiple home pages, dock customization, wallpaper picker.

---

## Part 3 — Implementation plan

Target architecture: a thin **MVVM** layer — `LauncherViewModel` (+ `SavedStateHandle`) backed by
three repositories (`AppRepository` on `LauncherApps`, `ThemeRepository` on DataStore, and the
existing notification bridge promoted to `NotificationRepository`). Compose reads state via
`collectAsStateWithLifecycle()`. This single change resolves most lifecycle/persistence findings and
makes the rest small.

Phases are ordered by correctness-first; each lists tasks and acceptance criteria. Severity tags map
back to Part 1.

### Phase 0 — Architecture foundation ✅ *(implemented)*
- ✅ `LauncherViewModel` (+ `LauncherViewModelFactory`) exposing UI state (theme, screen, apps,
  search query, notifications, default-home status).
- ✅ `AppRepository` promoted to a process singleton holding a cached `StateFlow<List<AppInfo>>`
  (`ensureLoaded`/`refresh`, mutex-guarded); no more reload-on-every-Search-open. *(B2)*
- ✅ `ThemeRepository` on Jetpack **DataStore (Preferences)** with a `SharedPreferencesMigration`
  that imports the old `theme` key. *(B4)*
- ✅ `NotificationRepository` wraps `NotificationStore`.
- ✅ Composables read state via `collectAsStateWithLifecycle`. *(B1)*
- Deps added: `lifecycle-viewmodel-compose`, `datastore-preferences`.
- *Result:* theme persists via DataStore; rotation keeps state (VM); business logic out of composables.
- *Deferred to Phase 2:* `LauncherApps` migration + live updates (B3) and the bounded icon cache (B5).

### Phase 1 — Launcher lifecycle correctness ✅ *(implemented)*
- ✅ `MainActivity.onNewIntent` → `viewModel.resetToHome()` (screen=Home + clear search). *(A1, A6)*
- ✅ Clock moved to a lifecycle-aware, **minute-aligned** `rememberMinuteTime()` using
  `repeatOnLifecycle(STARTED)`, read only inside Home/Notifications so the dock never recomposes on
  tick. *(A2, A3, A4, E1)*
- ✅ `RoleManager.ROLE_HOME` request + held-state detection, refreshed on resume; affordance =
  long-press the home-indicator pill (which tints to accent when Tempo isn't default). *(A5)*
- ✅ Predictive back enabled (`enableOnBackInvokedCallback="true"`); Compose `BackHandler`s retained.
  *(A7 — full predictive-back progress animation is still a polish follow-up.)*
- *Accept:* HOME press returns to a clean Home; clock suspends when backgrounded; user can set Tempo
  default from within the app.

> **IME hide on reset:** resolved in Phase 3 — leaving Search removes the focused field from the
> composition, which drops the keyboard; launching the top result also hides it explicitly.

### Phase 2 — App inventory & launching ✅ *(implemented)*
- ✅ `AppRepository` rewritten on `LauncherApps`: `getActivityList(null, user)` across **all user
  profiles** (`UserManager.userProfiles` → work profile / secondary users), a registered
  `LauncherApps.Callback` pushing live updates into the cached `StateFlow`, plus a context-registered
  `ACTION_LOCALE_CHANGED` receiver that reloads labels + evicts icons. *(B3)*
- ✅ Bounded **icon `LruCache`** (256 entries) with sized (48dp), lazy, off-main-thread decode; rows
  paint a placeholder until the icon resolves. *(B5, E2)*
- ✅ Work-profile / secondary-user support via `UserHandle` (badged icons, profile-aware launch &
  app-details).
- ✅ Launch via `LauncherApps.startMainActivity` with **source bounds + `makeScaleUpAnimation`**, and
  `ActivityNotFoundException` / `SecurityException` caught with a toast (no more silent no-op). *(C6)*
- ✅ Long-press menu: **アプリ情報 (App info)** via `startAppDetailsActivity` and **アンインストール
  (Uninstall)** via `ACTION_DELETE`. *(C5)*
- No new Gradle deps (all platform / already-present).
- *Result:* install/remove/locale changes refresh Search live; first open is fast (no bulk icon
  decode); launching animates and surfaces failures.
- *Deferred:* **app shortcuts** (`getShortcuts`/`startShortcut`, gated on `hasShortcutHostPermission`)
  left as a follow-up to keep the long-press menu minimal.

### Phase 3 — Search UX ✅ *(implemented)*
- ✅ `FocusRequester` + `keyboard.show()` on entry; `imePadding()` on the results list. *(C1, C3)*
- ✅ `ImeAction.Go` → `KeyboardActions(onGo = …)` launches the top filtered result and hides the IME. *(C2)*
- ✅ Query cleared whenever Search is left (`goHome`/`goNotifications` reset it); IME drops with the
  field as the screen leaves composition. *(C4 + the Phase-1 IME-hide note)*
- ✅ Loading state ("・・・") while the first inventory builds.
- *Accept:* open 検索 → keyboard up, field focused; type + Go launches the top hit; leaving resets.

### Phase 4 — Notifications UX ✅ *(implemented)*
- ✅ Tap a row → `contentIntent.send()` (auto-cancels when the notification flags `FLAG_AUTO_CANCEL`);
  swipe either direction → `cancelNotification(key)` via the bound service. *(C7)*
- ✅ Ordered by the system `Ranking` then post-time; **group summaries dropped** (children kept);
  ongoing/non-clearable excluded. *(C8)*
- ✅ Access-revocation handled (`onListenerDisconnected` clears + drops the instance);
  `requestRebind` nudged on resume when access is granted. 
- *Accept:* tapping opens the source app; swiping clears it from Tempo + the shade; ordering is sane.

### Phase 5 — Accessibility ✅ *(implemented)*
- ✅ Dock buttons are now **48 dp** targets with `contentDescription` + `Role.Button` semantics; the
  glyph stays 23 dp. *(D1, D2)*
- ✅ The default-home pill exposes an accessible `onLongClick` action ("set Tempo as default") so the
  gesture isn't TalkBack-invisible.
- ✅ App rows carry click/long-click labels (起動 / メニュー).
- *Known trade-off (D3):* the 104sp display clock is intentionally fixed-size and is not designed to
  scale to extreme accessibility font sizes — a deliberate concession to the design's typographic
  scale rather than a bug. No custom motion to gate for reduce-motion (launches use the system
  animation, which already honours it).

### Phase 6 — Performance & battery ✅ *(addressed)*
- ✅ Steady-state wakeups removed already by the lifecycle-aware **minute** clock (Phase 1). *(E1)*
- ✅ Clock isolation: `rememberMinuteTime()` is read only inside Home/Notifications, so a tick never
  recomposes the dock or other layers. *(A3)*
- ✅ First Search open is cheap — lazy, bounded icon cache (Phase 2). *(E2)*
- ✅ `AppInfo` and `TempoNotification` marked `@Immutable` so Compose reliably skips unchanged rows.
- *Deferred (optional):* a generated **Baseline Profile** for faster cold start (needs the
  benchmark/profile tooling + a build run; documented, not added).

### Status
All seven phases (0–6) are implemented. Remaining explicitly-deferred, out-of-minimalism-scope items:
**app shortcuts** (Phase 2 follow-up), **Baseline Profile** (Phase 6 optional), and the larger
non-goals listed above (app drawer, widgets, folders, icon packs). Build/run verification is still
pending a Gradle sync (artifacts not yet downloaded in this environment).
