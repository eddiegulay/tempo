# People and call-log search

Planning document. Ceremony: **large epic**, then **narrowed**.

## Decision (2026-09-04): permission-free hand-offs

Play-restricted and data-risky permissions are out. Tempo does **not** declare `READ_CONTACTS`,
`READ_CALL_LOG`, `CALL_PHONE`, or `INTERNET`. Search never reads the address book or the call log
and never stores people data.

What shipped on `feature/people-search` is the safer substitute: typed queries that look like a
number or a person-hunt offer inline hand-off rows (Call, Contacts, WhatsApp, Google). The original
provider-read WBS (1.1–1.2) stays out of scope unless a later decision re-opens it.

---

# People and call-log search (original plan)

Planning document. No implementation code. Ceremony: **large epic**.

Restated ask: a Tempo user who needs a person or a past call currently leaves Search (or leaves
Tempo) and hunts through the Phone app, Google, or WhatsApp. This epic makes Tempo Search the place
that finds those things and hands off the action.

GitHub: no existing issue covers contacts, call logs, or people search (`gh issue list --search`
and the full issue list). Closest shipped analog is Calendar (予定), which already reads a device
provider without OAuth or network.

---

## Why

Tempo is the home screen. Search is the drawer. Today Search only matches installed app labels and
package names. When the user needs *Mina*, or last Tuesday's missed call, or the WhatsApp thread
behind a number, Search cannot help. They open Google, they open WhatsApp, they open the dialer —
three exits from a launcher whose job is to stay still.

The pain is not "Tempo has no CRM." The pain is that the one field they already type into cannot
answer a people question.

---

## Primary outcome

From Tempo Search, a person can type a name or a phone number, see matching **device contacts** and
**recent calls** next to apps, and act (open the contact, place a call, open WhatsApp) without first
hunting for the Phone, Contacts, or WhatsApp apps.

One outcome. Google and WhatsApp are **entry habits to replace**, not a second product. Inbound
Google indexing and a WhatsApp share-target are follow-ups (see Out of scope).

---

## Customer journey

One primary journey. Stages this epic **owns** are marked.

```
Need a person or past call
  → Open Tempo Search                         [owns]
  → Grant people / calls access (first time)  [owns]
  → Type a name or number                     [owns]
  → See the person or the call                [owns]
  → Act (call / contact / WhatsApp)           [owns]
  → Land in the system app that owns the act  [must not break]
  → Return to Tempo Home                      [must not break]
```

**Upstream, must not break:** Home, dock, app search by label/package, 10-day blockade (including
notification suppression), calendar, notifications, gym library/exercise search, onboarding.

**Downstream, must not break:** launching Phone / Contacts / WhatsApp as ordinary apps; `tel:` and
contact `ACTION_VIEW` hand-off to whatever the user already uses.

Google-the-website and WhatsApp-the-app remain available. This work does not replace them. It stops
Search from being a dead end for people.

---

## User stories

| Id | Stage | Story |
| --- | --- | --- |
| **US-1** | Type → See | As a Tempo user, I want to type a person's name in Search and see that contact, so that I do not open Google or the Contacts app just to find them. |
| **US-2** | Type → See | As a Tempo user, I want to type a phone number (or part of one) and see matching contacts and recent calls, so that a number I remember is enough. |
| **US-3** | See | As a Tempo user, I want recent calls to appear when I search a name or number, so that I can get back to a past conversation without opening the dialer first. |
| **US-4** | Act | As a Tempo user, I want a tap on a contact or call to place a call or open the contact, so that Search finishes the job. |
| **US-5** | Act | As a Tempo user who has WhatsApp installed, I want a contact with a mobile number to offer WhatsApp, so that I do not leave Search, find WhatsApp, then search again inside it. |
| **US-6** | Grant | As a Tempo user, I want to keep using app search if I refuse contacts or call-log access, so that people search is optional and app search never regresses. |
| **US-7** | Act | As a Tempo user who hid WhatsApp or Phone behind the blockade, I want those actions to stay blocked, so that people search cannot undo a 10-day hide. |

---

## Functional requirements

