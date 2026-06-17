<div align="center">

<img src="art/logo/tempo_logo.svg" width="112" height="112" alt="Tempo logo — the kanji 拍 on washi paper" />

# Tempo

**An ultra-minimal Android launcher — airy washi paper, Japanese typography, no distractions.**

[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)
[![Android CI](https://github.com/eddiegulay/tempo/actions/workflows/android.yml/badge.svg)](../../actions/workflows/android.yml)
![Min SDK](https://img.shields.io/badge/minSdk-35-3DDC84)
![Kotlin](https://img.shields.io/badge/Kotlin-2.1-7F52FF)
![Jetpack Compose](https://img.shields.io/badge/Jetpack%20Compose-UI-4285F4)

</div>

Tempo is a home-screen replacement that does three quiet things well: tell the time, find an app,
and show your notifications — on a calm, paper-cream canvas with a single vermillion accent. It was
designed around one idea: **peace, no distractions.**

> Screenshots: _coming soon_ — add them under `docs/screenshots/` and link them here.

## Features

- **Home** — a faint sumi-e ensō behind a large mincho clock, the date in vertical Reiwa-era kanji
  (令和八年・六月十七日・水曜日), a live spoken-style reading (午後九時一分), and a single 静 ("stillness") seal.
- **Search (検索)** — live-filtered list of every installed app (work-profile apps included), with a
  scale-up launch animation and a long-press menu (app info / uninstall).
- **Notifications (通知)** — your real notifications, tap to open, swipe to dismiss, ordered by the
  system ranking.
- **Paper / AMOLED themes** — a one-tap toggle between washi cream and true black, persisted.
- **A well-behaved launcher** — HOME-press always returns to a clean home, a lifecycle-aware
  minute clock (no idle wakeups), default-home onboarding, edge-to-edge insets, predictive back, and
  accessible controls.

## Tech stack

- **Kotlin** + **Jetpack Compose** (Material 3) — the entire UI is Compose, drawn edge-to-edge.
- **MVVM** — a single `LauncherViewModel` over three repositories (apps, theme, notifications).
- **Jetpack DataStore** for settings; **`LauncherApps`** for a live app inventory; a
  **`NotificationListenerService`** for real notifications.
- No DI framework, no third-party UI libraries — just AndroidX.

See [`docs/IMPLEMENTATION_NOTES.md`](docs/IMPLEMENTATION_NOTES.md) for the module map and
[`docs/LAUNCHER_AUDIT_AND_PLAN.md`](docs/LAUNCHER_AUDIT_AND_PLAN.md) for the launcher-correctness
audit and the phased plan that shaped the current code.

## Build & run

Requirements: **JDK 17+** and the **Android SDK** (compileSdk 36, minSdk 35).

```bash
git clone https://github.com/eddiegulay/tempo.git
cd tempo
./gradlew assembleDebug          # build the debug APK
./gradlew installDebug           # build + install on a connected device/emulator
./gradlew testDebugUnitTest      # run unit tests
```

Open the project in Android Studio (latest stable) and let it sync; `local.properties` is generated
for you and is intentionally git-ignored.

### Using it as your launcher

1. Install the app, then press Home and pick **Tempo** (or Settings → _Default apps → Home app_).
   In-app, long-press the home-indicator pill (it tints vermillion when Tempo isn't default yet) to
   jump straight to the system picker.
2. For the **Notifications** screen, grant notification access when prompted — the in-app prompt
   deep-links to the right Settings page. App search needs no special permission.

## Project layout

```
app/src/main/java/io/eddiegulay/tempo/
├─ MainActivity.kt              # HOME activity; owns the ViewModel + lifecycle
├─ LauncherViewModel.kt         # single source of UI state
├─ data/                        # AppRepository, ThemeRepository (DataStore), JapaneseDate
├─ notification/                # listener service, store, repository
└─ ui/                          # Compose screens (Home/Search/Notifications), Dock, theme
```

## Contributing

Contributions are welcome — please read [`CONTRIBUTING.md`](CONTRIBUTING.md) and the
[Code of Conduct](CODE_OF_CONDUCT.md). Bug reports and feature requests go through the
[issue templates](.github/ISSUE_TEMPLATE).

## Credits

- The app logo is the kanji **拍** ("beat" — a single-character reading of *tempo*) set in Hiragino
  Mincho, in the same vermillion as the home seal. The master asset is
  [`art/logo/tempo_logo.svg`](art/logo/tempo_logo.svg); the Android adaptive icon (vector foreground
  + washi gradient background + themed monochrome layer) is generated from the same glyph outline.
- The visual direction was prototyped in **Claude Design** and implemented natively here.
- Display fonts: **Shippori Mincho** (clock, date, app names, 静 seal) and **Zen Kaku Gothic New**
  (notification copy, romaji) — Google Fonts under the SIL Open Font License, bundled under
  `app/src/main/res/font` so the design renders with its intended type rather than the platform
  Noto CJK fallback.

## License

[MIT](LICENSE) © 2026 Eddie Gulay
