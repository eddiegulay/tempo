package io.eddiegulay.tempo.ui

import android.app.ActivityOptions
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.eddiegulay.tempo.LauncherViewModel
import io.eddiegulay.tempo.data.AppInfo
import io.eddiegulay.tempo.i18n.LocalStrings
import io.eddiegulay.tempo.i18n.SearchStrings
import io.eddiegulay.tempo.calendar.CalendarEvent
import io.eddiegulay.tempo.calendar.displayTitle
import io.eddiegulay.tempo.contacts.DeviceContact
import io.eddiegulay.tempo.contacts.matchContacts
import io.eddiegulay.tempo.contacts.rememberContactsPermissionState
import io.eddiegulay.tempo.search.AppMatch
import io.eddiegulay.tempo.search.HandOffKind
import io.eddiegulay.tempo.search.SpotifyHit
import io.eddiegulay.tempo.search.appCategoryLabel
import io.eddiegulay.tempo.search.handOffsAboveApps
import io.eddiegulay.tempo.search.isNumberShaped
import io.eddiegulay.tempo.search.matchAppFields
import io.eddiegulay.tempo.search.matchCalendarFields
import io.eddiegulay.tempo.search.shouldOfferSpotify
import io.eddiegulay.tempo.search.visibleHandOffs
import io.eddiegulay.tempo.ui.theme.Gothic
import io.eddiegulay.tempo.ui.theme.LocalTempoColors
import io.eddiegulay.tempo.ui.theme.Mincho
import io.eddiegulay.tempo.ui.theme.TempoShapes
import io.eddiegulay.tempo.ui.theme.combinedPressable
import io.eddiegulay.tempo.ui.theme.pressable
import java.time.Instant
import java.time.ZoneId

/**
 * Search (検索): a bottom-ruled mincho input over a live-filtered list of every installed app,
 * plus address-book hits (Call, Message, WhatsApp), a few Spotify titles, and hand-offs when
 * the query looks like a person or a number.
 *
 * The inventory is the shared, live [LauncherViewModel] flow; icons load lazily per visible row from
 * the repository's cache. Tapping launches with a scale-up animation from the row; long-press opens
 * a minimal menu (app info / uninstall).
 */
