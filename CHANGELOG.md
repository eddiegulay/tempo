# Changelog

All notable changes to this project are documented here. The format is based on
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and this project aims to follow
[Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

## [0.2.0] - 2026-08-14

### Added
- **鍛錬 (Training)** — Tempo now runs your workout. Not a log you fill in afterwards: a timer you
  train against, built around the same idea as the rest of the launcher, which is that the screen
  should ask for as little of your attention as possible while you are busy doing something else.
  - **The ensō is the timer.** The brushed circle on the player is not decoration next to a countdown
    — it *is* the countdown, closing as the interval runs out. Mid-set you can read a nearly-closed
    circle from arm's length in a way you cannot read a two-digit number.
  - **Seven kinds of workout**, because "a workout" is not one shape: fixed circuits, as-many-rounds-
    as-possible against a clock, for-time, for-time with rest, every-minute-on-the-minute, ascending
    EMOM, and fixed sets. Nine built-in routines ship with the app, including the benchmark workouts
    people actually train — Cindy, Murph, Tabata, Death by Burpees.
  - **Nothing advances past a rep-based exercise on its own.** A timer that decides you have finished
    twenty push-ups has decided something it cannot know. You press 済 when you are done, and until
    then it waits.
  - **A workout you cut short is still a workout.** Stop halfway and the session is recorded as what
    it was — marked 途中まで, no personal-record claim, no ceremony — rather than discarded. The
    alternative teaches you to fake the last round.
  - **Records that answer questions you would actually ask**: what you have done, how often, your
    personal bests, and how your load has moved. Tempo computes a training-load ratio internally to
    pace how fast a routine is allowed to ramp — and deliberately never draws it. The injury-risk
    bands that number is famous for have substantially failed to replicate, and a launcher is in no
    position to make a medical claim.
  - **It speaks and it buzzes**, so you can put the phone down. Cues at the half-way point, the last
    round, and the end, with the screen kept awake only while a session is actually running.
  - **Reached from the dock** — a fourth button beside Home, Search and Notifications.
- **English.** Tempo was written in Japanese, from the ground up, and stays that way by default. It
  now also speaks English, chosen from a picker in the Search header or on the very first screen.
  - **The language is offered before anything is explained.** The first-launch walkthrough asks for
    notification access and the home-app role, and explains in prose why it wants each and that
    nothing leaves your device. Reading that is the point of it. The language row therefore sits above
    the greeting, not buried in a settings page you would find afterwards.
  - **It is a picker, not a switch**, and both options are written in their own language — 日本語 and
    English. A blind toggle is fine for a theme, where a wrong guess is undone by eye. Guess wrong on
    a language and you may not be able to read the control that would put it back.
  - **The catalogue was already bilingual.** Every built-in exercise has carried an English name in
    the database since the first version; it had simply never been shown.
  - **Numbers and dates are translated, not just words.** Japanese counts in kanji and marks a
    duration differently depending on whether you chose it or the app measured it. English does
    neither, so those rules end at the language boundary rather than being mimicked badly.
  - Japanese is unchanged. Every Japanese string in the app is the one that shipped before.

### Changed
- **Everything you press now responds like ink on paper.** Buttons, rows, chips and tabs used to flash
  a grey rectangle when touched — a Material default that had nothing to do with Tempo's palette and
  square corners on an app with none. The press now settles into the shape of the control it belongs
  to, in the theme's own ink, and fades out more slowly than it comes in.
- **Home's corner turns with the writing.** The date, weekday and next event are set vertically in
  Japanese, as they should be. In English they are laid out horizontally, because stacking Latin
  letters one per line is a ransom note rather than typography.

### Fixed
- **Tempo no longer writes a placeholder into your real calendar.** An event with no title was shown
  as （無題）, and editing anything about it — moving it an hour, say — saved that placeholder as the
  event's actual name. It then synced to Google and to everyone invited. Untitled events now stay
  untitled, and you can reschedule one without renaming it.
- **The gym no longer crashes on first open.**
- **Opening a station no longer destroys its note**, unfolding a wheel no longer rewrites the value it
  is showing you, and a settings change followed by Back is no longer discarded.
- **Timestamps read consistently.** Notification times used a date format found nowhere else in the
  app and ambiguous outside Japan and the US — 6/17 could be June 17th or the 6th of July.
- **Digits stay digits.** Several clocks and countdowns were formatted without a locale, so on some
  devices they rendered in a numeral system the layout had no room for.
- **"Undo" is reachable again.** The strip that lets you take back a cleared notification was drawn
  underneath the floating dock — the one control in the app with a deadline on it, and you could not
  press it. The last notification in the list was hidden the same way.
- **The Pomodoro controls can be used with a screen reader.** They were drawn as raw gesture targets,
  which publish no button to TalkBack, so they were invisible to it. Several controls that were too
  small to hit comfortably — some barely half the recommended size, including the one you reach for
  when something has already gone wrong — are now full-sized.

## [0.1.0] - 2026-07-12

### Added
- **Calendar (予定)** — Tempo now shows your real agenda. It reads the device's calendar provider,
  which is the same place the Google Calendar app syncs into, so the events on your launcher are the
  very ones on your laptop. No sign-in, no network of its own, no Play Services: if an event reaches
  your phone, it reaches Tempo, and an event added in Tempo syncs back out to every device you own.
  - **The next event replaces the date on Home.** The top-right cluster used to print today's date in
    Japanese; it now shows what is coming next — the day, the time, and the title — and falls back to
    the date when there is nothing ahead or no calendar access.
  - **Written vertically** (縦書き). Kanji and kana stack upright, one glyph per cell, the way they are
    set on a printed page; a run of Latin turns a quarter-turn and reads as a single stroke rather
    than a column of loose letters; short numbers sit upright in place (縦中横). A vermillion dot
    appears when the event is within half an hour.
  - **Tap the corner to open the agenda** — the next fortnight, grouped by day, with 今日 and 明日
    named rather than dated. In-progress events are marked いま. It is the only way in: the calendar
    has no dock tab, because a launcher's home screen should stay a home screen.
  - **Add and edit events in place**, without leaving for another app. Title, all-day, start, end,
    location, and — when you have more than one — which calendar it lands on. The date and time are
    dialled on a 栞 (bookmark) wheel rather than a Material date picker, so the composer looks like the
    rest of Tempo and not like a dialog that wandered in from another application.

### Changed
- **Every change to the calendar now asks first.** Adding, editing, and deleting each restate what is
  about to happen — the event, its time, and the consequence — and wait to be told yes. A calendar is
  not private state: what Tempo writes appears on your other devices, and a deletion is withdrawn from
  everyone invited and cannot be taken back. That is worth a deliberate second tap, especially on a
  launcher, which lives in a pocket.
- **Repeating events are read-only**, and open in your calendar app instead (カレンダーで開く). Editing
  one occurrence of a series means writing the provider's exception rows, and getting it subtly wrong
  silently rewrites a meeting for everyone on the invite. Tempo would rather hand you to the tool that
  does it properly than quietly corrupt a shared calendar. Calendars you only have read access to —
  a holiday feed, a colleague's shared calendar — behave the same way.

### Fixed
- **An unreadable calendar can no longer masquerade as an empty one.** If the calendar could not be
  read, Tempo used to render a calm, empty page that said "予定はありません" — you had no way to tell a
  free day from a broken one, and you would have believed it and missed a meeting. Loading, empty, and
  failed are now three different states, and Tempo will only claim your day is clear when it actually
  knows that to be true.
- **A failed save no longer throws your draft away.** Saving used to return to the agenda whether or
  not the event had been written, so a rejected save left you looking at a list that did not contain
  the event you had just typed, with nothing said. Tempo now stays exactly where you are, keeps every
  word, tells you what went wrong, and lets you fix it and try again.
- **Nothing dead-ends.** Every failure in the calendar — access revoked, no writable calendar, an event
  deleted on another device, a provider that refuses the write, no calendar app to hand off to — now
  says what happened and offers the one thing that would fix it: ask for access again, open Settings,
  add an account, or simply try again.

## [0.0.9] - 2026-07-02

### Removed
- **All-files access permission**: Tempo no longer requests `MANAGE_EXTERNAL_STORAGE` (All-files
  access), which was causing Google Play Protect to flag installs as harmful and block installation
  on many devices. A launcher has no need for device-wide file access. The legacy
  `WRITE_EXTERNAL_STORAGE` permission and `requestLegacyExternalStorage` are gone with it, so Tempo
  now asks for **no storage permission at all**. The 10-day app blockade still survives an
  uninstall/reinstall — its ledger is now carried by Android's built-in app backup (restored from
  your Google account on reinstall) instead of a file in shared storage.

