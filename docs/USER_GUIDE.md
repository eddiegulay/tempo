# Tempo — User Guide

Tempo is an ultra-minimal Android **home-screen replacement** (launcher). It does three quiet
things and nothing else: **tell the time**, **find an app**, and **show your notifications** — all
on a calm washi-paper canvas with a single vermillion accent and Japanese typography.

There are no home-screen widgets, no app drawer grid, no folders, no wallpaper picker, and no
settings menu. The design is deliberate: **peace, no distractions.**

---

## 1. Getting started

### Install

Install the Tempo APK on a device or emulator running **Android 15 (API 35) or newer**. (For a
build from source, see the [README](../README.md).)

Installing Tempo does **not** change anything on its own — it just becomes *available* as a home
app. You have to choose it.

### Make Tempo your home screen

You have two ways to set Tempo as your launcher:

1. **Press the Home button / swipe up Home.** Android shows a chooser; pick **Tempo** (tap "Always"
   if offered).
2. **Long-press the home-indicator pill.** Inside Tempo, the slim horizontal pill at the very bottom
   of the screen acts as a shortcut. While Tempo is *not* yet your default home app, this pill is
   tinted **vermillion** as a gentle "hold me" cue. Long-press it and Tempo takes you straight to the
   system's home-app picker.

Once Tempo is the default, the pill turns a faint neutral grey — that's your confirmation. Tempo
re-checks this status every time it returns to the foreground, so if you change it in
**Settings → Apps → Default apps → Home app**, the pill updates automatically.