@Composable
fun SearchScreen(
    viewModel: LauncherViewModel,
    isDark: Boolean,
    onToggleTheme: () -> Unit,
    onOpenFilter: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = LocalTempoColors.current
    val s = LocalStrings.current
    val context = LocalContext.current

    val query by viewModel.searchQuery.collectAsStateWithLifecycle()
    val lang by viewModel.lang.collectAsStateWithLifecycle()

    // Local, not hoisted: the picker is a transient sheet over this page and nothing outside Search
    // needs to know it is open. Same treatment the app-info menu below already gets.
    var showLanguage by remember { mutableStateOf(false) }
    val apps by viewModel.visibleApps.collectAsStateWithLifecycle()
    val inventory by viewModel.apps.collectAsStateWithLifecycle()
    val blockade by viewModel.blockade.collectAsStateWithLifecycle()
    val areas by viewModel.searchAreas.collectAsStateWithLifecycle()
    val events by viewModel.calendarEvents.collectAsStateWithLifecycle()
    val contactsGranted by viewModel.contactsAccess.collectAsStateWithLifecycle()
    val deviceContacts by viewModel.deviceContacts.collectAsStateWithLifecycle()
    val contactsPermission = rememberContactsPermissionState(
        granted = contactsGranted,
        onGrantedChange = viewModel::setContactsAccess,
    )

    LaunchedEffect(Unit) { viewModel.ensureAppsLoaded() }

    val filtered = remember(query, apps, areas) {
        if (!areas.apps) emptyList()
        else {
            val q = query.trim()
            if (q.isEmpty()) apps
            else apps.filter { matchAppFields(it.label, it.packageName, q) }
        }
    }
    val availability = remember(inventory, blockade) { viewModel.handOffAvailability() }
    val matches = remember(filtered) { filtered.map { AppMatch(it.label, it.packageName) } }
    val contactHits = remember(query, deviceContacts, areas, contactsGranted) {
        if (!areas.contacts || !contactsGranted) emptyList()
        else matchContacts(query, deviceContacts)
    }
    val handOffs = remember(query, matches, availability, areas, contactHits) {
        visibleHandOffs(query, matches, availability, areas, hasContactHits = contactHits.isNotEmpty())
    }
    val calendarHits = remember(query, events, areas) {
        if (!areas.calendar || query.trim().length < 2) emptyList()
        else events.filter { matchCalendarFields(it.title, it.location, it.calendarName, query) }
    }
    val spotifyHits by viewModel.spotifyHits.collectAsStateWithLifecycle()
    val showSpotify = remember(query, areas, matches) { shouldOfferSpotify(query, areas, matches) }
    val showContactsAllow = areas.contacts && !contactsGranted && query.trim().length >= 2
    val noResults = query.isNotBlank() &&
        filtered.isEmpty() &&
        handOffs.isEmpty() &&
        calendarHits.isEmpty() &&
        contactHits.isEmpty() &&
        !showContactsAllow &&
        !showSpotify
    val loading = areas.apps && apps.isEmpty() && query.isBlank()
    val peopleFirst = (handOffs.isNotEmpty() && handOffsAboveApps(query)) ||
        (contactHits.isNotEmpty() && isNumberShaped(query)) ||
        (showContactsAllow && isNumberShaped(query))

    // Search doubles as the app drawer, so it opens unfocused. Submitting launches the top app, or
    // the first hand-off when the query matched no app, or the first agenda hit.
    val keyboard = LocalSoftwareKeyboardController.current
    val launchTop: () -> Unit = {
        val topApp = filtered.firstOrNull()
        val topContact = contactHits.firstOrNull()
        val topHandOff = handOffs.firstOrNull()
        val topEvent = calendarHits.firstOrNull()
        val topSpotify = spotifyHits.firstOrNull()
        when {
            topApp != null -> {
                keyboard?.hide()
                viewModel.launchApp(context, topApp)
            }
            topContact != null -> {
                keyboard?.hide()
                viewModel.launchContactCall(context, topContact.phone)
            }
            topHandOff != null -> {
                keyboard?.hide()
                viewModel.launchHandOff(context, topHandOff, query)
            }
            topEvent != null -> {
                keyboard?.hide()
                viewModel.openInCalendarApp(context, topEvent)
            }
            topSpotify != null -> {
                keyboard?.hide()
                viewModel.launchSpotifyTrack(context, topSpotify)
            }
            showSpotify -> {
                keyboard?.hide()
                viewModel.launchSpotifySearch(context, query)
            }
            else -> keyboard?.hide()
        }
    }

    Column(modifier.fillMaxSize()) {
        Column(Modifier.padding(start = 26.dp, end = 26.dp, top = 20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = s.search.heading,
                    style = TextStyle(fontFamily = Mincho, fontSize = 14.sp, letterSpacing = 6.sp, color = c.inkFaint),
                )
                // Trailing controls: hidden-apps filter page, then the theme toggle (relocated from
                // the dock). Both stay faint, mirroring the prototype's quiet chrome.
                Row(verticalAlignment = Alignment.CenterVertically) {
                    HeaderIconButton(
                        paths = TempoIcons.EyeOff,
                        contentDescription = s.search.hiddenApps,
                        onClick = onOpenFilter,
                    )
                    HeaderIconButton(
                        paths = if (isDark) TempoIcons.Sun else TempoIcons.Moon,
                        contentDescription = if (isDark) s.search.toLightTheme else s.search.toDarkTheme,
                        onClick = onToggleTheme,
                    )
                    HeaderIconButton(
                        paths = TempoIcons.Globe,
                        contentDescription = s.search.language,
                        onClick = { showLanguage = true },
                    )
                }
            }
            Box(Modifier.height(14.dp))
            BasicTextField(
                value = query,
                onValueChange = viewModel::onSearchQueryChange,
                singleLine = true,
                textStyle = TextStyle(fontFamily = Mincho, fontSize = 26.sp, color = c.ink),
                cursorBrush = SolidColor(c.accent),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = { launchTop() }),
                modifier = Modifier.fillMaxWidth(),
                decorationBox = { inner ->
                    Column {
                        Box(Modifier.padding(vertical = 8.dp, horizontal = 2.dp)) {
                            if (query.isEmpty()) {
                                Text(
                                    text = s.search.placeholder,
                                    style = TextStyle(fontFamily = Mincho, fontSize = 26.sp, color = c.inkFaint),
                                )
                            }
                            inner()
                        }
                        Box(Modifier.fillMaxWidth().height(1.5.dp).background(c.hair))
                    }
                },
            )
        }

        LazyColumn(
            modifier = Modifier.fillMaxWidth().weight(1f).imePadding(),
            // Bottom inset clears the floating dock pill (capsule + indicator) so the last row is reachable.
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 18.dp, bottom = 96.dp),
        ) {
            if (loading || noResults) {
                item {
                    Box(Modifier.fillMaxWidth().padding(top = 70.dp), contentAlignment = Alignment.Center) {
                        Text(
                            text = if (loading) s.search.loading else s.search.empty,
                            style = TextStyle(fontFamily = Mincho, fontSize = 17.sp, letterSpacing = 4.sp, color = c.inkFaint),
                        )
                    }
                }
            }
            if (peopleFirst) {
                peopleBlock(
                    contactHits = contactHits,
                    showAllow = showContactsAllow,
                    allowLabel = s.search.contactsAllow,
                    allowHint = s.search.contactsAllowHint,
                    section = s.search.peopleSection,
                    callLabel = s.search.handOffCall,
                    messageLabel = s.search.contactMessage,
                    whatsAppLabel = s.search.handOffWhatsApp,
                    showWhatsApp = areas.whatsApp && availability.whatsAppPackage != null,
                    afterApps = false,
                    onAllow = contactsPermission.request,
                    onCall = { viewModel.launchContactCall(context, it) },
                    onMessage = { viewModel.launchContactMessage(context, it) },
                    onWhatsApp = { viewModel.launchContactWhatsApp(context, it) },
                )
                if (handOffs.isNotEmpty()) {
                    handOffBlock(
                        kinds = handOffs,
                        query = query,
                        afterApps = contactHits.isNotEmpty() || showContactsAllow,
                        strings = s.search,
                        onOpen = { kind -> viewModel.launchHandOff(context, kind, query) },
                    )
                }
            }
            items(filtered, key = { it.key }) { app ->
                AppRow(viewModel = viewModel, app = app)
            }
            if (!peopleFirst) {
                peopleBlock(
                    contactHits = contactHits,
                    showAllow = showContactsAllow,
                    allowLabel = s.search.contactsAllow,
                    allowHint = s.search.contactsAllowHint,
                    section = s.search.peopleSection,
                    callLabel = s.search.handOffCall,
                    messageLabel = s.search.contactMessage,
                    whatsAppLabel = s.search.handOffWhatsApp,
                    showWhatsApp = areas.whatsApp && availability.whatsAppPackage != null,
                    afterApps = filtered.isNotEmpty(),
                    onAllow = contactsPermission.request,
                    onCall = { viewModel.launchContactCall(context, it) },
                    onMessage = { viewModel.launchContactMessage(context, it) },
                    onWhatsApp = { viewModel.launchContactWhatsApp(context, it) },
                )
                if (handOffs.isNotEmpty()) {
                    handOffBlock(
                        kinds = handOffs,
                        query = query,
                        afterApps = filtered.isNotEmpty() || contactHits.isNotEmpty() || showContactsAllow,
                        strings = s.search,
                        onOpen = { kind -> viewModel.launchHandOff(context, kind, query) },
                    )
                }
            }
            if (calendarHits.isNotEmpty()) {
                item(key = "handoff:calendar-heading") {
                    Text(
                        text = s.search.handOffCalendarSection,
                        style = TextStyle(fontFamily = Mincho, fontSize = 12.sp, letterSpacing = 3.sp, color = c.inkFaint),
                        modifier = Modifier
                            .padding(
                                start = 12.dp,
                                end = 12.dp,
                                top = if (filtered.isNotEmpty() || handOffs.isNotEmpty() || contactHits.isNotEmpty() || showContactsAllow) 24.dp else 0.dp,
                                bottom = 6.dp,
                            )
                            .semantics { heading() },
                    )
                }
                items(calendarHits, key = { "cal:${it.key}" }) { event ->
                    CalendarHitRow(
                        event = event,
                        onClick = { viewModel.openInCalendarApp(context, event) },
                    )
                }
            }
            if (showSpotify) {
                spotifyBlock(
                    hits = spotifyHits,
                    afterOthers = filtered.isNotEmpty() ||
                        handOffs.isNotEmpty() ||
                        calendarHits.isNotEmpty() ||
                        contactHits.isNotEmpty() ||
                        showContactsAllow,
                    section = s.search.spotifySection,
                    openLabel = s.search.spotifyOpen,
                    onHit = { viewModel.launchSpotifyTrack(context, it) },
                    onOpen = { viewModel.launchSpotifySearch(context, query) },
                )
            }
        }
    }

    // Choosing dismisses: the picker has done its job the moment a language is chosen, and the whole
    // screen behind it is already redrawing in the new language, so leaving the sheet up would make
    // the user close a dialog whose question has visibly been answered.
    if (showLanguage) {
        LanguageDialog(
            current = lang,
            onChoose = {
                viewModel.setLanguage(it)
                showLanguage = false
            },
            onDismiss = { showLanguage = false },
        )
    }
}

