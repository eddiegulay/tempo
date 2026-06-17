# Tempo Launcher — implementation notes

Native Android recreation of the **Tempo** design (`Tempo Launcher.dc.html`) — an ultra-minimal,
washi-paper, Japanese-typography home launcher. Built with Jetpack Compose.

The design's HTML/CSS was a phone *mockup*; the device frame and the painted status bar were mockup
chrome, so they're intentionally dropped in favour of the real system bars (drawn edge-to-edge).

## Screens (match the prototype)

- **Home** — faint sumi-e ensō ring, large mincho clock + spoken reading (午後九時一分), the date in
  vertical Reiwa kanji (令和八年・六月十七日・水曜日), and the lone vermillion 静 seal. Nothing else —
  the user's final instruction was "pure minimalism", so the home favourites row was removed.
- **Search (検索)** — bottom-ruled mincho input over a live-filtered list of installed apps. Tapping
  launches the app. (The prototype's monochrome line icons are replaced with real app icons since
  recognisability matters in a working launcher; labels stay in the mincho/gothic type system.)
- **Notifications (通知)** — real device notifications via a `NotificationListenerService`, with a
  calm tap-to-enable prompt until access is granted, and a 通知はありません empty state.
- **Theme toggle** — paper ⇄ AMOLED (true black), persisted to `SharedPreferences`.

## Source layout (`app/src/main/java/io/eddiegulay/tempo/`)

```
MainActivity.kt                    # HOME activity, edge-to-edge, hosts Compose
ui/TempoApp.kt                     # root: screen state, theme, live clock, back handling, dock
ui/HomeScreen.kt                   # ensō (Canvas arc), vertical date, clock, 静 seal
ui/SearchScreen.kt                 # mincho input + live-filtered app list
ui/NotificationsScreen.kt          # listener-gated notification list + prompt/empty states
ui/Dock.kt                         # bottom nav (Home/Search/Bell) + theme toggle + indicator pill
ui/LineIcon.kt                     # renders the design's SVG glyph paths (PathParser) + TempoIcons
ui/Background.kt                   # Modifier.tempoBackground: radial washi gradient + grain tile
ui/theme/TempoTheme.kt             # TempoColors + Paper/Amoled palettes + LocalTempoColors
ui/theme/Type.kt                   # Mincho / Gothic font families (system fallback for now)
data/JapaneseDate.kt               # kanji numerals, Reiwa era, reading, day-of-week
data/AppRepository.kt              # query launchable apps + launch intent
notification/NotificationStore.kt  # StateFlow bridge service -> UI
notification/TempoNotificationListener.kt  # NotificationListenerService
```

## Gradle config (wired — verify versions on first sync)

The Kotlin + Compose plugins and dependencies are now declared in `gradle/libs.versions.toml`,
`build.gradle.kts`, and `app/build.gradle.kts`. They were added offline, so on your first sync
double-check these pinned versions resolve against your toolchain (AGP 9.2.1 / Gradle 9.4.1 /
Java 23) and bump if Gradle reports a mismatch:

- `kotlin = "2.1.0"` (with the matching `kotlin.plugin.compose`)
- `composeBom = "2025.01.00"`, `activityCompose = "1.9.3"`, `lifecycle = "2.8.7"`, `datastore = "1.1.1"`
- bumped `coreKtx`, `junitVersion`, `espressoCore` to recent stable
- removed the unused `appcompat` / `material` (Views) deps — the UI is fully Compose
- Phase 0/1 added `lifecycle-viewmodel-compose` and `datastore-preferences` (see `LAUNCHER_AUDIT_AND_PLAN.md`)

**Fonts (optional, for pixel-perfect type):** drop `Shippori Mincho` and `Zen Kaku Gothic New`
`.ttf` into `res/font` and point `ui/theme/Type.kt` at them (currently falls back to system
serif/sans, which already render CJK via Noto).

## Runtime notes

- Set Tempo as the default Home app (system will prompt, or Settings → Default apps → Home app).
- Notifications screen needs **Notification access** granted once (the in-app prompt deep-links to
  the right Settings page). App icons in Search require no permission — the manifest `<queries>`
  block covers launcher visibility on Android 11+.
