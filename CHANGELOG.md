# Changelog

All notable changes to this project are documented here. The format is based on
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and this project aims to follow
[Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

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

[Unreleased]: https://github.com/eddiegulay/tempo/commits/main