### Fixed
- **Screen no longer stays awake after leaving Focus mode**: opening the flip clock / Pomodoro used
  to leave the display permanently awake — it never slept again, even back on the Home screen.
  Keep-awake is now bound to the Focus surface and released on every other screen, so the device
  returns to its normal sleep behaviour the moment you leave Focus. The fix also holds when Tempo
  isn't set as the default launcher.

## [0.0.8] - 2026-06-30

### Changed
- **Focus mode stays immersive**: the status bar is now kept *actively* hidden for the whole session.
  The system bars are re-hidden on every window-focus regain — pulling the notification shade, a
  toast, a permission prompt, or returning from another app no longer lets the bar creep back. A
  deliberate swipe still peeks the bars, then they auto-hide.
- **Screen stays awake in Focus mode**: while the flip clock / Pomodoro is showing, the display no
  longer dims or sleeps, so the clock is always readable. The wake-lock is scoped to the focus
  surface and released the moment you leave it.

## [0.0.7] - 2026-06-28

### Added
- **Focus mode** — a full-screen, landscape *flip clock* for distraction-free time. Reach it by
  **long-pressing the Home clock** and confirming the prompt; the screen rotates to landscape and
  hides the system bars for a calm, immersive surface. Back (or the HOME key) returns you to a clean
  Home and restores the previous orientation.
  - **Flip clock**: split-flap digit cards in the sumi-e style showing `HH:MM`, where each changed
    digit folds over as it ticks. A single **tap** reveals or hides the seconds.
  - **Pomodoro**: **long-press** anywhere on the focus surface to switch between the plain clock and a
    Pomodoro timer — 25-minute focus, 5-minute short break, and a 15-minute long break every fourth
    session. Tap to start or pause, with controls to reset or skip the current block. Four dots track
    progress toward the next long break, and the active phase is labelled in kanji (集中 / 休憩 / 長休憩).

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