@Composable
private fun AppRow(viewModel: LauncherViewModel, app: AppInfo) {
    val c = LocalTempoColors.current
    val s = LocalStrings.current
    val context = LocalContext.current
    val rootView = LocalView.current

    var menuOpen by remember { mutableStateOf(false) }
    var rowBounds by remember { mutableStateOf<android.graphics.Rect?>(null) }

    Box {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .onGloballyPositioned { coords ->
                    val r = coords.boundsInWindow()
                    rowBounds = android.graphics.Rect(r.left.toInt(), r.top.toInt(), r.right.toInt(), r.bottom.toInt())
                }
                // The launch animation scales the new app's window up out of *these* bounds, so the
                // press wash and the window it hands off to are the same rounded rectangle.
                .combinedPressable(
                    shape = TempoShapes.Row,
                    role = Role.Button,
                    onClickLabel = s.search.launch,
                    onLongClickLabel = s.search.menu,
                    onLongClick = { menuOpen = true },
                    onClick = {
                        val b = rowBounds
                        val opts = if (b != null) {
                            ActivityOptions.makeScaleUpAnimation(rootView, b.left, b.top, b.width(), b.height()).toBundle()
                        } else null
                        viewModel.launchApp(context, app, b, opts)
                    },
                )
                .padding(horizontal = 12.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            AppGlyph(app = app, color = c.inkSoft, size = 26.dp)
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = app.label,
                    style = TextStyle(fontFamily = Mincho, fontSize = 18.sp, letterSpacing = 1.sp, color = c.ink),
                )
                // Subtitle: app category and last-updated date (e.g. "生産性 · 更新 6月10日"), each
                // dropped when unavailable. Replaces the developer-facing package name.
                val subtitle = remember(app.categoryId, app.lastUpdated, s) {
                    val date = app.lastUpdated.takeIf { it > 0L }?.let {
                        s.search.updatedPrefix +
                            s.fmt.monthDay(Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDateTime())
                    }
                    listOfNotNull(appCategoryLabel(app.categoryId, s.search), date).joinToString(" · ")
                }
                if (subtitle.isNotEmpty()) {
                    Text(
                        text = subtitle,
                        style = TextStyle(fontFamily = Gothic, fontSize = 11.sp, letterSpacing = 2.sp, color = c.inkFaint),
                    )
                }
            }
        }

        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            DropdownMenuItem(
                text = { Text(s.search.appInfo, style = TextStyle(fontFamily = Mincho, color = c.ink)) },
                onClick = {
                    menuOpen = false
                    viewModel.openAppInfo(context, app)
                },
            )
            DropdownMenuItem(
                text = { Text(s.search.hideApp, style = TextStyle(fontFamily = Mincho, color = c.ink)) },
                onClick = {
                    menuOpen = false
                    viewModel.requestBlock(app)
                },
            )
            DropdownMenuItem(
                text = { Text(s.search.uninstall, style = TextStyle(fontFamily = Mincho, color = c.ink)) },
                onClick = {
                    menuOpen = false
                    viewModel.requestUninstall(context, app)
                },
            )
        }
    }
}

