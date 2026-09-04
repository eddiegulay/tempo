# Search areas

## Assessment

The idea is sound, with three constraints the existing app already decided.

1. **A Material switch does not belong here.** Gym settings and the calendar composer already
   toggle with a word (On / Off, 入 / 切). The manage page uses that control, not a thumb switch.
2. **Email cannot be searched in Tempo.** There is no inbox provider we can read without new
   accounts, network, or Play-risky mail permissions. Email area means: an address-shaped query
   offers Compose and Search mail, and a name can hand the query to Gmail. Same shape as WhatsApp.
3. **Calendar can be searched in Tempo.** `READ_CALENDAR` is already held for 予定. Search matches
   the loaded fortnight (title, location, calendar name) and opens that event. No new permission.
4. **Long-press on the Search dock icon conflicts with the capsule long-press** that claims the
   default-home role (`Dock.kt`). The Search button must consume the long-press so it does not
   also fire "set as default home."

Apps stay an area (default on). Turning Apps off leaves hand-offs and calendar hits only. That is
allowed: Search is still the drawer, but the user asked to customize it.

Defaults: every area on. Missing DataStore keys mean on, so existing installs do not go silent.

## Outcome

Long-press Search in the dock opens a page of search areas. Each row is a name plus a word
toggle. Search then only offers the areas that are on. Email-shaped queries and calendar title
hits are new areas.

## Journey

```
Need to change what Search looks through
  -> Long-press Search in the dock
  -> See areas with word toggles
  -> Flip Email / Calendar / WhatsApp / ...
  -> Back or tap Search
  -> Type; only enabled areas appear
```

## WBS (one work package)

1. Search-area model + DataStore keys on `tempo_settings` (ThemeRepository owns the file)
2. Email-shaped hand-offs (mailto, mail app)
3. Calendar hits against the existing agenda
4. SearchAreas page (Filter-shaped header, gym-shaped word toggles)
5. Dock Search long-press (consumed on the button)
6. Tests for shape, area gating, calendar match, no new permissions

Out of scope: reading Gmail, call logs, contacts, notification-title search, Material Switch.
