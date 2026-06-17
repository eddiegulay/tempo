package io.eddiegulay.tempo.ui

import android.app.ActivityOptions
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.eddiegulay.tempo.LauncherViewModel
import io.eddiegulay.tempo.data.AppInfo
import io.eddiegulay.tempo.ui.theme.Gothic
import io.eddiegulay.tempo.ui.theme.LocalTempoColors
import io.eddiegulay.tempo.ui.theme.Mincho

/**
 * Search (検索): a bottom-ruled mincho input over a live-filtered list of every installed app.
 *
 * The inventory is the shared, live [LauncherViewModel] flow; icons load lazily per visible row from
 * the repository's cache. Tapping launches with a scale-up animation from the row; long-press opens
 * a minimal menu (app info / uninstall).
 */
@Composable
fun SearchScreen(viewModel: LauncherViewModel, modifier: Modifier = Modifier) {
    val c = LocalTempoColors.current
    val context = LocalContext.current

    val query by viewModel.searchQuery.collectAsStateWithLifecycle()
    val apps by viewModel.apps.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { viewModel.ensureAppsLoaded() }

    val filtered = remember(query, apps) {
        val q = query.trim()
        if (q.isEmpty()) apps
        else apps.filter { it.label.contains(q, ignoreCase = true) || it.packageName.contains(q, ignoreCase = true) }
    }
    val noResults = query.isNotBlank() && filtered.isEmpty()
    val loading = apps.isEmpty() && query.isBlank()

    // Focus the field and raise the keyboard on entry; drop the query when leaving Search.
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
        keyboard?.show()
    }
    val launchTop: () -> Unit = {
        filtered.firstOrNull()?.let { top ->
            keyboard?.hide()
            viewModel.launchApp(context, top)
        }
    }

    Column(modifier.fillMaxSize()) {
        Column(Modifier.padding(start = 26.dp, end = 26.dp, top = 20.dp)) {
            Text(
                text = "けんさく",
                style = TextStyle(fontFamily = Mincho, fontSize = 14.sp, letterSpacing = 6.sp, color = c.inkFaint),
            )
            Box(Modifier.height(14.dp))
            BasicTextField(
                value = query,
                onValueChange = viewModel::onSearchQueryChange,
                singleLine = true,
                textStyle = TextStyle(fontFamily = Mincho, fontSize = 26.sp, color = c.ink),
                cursorBrush = SolidColor(c.accent),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = { launchTop() }),
                modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
                decorationBox = { inner ->
                    Column {
                        Box(Modifier.padding(vertical = 8.dp, horizontal = 2.dp)) {
                            if (query.isEmpty()) {
                                Text(
                                    text = "検索",
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
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 18.dp, bottom = 24.dp),
        ) {
            if (loading || noResults) {
                item {
                    Box(Modifier.fillMaxWidth().padding(top = 70.dp), contentAlignment = Alignment.Center) {
                        Text(
                            text = if (loading) "・・・" else "見つかりません",
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
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AppRow(viewModel: LauncherViewModel, app: AppInfo) {
    val c = LocalTempoColors.current
    val context = LocalContext.current
    val rootView = LocalView.current

    // Lazy, cached icon: paint the cache hit instantly, otherwise load off the main thread.
    val icon by produceState<ImageBitmap?>(initialValue = viewModel.peekIcon(app), app.key) {
        if (value == null) value = viewModel.loadIcon(app)
    }

    var menuOpen by remember { mutableStateOf(false) }
    var rowBounds by remember { mutableStateOf<android.graphics.Rect?>(null) }

    Box {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .onGloballyPositioned { coords ->
                    val r = coords.boundsInWindow()
                    rowBounds = android.graphics.Rect(r.left.toInt(), r.top.toInt(), r.right.toInt(), r.bottom.toInt())
                }
                .combinedClickable(
                    onClickLabel = "起動",
                    onLongClickLabel = "メニュー",
                    onClick = {
                        val b = rowBounds
                        val opts = if (b != null) {
                            ActivityOptions.makeScaleUpAnimation(rootView, b.left, b.top, b.width(), b.height()).toBundle()
                        } else null
                        viewModel.launchApp(context, app, b, opts)
                    },
                    onLongClick = { menuOpen = true },
                )
                .padding(horizontal = 12.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            val iconShape = RoundedCornerShape(7.dp)
            if (icon != null) {
                Image(
                    bitmap = icon!!,
                    contentDescription = app.label,
                    modifier = Modifier.size(26.dp).clip(iconShape).border(1.dp, c.hair, iconShape),
                )
            } else {
                // Placeholder while the icon decodes — keeps row height stable, avoids jank.
                Box(Modifier.size(26.dp).clip(iconShape).background(c.card).border(1.dp, c.hair, iconShape))
            }
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = app.label,
                    style = TextStyle(fontFamily = Mincho, fontSize = 18.sp, letterSpacing = 1.sp, color = c.ink),
                )
                Text(
                    text = app.packageName,
                    style = TextStyle(fontFamily = Gothic, fontSize = 11.sp, letterSpacing = 2.sp, color = c.inkFaint),
                )
            }
        }

        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            DropdownMenuItem(
                text = { Text("アプリ情報", style = TextStyle(fontFamily = Mincho, color = c.ink)) },
                onClick = {
                    menuOpen = false
                    viewModel.openAppInfo(context, app)
                },
            )
            DropdownMenuItem(
                text = { Text("アンインストール", style = TextStyle(fontFamily = Mincho, color = c.ink)) },
                onClick = {
                    menuOpen = false
                    viewModel.requestUninstall(context, app)
                },
            )
        }
    }
}
