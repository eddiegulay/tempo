package io.eddiegulay.tempo.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.eddiegulay.tempo.LauncherViewModel
import io.eddiegulay.tempo.calendar.rememberCalendarPermissionState
import io.eddiegulay.tempo.contacts.rememberContactsPermissionState
import io.eddiegulay.tempo.i18n.LocalStrings
import io.eddiegulay.tempo.i18n.SearchAreasStrings
import io.eddiegulay.tempo.search.SearchArea
import io.eddiegulay.tempo.ui.theme.Gothic
import io.eddiegulay.tempo.ui.theme.LocalTempoColors
import io.eddiegulay.tempo.ui.theme.Mincho
import io.eddiegulay.tempo.ui.theme.TempoShapes
import io.eddiegulay.tempo.ui.theme.pressable

/**
 * Search areas (検索の範囲): which sources Search may use. Reached by long-pressing Search in the dock.
 * Toggles are a word, the same control gym settings and the calendar composer already use.
 */
@Composable
fun SearchAreasScreen(viewModel: LauncherViewModel, modifier: Modifier = Modifier) {
    val c = LocalTempoColors.current
    val s = LocalStrings.current
    val areas by viewModel.searchAreas.collectAsStateWithLifecycle()
    val calendarGranted by viewModel.calendarAccess.collectAsStateWithLifecycle()
    val contactsGranted by viewModel.contactsAccess.collectAsStateWithLifecycle()
    val calendarPermission = rememberCalendarPermissionState(
        granted = calendarGranted,
        onGrantedChange = viewModel::setCalendarAccess,
    )
    val contactsPermission = rememberContactsPermissionState(
        granted = contactsGranted,
        onGrantedChange = viewModel::setContactsAccess,
    )
    val lang by viewModel.lang.collectAsStateWithLifecycle()
    var showLanguage by remember { mutableStateOf(false) }

    Column(modifier.fillMaxSize()) {
        Column(Modifier.padding(start = 26.dp, end = 26.dp, top = 20.dp)) {
            s.searchAreas.kana?.let { kana ->
                Text(
                    text = kana,
                    style = TextStyle(fontFamily = Mincho, fontSize = 14.sp, letterSpacing = 6.sp, color = c.inkFaint),
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = if (s.searchAreas.kana != null) 12.dp else 0.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = s.searchAreas.title,
                    style = TextStyle(fontFamily = Mincho, fontSize = 26.sp, color = c.ink),
                    modifier = Modifier.weight(1f),
                )
                HeaderIconButton(
                    paths = TempoIcons.Globe,
                    contentDescription = s.search.language,
                    onClick = { showLanguage = true },
                )
            }
            Text(
                text = s.searchAreas.subtitle,
                style = TextStyle(fontFamily = Gothic, fontSize = 11.sp, letterSpacing = 2.sp, color = c.inkFaint),
                modifier = Modifier.padding(top = 8.dp),
            )
        }

        LazyColumn(
            modifier = Modifier.fillMaxWidth().weight(1f),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 18.dp, bottom = 96.dp),
        ) {
            items(SearchArea.entries, key = { it.name }) { area ->
                val on = areas.isOn(area)
                val subtitle = when {
                    area == SearchArea.Calendar && on && !calendarGranted ->
                        s.searchAreas.calendarNeedsAccess
                    area == SearchArea.Contacts && on && !contactsGranted ->
                        s.searchAreas.contactsNeedsAccess
                    else -> null
                }
                AreaRow(
                    label = areaLabel(s.searchAreas, area),
                    on = on,
                    wordOn = s.searchAreas.toggleOn,
                    wordOff = s.searchAreas.toggleOff,
                    subtitle = subtitle,
                    onToggle = {
                        val next = !on
                        viewModel.setSearchArea(area, next)
                        if (area == SearchArea.Calendar && next && !calendarGranted) {
                            calendarPermission.request()
                        }
                        if (area == SearchArea.Contacts && next && !contactsGranted) {
                            contactsPermission.request()
                        }
                    },
                )
            }
        }
    }

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
private fun AreaRow(
    label: String,
    on: Boolean,
    wordOn: String,
    wordOff: String,
    subtitle: String?,
    onToggle: () -> Unit,
) {
    val c = LocalTempoColors.current
    val word = if (on) wordOn else wordOff
    val wordColour by animateColorAsState(
        targetValue = if (on) c.accent else c.inkFaint,
        animationSpec = tween(120, easing = LinearOutSlowInEasing),
        label = "area-toggle-word",
    )
    val announced = if (subtitle != null) "$label, $word, $subtitle" else "$label, $word"

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .sizeIn(minHeight = 56.dp)
            .pressable(TempoShapes.Row, onClick = onToggle)
            .clearAndSetSemantics {
                role = Role.Switch
                stateDescription = word
                contentDescription = announced
            }
            .padding(horizontal = 18.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                style = TextStyle(fontFamily = Mincho, fontSize = 16.sp, letterSpacing = 1.sp, color = c.ink),
            )
            Text(
                text = word,
                style = TextStyle(fontFamily = Mincho, fontSize = 14.sp, color = wordColour),
            )
        }
        if (subtitle != null) {
            Text(
                text = subtitle,
                style = TextStyle(fontFamily = Gothic, fontSize = 11.sp, letterSpacing = 2.sp, color = c.inkFaint),
            )
        }
    }
}

private fun areaLabel(copy: SearchAreasStrings, area: SearchArea): String = when (area) {
    SearchArea.Apps -> copy.apps
    SearchArea.Phone -> copy.phone
    SearchArea.Contacts -> copy.contacts
    SearchArea.WhatsApp -> copy.whatsApp
    SearchArea.Google -> copy.google
    SearchArea.Email -> copy.email
    SearchArea.Calendar -> copy.calendar
    SearchArea.Spotify -> copy.spotify
}