private fun LazyListScope.peopleBlock(
    contactHits: List<DeviceContact>,
    showAllow: Boolean,
    allowLabel: String,
    allowHint: String,
    section: String,
    callLabel: String,
    messageLabel: String,
    whatsAppLabel: String,
    showWhatsApp: Boolean,
    afterApps: Boolean,
    onAllow: () -> Unit,
    onCall: (String) -> Unit,
    onMessage: (String) -> Unit,
    onWhatsApp: (String) -> Unit,
) {
    if (contactHits.isEmpty() && !showAllow) return
    item(key = "people:heading") {
        Text(
            text = section,
            style = TextStyle(fontFamily = Mincho, fontSize = 12.sp, letterSpacing = 3.sp, color = LocalTempoColors.current.inkFaint),
            modifier = Modifier
                .padding(start = 12.dp, end = 12.dp, top = if (afterApps) 24.dp else 0.dp, bottom = 6.dp)
                .semantics { heading() },
        )
    }
    if (showAllow) {
        item(key = "people:allow") {
            ContactsAllowRow(label = allowLabel, hint = allowHint, onClick = onAllow)
        }
    }
    items(contactHits, key = { "contact:${it.contactId}" }) { contact ->
        ContactHitRow(
            contact = contact,
            callLabel = callLabel,
            messageLabel = messageLabel,
            whatsAppLabel = whatsAppLabel,
            showWhatsApp = showWhatsApp,
            onCall = onCall,
            onMessage = onMessage,
            onWhatsApp = onWhatsApp,
        )
    }
}