| Id | Requirement | Stories |
| --- | --- | --- |
| **FR-1** | When contacts access is granted, Search matches device contacts by display name and by any stored phone number. | US-1, US-2 |
| **FR-2** | When call-log access is granted, Search matches recent calls by cached name and by number. | US-2, US-3 |
| **FR-3** | A blank query still lists apps as it does today. People and calls are not dumped as a second home screen. | US-1, US-6 |
| **FR-4** | A non-blank query may show **apps, people, and calls** in one list, grouped under section headings, each group only when it has matches. | US-1, US-2, US-3 |
| **FR-5** | Tapping a contact opens the system contact, or starts a call, according to the row's primary action. | US-4 |
| **FR-6** | Tapping a call-log row redials that number (or opens the matching contact when one exists). | US-3, US-4 |
| **FR-7** | When WhatsApp or WhatsApp Business is installed and not blockaded, a contact with a usable number offers an explicit WhatsApp action. Tempo does not read WhatsApp chats. | US-5 |
| **FR-8** | Contacts and call-log permissions are requested from Search, after the user asks, never at launch and never on the onboarding HOME / notification screen. | US-6 |
| **FR-9** | Refusing or revoking either permission leaves app search identical to today. Missing people results are a permission state, not an empty-app-list. | US-6 |
| **FR-10** | An action that would launch a blockaded app is refused the same way a Search row for that app is refused. The person or call row may still be visible. | US-7 |
| **FR-11** | Japanese contact names match after the same kana folding gym search already uses (ひらがな / カタカナ / full-width Latin). Romaji-to-kana is not required. | US-1 |
| **FR-12** | "Nothing found" is shown only when the query matches no apps, no (accessible) contacts, and no (accessible) calls. A grant-needed state is never worded as no results. | US-6 |

---

## Non-functional requirements

| Id | Category | Requirement |
| --- | --- | --- |
| **NFR-1** | Privacy | Tempo does not upload contacts or call logs. No `INTERNET` permission is added. No Google People API, no WhatsApp Cloud API. |
| **NFR-2** | Privacy | Contact and call-log data are not added to Auto Backup rules. The blockade ledger and `exercise.db` stay as they are. |
| **NFR-3** | Compat | Existing app-search behaviour (label/package substring, IME Go launches top **app** when the top hit is an app, long-press menu, work-profile apps, blockade filter) is unchanged for app rows. |
| **NFR-4** | Latency | Typing remains live. Filtering a typical address book (≤ a few thousand contacts) plus a bounded recent-call window stays on-device and does not freeze the field. |
| **NFR-5** | Security | Call log reads are limited to fields needed to identify and redial (number, cached name, type, date). No call recording, no full-history export UI. |
| **NFR-6** | UX / a11y | Keyboard-complete on Search. 48dp targets. Distinct copy for loading, no-match, no-permission, and provider fault. TalkBack heading on each result group. |
| **NFR-7** | Operability | Revoking permission in Settings while Tempo is alive is picked up on resume, same as calendar. |
| **NFR-8** | Policy | `READ_CALL_LOG` is treated as a restricted Play permission. Contacts can ship without it. Call-log rows do not ship on a Play build until the declaration is decided (see risks). GitHub APK may include it. |
| **NFR-9** | Design | People and call rows use Tempo paper chrome: `AppRow` geometry, Mincho name, Gothic subtitle, `Person` / `Phone` / `Message` glyphs, vermillion cursor. No Material search bar, no bottom sheet, no WhatsApp green, no platform avatars. |

---

## Acceptance criteria (epic)

Checkable without reading the diff. Cite FR/NFR. Epic does not close until **1.6.2** proves AC-5.

