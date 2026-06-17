# Contributing to Tempo

Thanks for your interest in improving Tempo! This is a small, opinionated project — an
**ultra-minimal** launcher — so contributions are weighed against that ethos as much as their code.

## The minimalism bar

Tempo deliberately omits app drawers, widgets, folders, icon packs, multiple home pages, and dock
customization. Please open an issue to discuss before building a feature in those areas — a great PR
that doesn't fit the product vision is still hard to accept. Bug fixes, accessibility, performance,
and correctness improvements are always welcome.

## Getting started

1. Fork and clone the repo.
2. Requirements: **JDK 17+** and the **Android SDK** (compileSdk 36, minSdk 35).
3. Build and test:
   ```bash
   ./gradlew assembleDebug
   ./gradlew testDebugUnitTest
   ./gradlew lintDebug
   ```
4. Open in the latest stable Android Studio and let it sync.

## Branch & PR workflow

- Create a topic branch from `main` (e.g. `fix/search-ime-focus`).
- Keep PRs focused and reasonably small; one logical change per PR.
- Reference any related issue in the description.
- Make sure `assembleDebug`, unit tests, and lint pass before requesting review.

## Coding style

- Kotlin official style (enforced by `.editorconfig`; `kotlin.code.style=official`).
- Match the surrounding code: small composables, state hoisted into the ViewModel, repositories for
  anything touching the system (`PackageManager`/`LauncherApps`/notifications/DataStore).
- Prefer clear names and short doc comments on non-obvious classes/functions, as in the existing code.
- No new third-party dependencies without discussion — the project intentionally sticks to AndroidX.

## Commit messages

Write imperative, present-tense summaries (e.g. "Add IME focus on Search entry"). Group related
changes; avoid mixing refactors with behavior changes.

## Reporting bugs / requesting features

Use the [issue templates](.github/ISSUE_TEMPLATE). For bugs, include device, Android version, and
clear reproduction steps. For features, explain how they fit the minimalism bar above.

By contributing, you agree that your contributions are licensed under the project's
[MIT License](LICENSE).