private fun LazyListScope.handOffBlock(
    kinds: List<HandOffKind>,
    query: String,
    afterApps: Boolean,
    strings: SearchStrings,
    onOpen: (HandOffKind) -> Unit,
) {
    item(key = "handoff:heading") {
        Text(
            text = strings.handOffSection,
            style = TextStyle(fontFamily = Mincho, fontSize = 12.sp, letterSpacing = 3.sp, color = LocalTempoColors.current.inkFaint),
            modifier = Modifier
                .padding(start = 12.dp, end = 12.dp, top = if (afterApps) 24.dp else 0.dp, bottom = 6.dp)
                .semantics { heading() },
        )
    }
    items(kinds, key = { "handoff:${it.name}" }) { kind ->
        HandOffRow(kind = kind, query = query, strings = strings, onClick = { onOpen(kind) })
    }
}

@Composable
private fun HandOffRow(
    kind: HandOffKind,
    query: String,
    strings: SearchStrings,
    onClick: () -> Unit,
) {
    val c = LocalTempoColors.current
    val copy = handOffCopy(kind, query, strings)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .pressable(
                shape = TempoShapes.Row,
                role = Role.Button,
                onClickLabel = copy.primary,
                onClick = onClick,
            )
            .padding(horizontal = 12.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        LineIcon(paths = copy.glyph, color = c.inkSoft, size = 26.dp)
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = copy.primary,
                style = TextStyle(fontFamily = Mincho, fontSize = 18.sp, letterSpacing = 1.sp, color = c.ink),
            )
            Text(
                text = copy.subtitle,
                style = TextStyle(fontFamily = Gothic, fontSize = 11.sp, letterSpacing = 2.sp, color = c.inkFaint),
            )
        }
    }
}