1. **AC-1** — With contacts granted, typing a known contact's name or number in Search shows that person in a People group. (FR-1, FR-4, FR-11)
2. **AC-2** — With call-log granted, typing a number that appears in recent calls shows that call in a Calls group. (FR-2, FR-4)
3. **AC-3** — With both permissions refused, Search still lists and launches apps exactly as today; a permission prompt is visible and is not worded as "Nothing found". (FR-8, FR-9, FR-12, NFR-3)
4. **AC-4** — Tapping a contact or call performs the documented action; a WhatsApp action is offered only when WhatsApp is installed and not blockaded. (FR-5, FR-6, FR-7, FR-10)
5. **AC-5** *(journey proof, owned by 1.6.2)* — One sitting: Home → Search → grant → type a name → see the person and a matching recent call → call or open WhatsApp → return via Home. App search, blockade, calendar, and gym search still work on the same build. (FR-1–FR-12, NFR-3, NFR-6)

---

## Assumptions and constraints

- Tempo remains an Android launcher (`minSdk 29`). No iOS, no web, no desktop.
- Data source is the device, the way Calendar uses `CalendarContract`: `ContactsContract` and
  `CallLog.Calls`. Google Contacts that have already synced to the device are visible. Google
  Contacts that live only in a browser are not.
- WhatsApp identity is a **phone number**, not a JID. Tempo never reads WhatsApp's database.
- Search stays a full page on the dock. No command palette, no modal, no web-search bar (USER_GUIDE
  already rejects that).
- Gym `foldKana` is reused or extracted, not rewritten beside a third folder.
- Japanese remains the default UI language; every new string ships JA + EN.
- Docs that still say Search "opens focused" are already stale (`SearchScreen.kt` opens unfocused).
  People search follows the code: unfocused until the field is tapped.

---

## Non-blast-radius risks

- **Play restricted permission.** `READ_CALL_LOG` is in Google Play's SMS/Call Log policy. A
  launcher that is not the default dialer can be rejected. Contacts-only is the safe first slice.
  Call logs are a separate leaf so a Play listing can omit them.
- **High-trust surface.** Tempo already has notification-listener access and is HOME. Adding
  contacts + call log increases the permission story. Onboarding copy must stay honest and late.
- **Blockade vs WhatsApp.** People search that can `wa.me` a number while WhatsApp is hidden for
  10 days would be a cheat. FR-10 is not optional.
- **Backup.** Putting a contact cache in files covered by `backup_rules.xml` would send an address
  book to the user's Google account. NFR-2 exists because that is easy to do by accident.
- **Notification text is not a contact graph.** Parsing WhatsApp notification titles as people
  would silently leak chat metadata into Search. Rejected.

---

## Survey findings

Four explore agents swept search, contacts/call logs, WhatsApp/Google/OS entry, and design.
Paths below were re-checked in this session.

### What is already there

**Decision: something close exists for the *pattern*; nothing exists for the *domain*.**

| Concept | Status | Where |
| --- | --- | --- |
| Launcher Search | Exists — apps only | `app/src/main/java/io/eddiegulay/tempo/ui/SearchScreen.kt` |
| App inventory | Exists | `data/AppRepository.kt` (`AppInfo`) |
| Search query state | Exists | `LauncherViewModel.searchQuery`, `onSearchQueryChange`, `visibleApps`, `goSearch` |
| Japanese text fold | Exists — gym only | `gym/LibraryFilters.kt` (`foldKana`, `matchRoutine`, `matchExercise`) |
| Device-provider + late permission | Exists — calendar | `calendar/CalendarRepository.kt`, `calendar/CalendarPermission.kt` |
| Permission empty / settings CTA | Exists | `ui/NotificationsScreen.kt` (`EnableAccessPrompt`), `ui/CalendarScreen.kt` (`AccessPrompt`) |
| Provider fault chrome | Exists | `ui/CalendarFeedback.kt` (`FaultPanel`, `FaultStrip`) |
| Person / Phone / Message glyphs | Exists — drawer cosmetics | `ui/AppGlyph.kt` (`KNOWN_PACKAGES` for Contacts, Dialer, WhatsApp) |
| WhatsApp as an app | Exists — launch / notify / blockade | `AppRepository.launch`, `notification/TempoNotificationListener.kt`, `BlockadeRepository.kt` |
| WhatsApp Cloud API / chat model | **Nothing** | searched `whatsapp`, `wa.me`, `jid` |
| Contacts / call logs | **Nothing** | searched `ContactsContract`, `CallLog`, `READ_CONTACTS`, `READ_CALL_LOG` — zero matches |
| Deep links / share target / AppSearch | **Nothing** | `AndroidManifest.xml` exports only MAIN + HOME + LAUNCHER |
| Google Sign-In / People API / INTERNET | **Nothing** | no `INTERNET` permission |

