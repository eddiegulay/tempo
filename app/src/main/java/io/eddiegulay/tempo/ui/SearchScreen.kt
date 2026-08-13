package io.eddiegulay.tempo.ui

import android.app.ActivityOptions
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.eddiegulay.tempo.LauncherViewModel
import io.eddiegulay.tempo.data.AppInfo
import io.eddiegulay.tempo.ui.theme.Gothic
import io.eddiegulay.tempo.i18n.LocalStrings
import io.eddiegulay.tempo.ui.theme.LocalTempoColors
import io.eddiegulay.tempo.ui.theme.Mincho
import io.eddiegulay.tempo.ui.theme.TempoShapes
import io.eddiegulay.tempo.ui.theme.combinedPressable
import java.time.Instant
import java.time.ZoneId

/**
 * Search (検索): a bottom-ruled mincho input over a live-filtered list of every installed app.
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

    LaunchedEffect(Unit) { viewModel.ensureAppsLoaded() }

    val filtered = remember(query, apps) {
        val q = query.trim()
        if (q.isEmpty()) apps
        else apps.filter { it.label.contains(q, ignoreCase = true) || it.packageName.contains(q, ignoreCase = true) }
    }
    val noResults = query.isNotBlank() && filtered.isEmpty()
    val loading = apps.isEmpty() && query.isBlank()

    // Search doubles as the app drawer, so it opens unfocused — no keyboard pops up until the user
    // taps the field. Submitting still launches the top hit.
    val keyboard = LocalSoftwareKeyboardController.current
    val launchTop: () -> Unit = {
        filtered.firstOrNull()?.let { top ->
            keyboard?.hide()
            viewModel.launchApp(context, top)
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
            items(filtered, key = { it.key }) { app ->
                AppRow(viewModel = viewModel, app = app)
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
                val subtitle = remember(app.category, app.lastUpdated, s) {
                    val date = app.lastUpdated.takeIf { it > 0L }?.let {
                        s.search.updatedPrefix +
                            s.fmt.monthDay(Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDateTime())
                    }
                    listOfNotNull(app.category, date).joinToString(" · ")
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

/**
 * A faint, 48dp-target line-icon button used in the Search header (filter + theme toggle).
 *
 * **No indication, deliberately** — and it is the one control in this file that keeps that. These
 * three glyphs are page chrome sitting beside a 14sp heading; a wash under each would put three grey
 * tiles across the top of the quietest screen in the app. The result of every one of them is instant
 * and unmistakable (the page turns, the theme flips, a picker opens), which is the feedback.
 */
@Composable
private fun HeaderIconButton(
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