private data class HandOffCopy(
    val glyph: List<String>,
    val primary: String,
    val subtitle: String,
)

private fun handOffCopy(kind: HandOffKind, query: String, strings: SearchStrings): HandOffCopy {
    val q = query.trim()
    return when (kind) {
        HandOffKind.Call -> HandOffCopy(AppGlyphs.Phone, strings.handOffCall, q)
        HandOffKind.WhatsAppNumber -> HandOffCopy(AppGlyphs.Message, strings.handOffWhatsApp, strings.handOffWhatsAppNumberSubtitle)
        HandOffKind.FindContacts -> HandOffCopy(AppGlyphs.Person, strings.handOffFindContacts, q)
        HandOffKind.SearchContacts -> HandOffCopy(AppGlyphs.Person, strings.handOffSearchContacts, q)
        HandOffKind.SearchWhatsApp -> HandOffCopy(AppGlyphs.Message, strings.handOffSearchWhatsApp, q)
        HandOffKind.SearchGoogle -> HandOffCopy(AppGlyphs.Globe, strings.handOffSearchGoogle, q)
        HandOffKind.ComposeEmail -> HandOffCopy(AppGlyphs.Mail, strings.handOffComposeEmail, q)
        HandOffKind.SearchMail -> HandOffCopy(AppGlyphs.Mail, strings.handOffSearchMail, q)
    }
}