Launcher match (today):

```kotlin
apps.filter {
  it.label.contains(q, ignoreCase = true) ||
  it.packageName.contains(q, ignoreCase = true)
}
```

Empty query = full app list. IME Go launches the top filtered **app**. No ranking, no debounce.

### How the three habits work today

| Habit | What the user does now | What Tempo can already do | Gap |
| --- | --- | --- | --- |
| **Application search** | Type a name in 検索, hope a person appears, get only apps | Filter apps by label/package; launch Phone / Contacts / WhatsApp if the user knows those names | No people, no numbers, no calls |
| **Google search** | Leave Tempo, type the name in Google or the Google app | Google is just another launchable app (`googlequicksearchbox` glyph only) | Tempo is not searchable from Google; Tempo also cannot find the person, which is why they left |
| **WhatsApp** | Open WhatsApp, search the chat list, or hope a notification is still on 通知 | Launch WhatsApp; show/reply to its notifications; hide it for 10 days | No chat index, no number → thread, no share-in |

WhatsApp does not expose a public content provider for chats. Google Search will not list Tempo
contacts unless Tempo indexes them (AppSearch / App Actions) or becomes a web destination. Both
conflict with NFR-1 and with the documented "no widgets, no shortcuts, no search-the-web bar"
stance (`docs/USER_GUIDE.md`).

**Accommodation that fits Tempo:** make Search good enough that those two exits are unnecessary.
Hand off the last metre (`tel:`, `ContactsContract` VIEW, `https://wa.me/<digits>`) instead of
owning the conversation.

### What gets touched (existing) vs created

**Changed (risk lives here):**

| Surface | Why |
| --- | --- |
| `SearchScreen.kt` | Mixed result list, permission chrome, IME Go rule when top hit is not an app |
| `LauncherViewModel.kt` | New flows beside `visibleApps`; permission + launch actions |
| `AndroidManifest.xml` | `READ_CONTACTS`; optionally `READ_CALL_LOG`; `<queries>` for `tel:` / `vnd.android.cursor.item/contact` / WhatsApp view |
| `i18n/Strings.kt`, `StringsJa.kt`, `StringsEn.kt` | Search copy, permission, empty, fault |
| `docs/USER_GUIDE.md`, `README.md` | Search no longer "apps only" |
| `app/src/test/.../TrainingManifestTest.kt` (and any manifest permission tests) | New permission lines |
| Possibly `gym/LibraryFilters.kt` | Extract `foldKana` if people search shares it |

**Created:**

| Surface | Role |
| --- | --- |
| `contacts/` (or similar) repository + models + permission helper | Calendar-shaped provider read |
| Call-log repository (same package or sibling) | Bounded recents window |
| Phone normalizer | Shared by match + WhatsApp digits |
| Unified `SearchHit` (app / person / call) | One list, three kinds |
| Search section headings + person/call rows | UI, reusing `AppRow` geometry |

No new `Screen` enum value. No new dock tab. Filter (非表示), Home, Notifications, Calendar, Gym
are unchanged except as callers of shared chrome.

### Blast radius

| Surface | Consumers (approx.) | Silent-break risk |
| --- | --- | --- |
| `SearchScreen` filter block | 1 file, **0 tests** | Changing IME Go or empty-state can ship unnoticed |
| `LauncherViewModel` | `TempoApp`, `Dock`, `SearchScreen`, `FilterScreen`, notifications, calendar wiring | New StateFlows must not stall `visibleApps` |
| `AppRepository` | ViewModel, Filter, glyphs — **~4 production files** | Should stay app-only; do not overload `AppInfo` |
| `foldKana` | Library + exercise + picker + **~50** `KanaFoldingTest` + **~18** `LibraryFiltersTest` | A drive-by change breaks gym search |
| `BlockadeRepository` | Search visibility, notification listener cancel | WhatsApp/`tel:` actions that ignore blockade |
| Manifest permissions | `TrainingManifestTest` enumerates `uses-permission` | New lines fail the test until updated |
| Auto Backup XML | Blockade + gym DB | Accidental include of a people cache |
| Calendar permission UX | Calendar page + Home corner | Copying the helper is fine; sharing one "any dangerous permission" blob is not |

