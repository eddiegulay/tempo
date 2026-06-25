# Changelog

All notable changes to this project are documented here. The format is based on
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and this project aims to follow
[Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

## [0.0.6] - 2026-06-25

### Changed
- **Wider device support**: the minimum supported OS drops from Android 15 (API 35) all the way to
  **Android 10 (API 29)**, bringing Tempo to far more phones. Every version-sensitive code path now
  branches on the OS level, so nothing regresses on newer releases.
- **The blockade on Android 10**: All-files access doesn't exist before Android 11, so on Android 10
  the uninstall-proof ledger mirror is backed by legacy shared storage (`WRITE_EXTERNAL_STORAGE` plus
  `requestLegacyExternalStorage`), requested as a one-time runtime permission. Android 11+ is
  unchanged and still uses All-files access.

## [0.0.5] - 2026-06-18

### Changed
- **Notifications (通知)**: each notification is now a soft, rounded *washi card* — an 18 dp corner
  radius, a faint paper fill, and roomy interior padding — with calm spacing between items, replacing
  the flat full-bleed rows split by hairline dividers. Inline actions and per-app group headers
  realign to the new card inset. Swipe-to-dismiss, grouping, and accessibility semantics are unchanged.

### Added
- **Cover art**: the 拍 glyph set inside a hand-drawn sumi-e ensō with the 静 seal on the washi ground
  ([`art/cover/tempo_cover.svg`](art/cover/tempo_cover.svg)), now the README hero banner.

### Docs
- Refreshed the Home and Search screenshots and added a Notifications screenshot; the README now leads
  with the cover and shows a three-up screen gallery (Home · Search · Notifications).

## [0.0.4] - 2026-06-18

### Changed
- Nudged the primary ink colour for better readability and a more balanced palette.

## [0.0.3] - 2026-06-18

### Added
- **AppGlyphs**: a set of hand-drawn monochrome line glyphs now stands in for platform app icons in
  Search (検索) and the hidden-apps page, keeping the drawer on-brand and removing per-app bitmap
  decoding and icon caching.

### Changed
- Renamed and retuned the dark theme to **Sumi** (warm charcoal), with refreshed night colours.

## [0.0.2] - 2026-06-18

### Changed
- **Search (検索)**: the field no longer auto-focuses or pops the keyboard on entry — the screen opens
  calm, and you tap to start typing.

## [0.0.1] - 2026-06-18

### Added
- Home screen: sumi-e ensō, large mincho clock with a live kanji reading, vertical Reiwa-era date,
  and the 静 seal.
- Search (検索): live-filtered list of installed apps backed by `LauncherApps` (work-profile aware),
  lazy/cached icons, scale-up launch animation, and a long-press menu (app info / uninstall).
- Notifications (通知): real device notifications via a `NotificationListenerService` — tap to open,
  swipe to dismiss, ordered by system ranking.
- Paper / AMOLED theme toggle, persisted with Jetpack DataStore.
- Launcher correctness: HOME-press reset, lifecycle-aware minute clock, default-home onboarding via
  `RoleManager`, edge-to-edge insets, predictive back, and accessible controls.
- MVVM architecture (`LauncherViewModel` + app/theme/notification repositories).
- Brand: vermillion 拍 adaptive launcher icon (vector + gradient + themed monochrome) and master SVG.
- Open-source scaffolding: README, MIT license, contribution guide, code of conduct, CI, issue/PR
  templates, and editor config.

[Unreleased]: https://github.com/eddiegulay/tempo/compare/v0.0.6...HEAD
[0.0.6]: https://github.com/eddiegulay/tempo/compare/v0.0.5...v0.0.6
[0.0.5]: https://github.com/eddiegulay/tempo/compare/v0.0.4...v0.0.5
[0.0.4]: https://github.com/eddiegulay/tempo/compare/v0.0.3...v0.0.4
[0.0.3]: https://github.com/eddiegulay/tempo/compare/v0.0.2...v0.0.3
[0.0.2]: https://github.com/eddiegulay/tempo/compare/v0.0.1...v0.0.2
[0.0.1]: https://github.com/eddiegulay/tempo/releases/tag/v0.0.1
