# Changelog

All notable changes to this project are documented here. The format is based on
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and this project aims to follow
[Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

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

[Unreleased]: https://github.com/eddiegulay/tempo/compare/v0.0.5...HEAD
[0.0.5]: https://github.com/eddiegulay/tempo/compare/v0.0.4...v0.0.5
[0.0.1]: https://github.com/eddiegulay/tempo/releases/tag/v0.0.1