**Existing data in the wrong shape:** none. There are no contact rows to backfill.

**Call sites that stay out of this epic:** gym search UIs, notification store, calendar writer.

### Survey surprise

1. **Wrong product class for a naive reading of the ask.** Tempo is a distraction-blocking launcher
   plus 鍛錬, not a dialer. Contacts and call logs are greenfield. Calendar is the template, not
   Notifications (notifications are opaque title/body, not a people graph).
2. **Two search systems already.** Launcher `String.contains` vs gym `foldKana`. People names in
   Japanese will fail if we only extend the launcher matcher.
3. **Search is also Settings.** Theme, language, and hidden-apps live in the Search header. People
   chrome has to stay faint or the quietest screen becomes a toolbar.
4. **USER_GUIDE vs code.** Guide says Search opens focused; `SearchScreen.kt` deliberately opens
   unfocused. Guide still describes a three-icon dock.
5. **Reply exists.** USER_GUIDE says no in-app reply; `NotificationsScreen.kt` implements RemoteInput.
   Do not trust the guide as the source of truth for this epic.
6. **Zero deep links on purpose.** Workout notification tap (`TrainingService.kt`) refuses route
   extras. Adding `tempo://` for Google would be a new product stance, not a small leaf.
7. **WhatsApp blockade already cancels shade notifications.** A Search WhatsApp action that bypasses
   that is a real cheat, not a polish miss.

---

## Design aesthetics (how Search looks, and what people rows must inherit)

Tempo's identity is **washi paper, sumi ink, one vermillion accent, Mincho + Gothic**. Custom
Compose tokens, not Material ColorScheme, not a third-party design system.

| Token | File |
| --- | --- |
| Paper / Sumi colors | `ui/theme/TempoTheme.kt` |
| Mincho / Gothic roles | `ui/theme/Type.kt` |
| Radii | `ui/theme/TempoShapes.kt` (Row 14dp, Card 18dp, Glyph 16dp) |
| Press / focus wash | `ui/theme/InkPress.kt` |
| Paper grain + frosted dock | `ui/Background.kt` |

**Launcher Search today**

- Full dock page. No sheet, no modal (`EventComposeScreen` rejects Material bottom sheets).
- Faint heading 14sp / 6sp tracking; field **Mincho 26sp**, **1.5dp** hairline, vermillion cursor.
- Opens **unfocused**. Placeholder `検索` / `Search`.
- Results: hairline **rows** (not cards), 26dp monochrome `AppGlyph`, Mincho 18sp name, Gothic 11sp
  subtitle, 96dp bottom inset for the dock, `imePadding()`.
- Empty / loading share one centered Mincho 17sp line (`見つかりません` / `・・・`).
- IME Go launches top hit.

**Gym search (do not copy the field size onto launcher Search)**

- 18sp field, 1dp hairline, often collapsed behind `探す` / `とじる`, auto-focus on open.
- Section headings: Mincho 12sp, `inkFaint`, `heading()` semantics.
- Domain-specific no-match vs empty-library vs load fault — never conflated.

**Patterns to reuse for people / calls**

| Need | Steal from |
| --- | --- |
| Person / call row | `AppRow` in `SearchScreen.kt` |
| Glyphs | `AppGlyphs.Person`, `Phone`, `Message` |
| Permission gate | `AccessPrompt` / `EnableAccessPrompt` — centered soft title + accent Word CTA; re-check on resume |
| Provider failure | `FaultPanel` / `FaultStrip` |
| Group labels | Gym `PatternHeading` / notification `GroupHeader` |
| Missed-call hint | Filter's accent subtitle (sparing vermillion), not a red Material badge |