> **Note:** App search works immediately and needs no permission. The Notifications screen needs one
> permission, granted once — see [§5 Permissions](#5-permissions).

---

## 2. Moving around

Tempo has exactly **three screens**, reached from the **dock** — the row of icons fixed at the
bottom of every screen:

| Dock icon | Screen | What it shows |
|-----------|--------|---------------|
| Home glyph | **Home** (ホーム) | The clock, date, and ensō |
| Search glyph | **Search** (検索) | Find and launch an app |
| Bell glyph | **Notifications** (通知) | Your live notifications |
| Sun / Moon glyph | *(not a screen)* | Toggles the theme — see [§6](#6-themes) |

The icon of the screen you're on is shown in **vermillion**; the others stay faint. The sun/moon
theme toggle always stays faint (it's an action, not a destination).

### The Back gesture / button

Back never leaves Tempo — a launcher *is* the home screen, so there's nowhere "behind" it.

- From **Search** or **Notifications**, Back returns you to **Home**.
- On **Home**, Back does nothing (you're already home).

### The Home button

Pressing the device Home button while Tempo is open always returns you to a **clean Home screen** —
it clears any search text and resets to the clock. This matches how every Android launcher behaves.

---

## 3. The Home screen

Home is intentionally sparse. From top to bottom you'll see:

- **A faint ensō** — the broken sumi-e brush ring drawn behind everything, very low-contrast on
  purpose.
- **The date, vertically, in the top-right** — written in Reiwa-era kanji across three columns that
  read right-to-left, e.g. `令和八年 ・ 六月十七日 ・ 水曜日` (Reiwa year 8 · June 17 · Wednesday).
- **A large clock** in mincho type, e.g. `21:01`.
- **A spoken-style reading** of the time just below it, e.g. `午後九時一分` ("afternoon, nine
  o'clock, one minute"). When the minute is `00`, the "minutes" part is omitted (`午後九時`).
- **The 静 seal** — a single small vermillion stamp meaning *stillness* (sei/shizu), the only
  splash of colour on the screen.

### How the clock behaves

The clock updates **once per minute**, flipping exactly on the minute boundary (Tempo only displays
hours and minutes, so it never needs to tick per second). When Tempo is in the background the clock
loop **suspends entirely** — it causes no battery-draining wakeups — and re-reads the current time
the instant you return.

The date, era, and reading all follow your device's system clock and locale-independent Japanese
formatting; they recompute as the day rolls over.

---

## 4. Search — finding and launching apps

Tap the **Search** (検索) dock icon. The screen opens with the text field already focused and the
keyboard up, so you can start typing immediately.

### Finding an app

- Type any part of an app's **name** *or* its **package name** (e.g. `chrome` or `com.android`).
  Matching is case-insensitive and updates live as you type.
- The list shows every **launchable app on the device**, sorted alphabetically. This includes apps
  in a **work profile** or other user profiles, each shown with its proper badged icon. (Tempo
  itself is hidden from the list.)
- Each row shows the app **icon**, its **name** (mincho type), and its **package name** (small grey
  text beneath).

### Launching

- **Tap a row** to launch that app. The app opens with a subtle **scale-up animation** that grows
  out of the row you tapped.
- **Press the keyboard's "Go" / enter key** to instantly launch the **top result** — handy for
  launching by typing a few letters and hitting Go without lifting your thumb.

### App management (long-press)

**Long-press any app row** to open a small menu with two actions:

- **アプリ情報 (App info)** — opens the system App-details page for that app.
- **アンインストール (Uninstall)** — starts the system uninstall flow for that app.

### What you'll see while searching

- `・・・` — the app list is still loading (only briefly, on first open).
- `見つかりません` ("not found") — your query matched no apps.

The app list stays **live**: installing, removing, or updating an app, or changing the system
language, refreshes the list automatically without you reopening Search.

---

## 5. Notifications

Tap the **Notifications** (通知) dock icon. The header shows 通知 with today's date in kanji beneath
it.

### Granting access (one time)

To read your notifications, Tempo needs Android's **Notification access** permission. Until you
grant it, the screen shows a calm prompt:

> 通知へのアクセス
> **タップして許可** ("tap to allow")

Tap **タップして許可** and Tempo deep-links you straight to the system **Notification access**
settings page. Enable Tempo there, then return — the screen detects the change automatically (it
re-checks every time Tempo comes back to the foreground) and starts showing your notifications.

This permission is granted **once** and persists. You can revoke it anytime in
**Settings → Apps → Special app access → Notification access**.

### Reading notifications

Once access is granted, the screen lists your device's **current, clearable notifications**:

- Each row shows the app's **small icon** (tinted to match the theme), the notification **title**,
  up to three lines of **body** text, the **app name**, and a **timestamp**.
- Timestamps are relative: today's items show the time (`21:01`), yesterday's show `昨日`
  ("yesterday"), and older items show a `month/day` date.
- Notifications are ordered by the **system's own ranking** (most relevant first), then by how
  recently they arrived.
- Group **summary** rows are hidden — you see the individual notifications, not the "N new messages"
  bundle headers.
- **Ongoing / non-clearable** notifications (media players, active calls, persistent services) are
  intentionally **not shown**.

When there's nothing to show, the screen reads `通知はありません` ("there are no notifications").

### Acting on a notification

- **Tap** a notification to open it (fires its content action, exactly like tapping it in the system
  shade). If the notification is the auto-dismiss kind, it clears after you open it.
- **Swipe** a row left **or** right to dismiss it. This clears it from the system shade too — Tempo
  isn't a separate inbox, it's a window onto your real notifications.

> **Accessibility:** Each notification is announced as a single readable unit to TalkBack, with an
> explicit **消去 ("dismiss")** action — so screen-reader users can clear a notification without
> needing the swipe gesture (which assistive services can't see).

---

## 6. Themes

Tempo ships with two palettes, toggled by the **sun/moon icon** in the dock (the fourth icon):

- **Paper** — a warm washi-cream radial wash with sumi-ink text, a vermillion accent, and a faint
  paper-grain texture. The default.
- **AMOLED** — true black (great for OLED screens and battery), warm off-white text, and a slightly
  brighter vermillion. No grain.

Tap the toggle to switch instantly. Your choice is **saved** and restored next time you open Tempo.
The system status-bar and navigation-bar icons automatically flip light/dark to stay legible against
the active theme.

---

## 7. Gesture & control reference

| Action | Result |
|--------|--------|
| Tap dock Home / Search / Bell | Switch to that screen |
| Tap dock sun/moon | Toggle Paper ⇄ AMOLED theme |
| Back (from a sub-screen) | Return to Home |
| Device Home button | Return to a clean Home screen |
| Type in Search | Live-filter the app list |
| Tap an app row | Launch the app (scale-up animation) |
| Keyboard "Go" in Search | Launch the top result |
| Long-press an app row | App info / uninstall menu |
| Tap a notification | Open it |
| Swipe a notification (either way) | Dismiss it |
| Long-press the bottom pill | Request to be the default home app *(only while not default)* |

---

## 8. FAQ & troubleshooting

**The Notifications screen is empty / still shows the "tap to allow" prompt.**
You need to grant Notification access. Tap **タップして許可** and enable Tempo on the settings page,
then return to the app. If you granted it but the list is still empty right after a phone restart,
just reopen the Notifications screen — Tempo nudges the system to reconnect the listener.

**An app I have installed isn't in Search.**
Search lists apps that have a **launcher entry**. System components and apps without a launchable
activity won't appear. Tempo itself is also hidden by design. The list refreshes automatically when
apps are installed or removed.

**A notification won't go away when I swipe it.**
Ongoing notifications (media controls, calls, persistent service notices) are non-clearable by the
system, so Tempo doesn't display them at all. If a row reappears, the app that posted it re-posted
it.

**The bottom pill is orange/vermillion. Is something wrong?**
No — that just means Tempo isn't your default home app yet. Long-press the pill to set it. Once set,
the pill turns faint grey.

**How do I get back to my old launcher?**
Go to **Settings → Apps → Default apps → Home app** and pick a different launcher.

**Where are the settings?**
There aren't any, by design — the only preference is the theme toggle in the dock. Tempo's whole
premise is to stay out of your way.

---

## What Tempo deliberately leaves out

To preserve its calm, distraction-free character, Tempo has **no** widgets, app drawer pages,
folders, home-screen shortcuts, wallpaper picker, search-the-web bar, notification snoozing, or
in-app reply. It is a launcher for people who want their phone to be quiet.

---

*For build instructions and the tech-stack overview, see the [README](../README.md).*