@Composable
private fun ContactsAllowRow(label: String, hint: String, onClick: () -> Unit) {
    val c = LocalTempoColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .pressable(
                shape = TempoShapes.Row,
                role = Role.Button,
                onClickLabel = label,
                onClick = onClick,
            )
            .padding(horizontal = 12.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        LineIcon(paths = AppGlyphs.Person, color = c.inkSoft, size = 26.dp)
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = label,
                style = TextStyle(fontFamily = Mincho, fontSize = 18.sp, letterSpacing = 1.sp, color = c.ink),
            )
            Text(
                text = hint,
                style = TextStyle(fontFamily = Gothic, fontSize = 11.sp, letterSpacing = 2.sp, color = c.inkFaint),
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ContactHitRow(
    contact: DeviceContact,
    callLabel: String,
    messageLabel: String,
    whatsAppLabel: String,
    showWhatsApp: Boolean,
    onCall: (String) -> Unit,
    onMessage: (String) -> Unit,
    onWhatsApp: (String) -> Unit,
) {
    val c = LocalTempoColors.current
    val first = contact.phone
    Column(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .pressable(
                    shape = TempoShapes.Row,
                    role = Role.Button,
                    onClickLabel = callLabel,
                    onClick = { if (first.isNotEmpty()) onCall(first) },
                )
                .padding(horizontal = 12.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            LineIcon(paths = AppGlyphs.Person, color = c.inkSoft, size = 26.dp)
            Text(
                text = contact.displayName,
                style = TextStyle(fontFamily = Mincho, fontSize = 18.sp, letterSpacing = 1.sp, color = c.ink),
            )
        }
        contact.phones.forEach { number ->
            Column(Modifier.fillMaxWidth()) {
                Text(
                    text = number,
                    modifier = Modifier.padding(start = 56.dp, end = 12.dp, top = 2.dp),
                    style = TextStyle(fontFamily = Gothic, fontSize = 11.sp, letterSpacing = 2.sp, color = c.inkFaint),
                )
                FlowRow(
                    modifier = Modifier.padding(start = 56.dp, end = 12.dp, bottom = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(20.dp),
                ) {
                    ContactActionChip(label = callLabel, onClick = { onCall(number) })
                    ContactActionChip(label = messageLabel, onClick = { onMessage(number) })
                    if (showWhatsApp) {
                        ContactActionChip(label = whatsAppLabel, onClick = { onWhatsApp(number) })
                    }
                }
            }
        }
    }
}

@Composable
private fun ContactActionChip(label: String, onClick: () -> Unit) {
    val c = LocalTempoColors.current
    Box(
        modifier = Modifier
            .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
            .pressable(TempoShapes.Word, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(
            text = label,
            style = TextStyle(fontFamily = Mincho, fontSize = 13.sp, letterSpacing = 1.sp, color = c.accent),
        )
    }
}

private fun LazyListScope.spotifyBlock(
    hits: List<SpotifyHit>,
    afterOthers: Boolean,
    section: String,
    openLabel: String,
    onHit: (SpotifyHit) -> Unit,
    onOpen: () -> Unit,
) {
    item(key = "spotify:heading") {
        Text(
            text = section,
            style = TextStyle(fontFamily = Mincho, fontSize = 12.sp, letterSpacing = 3.sp, color = LocalTempoColors.current.inkFaint),
            modifier = Modifier
                .padding(start = 12.dp, end = 12.dp, top = if (afterOthers) 24.dp else 0.dp, bottom = 6.dp)
                .semantics { heading() },
        )
    }
    items(hits, key = { "spotify:${it.title}\u0000${it.artist}" }) { hit ->
        SpotifyHitRow(hit = hit, onClick = { onHit(hit) })
    }
    item(key = "spotify:open") {
        Box(Modifier.padding(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 8.dp)) {
            ContactActionChip(label = openLabel, onClick = onOpen)
        }
    }
}

@Composable
private fun SpotifyHitRow(hit: SpotifyHit, onClick: () -> Unit) {
    val c = LocalTempoColors.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .pressable(
                shape = TempoShapes.Row,
                role = Role.Button,
                onClickLabel = hit.title,
                onClick = onClick,
            )
            .padding(horizontal = 12.dp, vertical = 13.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = hit.title,
            style = TextStyle(fontFamily = Mincho, fontSize = 18.sp, letterSpacing = 1.sp, color = c.ink),
        )
        Text(
            text = hit.artist,
            style = TextStyle(fontFamily = Gothic, fontSize = 11.sp, letterSpacing = 2.sp, color = c.inkFaint),
        )
    }
}

@Composable
private fun CalendarHitRow(event: CalendarEvent, onClick: () -> Unit) {
    val c = LocalTempoColors.current
    val s = LocalStrings.current
    val title = event.displayTitle(s)
    val whenText = s.fmt.monthDay(event.startDateTime())
    val subtitle = listOfNotNull(whenText, event.location?.takeIf { it.isNotBlank() }).joinToString(" · ")
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .pressable(
                shape = TempoShapes.Row,
                role = Role.Button,
                onClickLabel = title,
                onClick = onClick,
            )
            .padding(horizontal = 12.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        LineIcon(paths = AppGlyphs.Calendar, color = c.inkSoft, size = 26.dp)
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = title,
                style = TextStyle(fontFamily = Mincho, fontSize = 18.sp, letterSpacing = 1.sp, color = c.ink),
            )
            if (subtitle.isNotEmpty()) {
                Text(
                    text = subtitle,
                    style = TextStyle(fontFamily = Gothic, fontSize = 11.sp, letterSpacing = 2.sp, color = c.inkFaint),
                )
            }
        }
    }
}

/**
 * A faint, 48dp-target line-icon button used in the Search header (filter + theme toggle).
 *
 * **No indication, deliberately** — and it is the one control in this file that keeps that. These
 * three glyphs are page chrome sitting beside a 14sp heading; a wash under each would put three grey
 * tiles across the top of the quietest screen in the app. The result of every one of them is instant
 * and unmistakable (the page turns, the theme flips, a picker opens), which is the feedback.
 */
@Composable
internal fun HeaderIconButton(
    paths: List<String>,
    contentDescription: String,
    onClick: () -> Unit,
) {
    val c = LocalTempoColors.current
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(TempoShapes.Glyph)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .semantics {
                this.contentDescription = contentDescription
                this.role = Role.Button
            },
        contentAlignment = Alignment.Center,
    ) {
        LineIcon(paths = paths, color = c.inkFaint, size = 23.dp)
    }
}