**Do not introduce:** colorful WhatsApp branding, circular photo avatars, Material `SearchBar`,
bottom sheets, a fourth dock destination, mixed cards-and-rows in one ungrouped list.

**Inconsistencies to not "fix" while we are here:** launcher 26sp vs gym 18sp; JA `検索` vs gym
`さがす`; header icons with no press wash. People search follows **launcher** Search, not gym.

---

## How this can be done (mechanism, not code)

Calendar already proved the shape:

1. Declare the provider permission in the manifest with a comment that says why.
2. Ask at the moment of use (`rememberCalendarPermissionState` + resume re-check).
3. Read the provider on a background dispatcher; expose `StateFlow`.
4. Hand off writes / ownership to the system app when Tempo should not be the system of record.

People search is the same, with **no writes**.

| Need | Provider / intent | Notes |
| --- | --- | --- |
| Names, numbers | `ContactsContract.CommonDataKinds.Phone` joined to `Contacts` | Lookup key for `ACTION_VIEW` |
| Recents | `CallLog.Calls` with a bounded window (e.g. last 90 days or last N rows) | `CACHED_NAME` is often enough to match a typed name |
| Normalize | Strip separators; keep a digits-only needle | `090-1234-5678` vs `+81 90…` vs WhatsApp's country-code form |
| Call | `ACTION_DIAL` (`tel:`) not `ACTION_CALL` | No `CALL_PHONE`; user confirms in the dialer |
| Open contact | `ContactsContract.Contacts.CONTENT_URI` + lookup | System Contacts UI |
| WhatsApp | `https://wa.me/<digits>` or WhatsApp `send` intent | Fail closed if package missing or blockaded |
| Japanese match | Existing `foldKana` | Do not add a second folder |

Blank query stays apps-only (FR-3). That keeps Search as a drawer and avoids turning it into a
recents feed — which would fight the product.

WhatsApp and Google as *destinations* are intents. WhatsApp and Google as *indexes* are out of
scope: there is no honest API for "search my WhatsApp" or "search Tempo from google.com" that stays
offline and private.

---

## WBS

Deliverables, not actions. Every leaf: size, predecessor, story/FR.

```
1. People and call-log results in Tempo Search

  1.1 Access
    1.1.1 Contacts and call-log permission contract (manifest + calendar-shaped helper)
          [S]  (none)
          enables FR-8, FR-9, NFR-7, NFR-8
    1.1.2 Search-page access prompt (grant / permanently-denied → Settings)
          [S]  Depends on 1.1.1
          US-6 / FR-8, FR-9, FR-12, NFR-6

  1.2 Directories
    1.2.1 Device contact directory (name, numbers, lookup key, live observer)
          [M]  Depends on 1.1.1
          US-1, US-2 / FR-1, NFR-1, NFR-2, NFR-4
    1.2.2 Bounded recent-call directory (number, cached name, type, date)
          [M]  Depends on 1.1.1
          US-2, US-3 / FR-2, NFR-5, NFR-8
    1.2.3 Shared phone-number normalizer (match + WhatsApp digits)
          [S]  (none)
          enables FR-1, FR-2, FR-7

  1.3 Search merge
    1.3.1 Unified search-hit model (app | person | call) without changing AppInfo
          [S]  Depends on 1.2.1, 1.2.2
          FR-4, NFR-3
    1.3.2 People/call matcher (name, digits, foldKana reuse)
          [M]  Depends on 1.2.3, 1.3.1
          US-1–US-3 / FR-1, FR-2, FR-11
    1.3.3 SearchScreen mixed list (section headings, AppRow-geometry person/call rows)
          [M]  Depends on 1.1.2, 1.3.2
          US-1–US-3 / FR-3, FR-4, FR-12, NFR-6, NFR-9

  1.4 Actions
    1.4.1 Contact primary action and long-press (view / dial)
          [S]  Depends on 1.3.3
          US-4 / FR-5
    1.4.2 Call-log primary action (redial / matching contact)
          [S]  Depends on 1.3.3, 1.2.2
          US-3, US-4 / FR-6
    1.4.3 WhatsApp hand-off gated on install + blockade
          [S]  Depends on 1.4.1, 1.2.3
          US-5, US-7 / FR-7, FR-10
    1.4.4 IME Go rule for mixed top hits (app vs person vs call)
          [S]  Depends on 1.3.3, 1.4.1
          NFR-3, FR-5

  1.5 Copy and docs
    1.5.1 JA/EN strings for prompt, groups, actions, no-match, fault
          [S]  Depends on 1.1.2
          FR-12, NFR-6
    1.5.2 USER_GUIDE + README Search section (apps and people; unfocused field)
          [S]  Depends on 1.3.3, 1.5.1
          AC docs

  1.6 Confidence
    1.6.1 Matcher and normalizer tests (name, JA fold, digit forms, empty, permission-off)
          [M]  Depends on 1.3.2, 1.2.3
          FR-1, FR-2, FR-11
    1.6.2 Journey proof on device (grant, refuse, revoke, blockade, WhatsApp absent)
          [M]  Depends on 1.4.2, 1.4.3, 1.4.4, 1.5.2, 1.6.1
          AC-5  ← integration leaf
```

### Definition of done (one line per leaf)

| Id | Done when |
| --- | --- |
| 1.1.1 | Manifest declares the permissions with rationale comments; a helper reports granted / denied / permanently-denied; resume re-check works. Call-log declaration can be compiled out or unused without breaking contacts. |
| 1.1.2 | Search shows a Tempo-styled prompt; app list remains usable underneath or below it; Settings path is explicit when the dialog will not return. |
| 1.2.1 | Contacts appear in a flow when granted; observer updates after a change in the system Contacts app; no backup of the cache. |
| 1.2.2 | A bounded recents list appears when granted; missed / incoming / outgoing are distinguishable in the model. |
| 1.2.3 | The same function makes `090-…`, `+81…`, and spaces compare equal for match and for `wa.me`. |
| 1.3.1 | `AppInfo` is unchanged; a hit type exists that Search can render. |
| 1.3.2 | Typed "山田" / "やまだ" / "ヤマダ" hit the same contact; digits hit number fields; gym tests still pass. |
| 1.3.3 | Non-blank query shows only groups that have hits; blank query is apps only; chrome matches NFR-9. |
| 1.4.1 | Tap opens contact or dialer as specified; no `CALL_PHONE`. |
| 1.4.2 | Tap redials via `ACTION_DIAL` or opens the linked contact. |
| 1.4.3 | WhatsApp action hidden when package missing or blockaded; shown otherwise. |
| 1.4.4 | Go still launches the top **app** when that is the top hit; documented behaviour when a person is on top. |
| 1.5.1 | Every new user-visible string has JA + EN; no-permission ≠ no-match. |
| 1.5.2 | Guide describes people/call search and the real unfocused field. |
| 1.6.1 | JVM tests cover FR-1/2/11 edges without a device. |
| 1.6.2 | AC-5 walked on a device; refuse and revoke paths recorded. |

### How predecessors advance the journey

`1.1` + `1.2` unlock **Grant**. `1.3` unlocks **Type → See**. `1.4` unlocks **Act**. `1.6.2` is the
sitting that proves the whole path. Docs and tests do not gate the first user-visible See, but
`1.6.2` gates epic close.

---

## Dependency edges

```
1.1.2  <- 1.1.1
1.2.1  <- 1.1.1
1.2.2  <- 1.1.1
1.3.1  <- 1.2.1, 1.2.2
1.3.2  <- 1.2.3, 1.3.1
1.3.3  <- 1.1.2, 1.3.2
1.4.1  <- 1.3.3
1.4.2  <- 1.3.3, 1.2.2
1.4.3  <- 1.4.1, 1.2.3
1.4.4  <- 1.3.3, 1.4.1
1.5.1  <- 1.1.2
1.5.2  <- 1.3.3, 1.5.1
1.6.1  <- 1.3.2, 1.2.3
1.6.2  <- 1.4.2, 1.4.3, 1.4.4, 1.5.2, 1.6.1

Roots: 1.1.1, 1.2.3
```

---

## First shippable slice

**1.1.1, 1.1.2, 1.2.1, 1.2.3, 1.3.1–1.3.3, 1.4.1, 1.4.4, 1.5.1, 1.6.1**

Earliest journey win: type a name or number, see the **contact**, open or dial it. Call-log rows
(1.2.2, 1.4.2) and WhatsApp (1.4.3) can land in the same epic but are not required for the first
See. This also keeps NFR-8 from blocking the first slice.

---

## Integration branch

`feature/people-search`

Leaf git work: `wbs/<id>-<slug>` into that branch. `develop` gets one PR when the epic lands.
Issue numbers do not exist yet — branch name stays unnumbered until an epic issue is opened.

---

## Out of scope

Linked follow-ups if this plan is shipped as issues; **not** parented to the epic.

| Follow-up | Why it is out |
| --- | --- |
| Google App Actions / AppSearch / Assistant shortcuts | Indexes people into OS/Google search; conflicts with NFR-1 and USER_GUIDE "no shortcuts / no search-the-web bar". Making *Tempo* Search work is what retires the Google habit. |
| `tempo://` or https App Links | Manifest and `MainActivity` deliberately have none. |
| Incoming share target from WhatsApp (vCard / text) | Useful later; not needed once Search finds the number. |
| WhatsApp Cloud API, chat history, Business webhooks | Requires `INTERNET` and a server. Tempo has neither. |
| In-app SMS / call recording / default-dialer role | Different product. |
| Favorites, starred contacts, a Recents home widget | Blank query stays apps-only (FR-3). |
| Writing or merging contacts | Calendar writes events; people search is read + hand-off only. |
| Gym / notification / calendar text search | Separate surfaces; gym already has its own matcher. |
| Romaji → カナ contact search | foldKana does not do this today; do not invent it here. |
| iOS Spotlight / Siri | Android-only repo. |
| Material / web redesign of Search | NFR-9. |

---

## WBS checklist (Gate D)

- [x] Journey, stories, FR, NFR exist.
- [x] Code opened; four agents + file re-check. Nothing planned from memory.
- [x] Contacts/call logs do not already exist; Calendar + Search + foldKana are extended, not cloned as a second people product.
- [x] Every named consumer has a node or an Out-of-scope line.
- [x] Changed vs new surfaces split (1.3.3 / ViewModel / manifest vs 1.2.*).
- [x] Children sum to parent; no overlapping siblings.
- [x] Deliverable names; no phase/journey-stage node titles.
- [x] Every leaf has id, `Depends on` / `(none)`, S/M/L, one-line done, FR trace.
- [x] Edges match predecessors.
- [x] First slice named; 1.6.2 is the integration leaf.
- [x] Survey surprise recorded.
- [x] No L leaves (no split proposal needed).
- [x] Interim work present: permission, tests, docs, backup exclusion, blockade gate.

---

## Research method

| Agent | Question |
| --- | --- |
| 1 | How application search works (surfaces, pipeline, gaps) |
| 2 | Contacts and call-log data model (none; app inventory + calendar analog) |
| 3 | WhatsApp, Google, OS search, deep links, privacy |
| 4 | Design tokens and Search look-and-feel |

Uncertainties left for a human (not inventable from the repo):

1. **Confirm the outcome.** This plan treats Google and WhatsApp as habits to retire by making
   Search complete — not as surfaces Tempo should index into. If the real ask is "Tempo results
   inside the Google app" or "search WhatsApp history from Tempo," that is a different epic and it
   fights the product.
2. **Call logs on Play.** Ship contacts-only on any Play listing until `READ_CALL_LOG` is declared
   or declined?
3. **IME Go** when the top hit is a person: launch that person (consistent with today's "top hit")
   or still prefer an app? 1.4.4 exists because this is a product call.
4. **Open GitHub issues** from this WBS? Not done. This file is the plan.

---

## Ceremony

**Large epic** — more than eight leaves, new provider permissions, Play-policy risk, design +
launcher + docs. Issues were **not** opened. Approval of this plan (and of the four uncertainties)
comes first.
