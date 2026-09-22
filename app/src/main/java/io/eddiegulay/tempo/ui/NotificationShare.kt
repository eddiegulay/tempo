package io.eddiegulay.tempo.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.eddiegulay.tempo.i18n.LocalStrings
import io.eddiegulay.tempo.notification.TempoNotification
import io.eddiegulay.tempo.notification.TempoNotificationMessage
import io.eddiegulay.tempo.notification.copyPngToClipboard
import io.eddiegulay.tempo.notification.displayBody
import io.eddiegulay.tempo.notification.exportFileStem
import io.eddiegulay.tempo.notification.opaqueCardFill
import io.eddiegulay.tempo.notification.savePngToPictures
import io.eddiegulay.tempo.notification.shareThread
import io.eddiegulay.tempo.notification.toPngBytes
import io.eddiegulay.tempo.ui.theme.Gothic
import io.eddiegulay.tempo.ui.theme.LocalTempoColors
import io.eddiegulay.tempo.ui.theme.Mincho
import io.eddiegulay.tempo.ui.theme.TempoShapes
import io.eddiegulay.tempo.ui.theme.pressable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/** One notification, lifted, with its window-space face rect. */
data class NotificationShareRequest(
    val notification: TempoNotification,
    val bounds: Rect,
)

/** Finger must stay down this long before the card lifts. Swipe and scroll cancel it. */
internal const val SHARE_HOLD_MS = 900L

/** How far the lifted card rises, matching a 1.02 scale so it reads picked-up, not a page push. */
private val LiftOffset = 10.dp
private const val LiftScale = 1.02f
private const val EnterMs = 240
private const val ExitMs = 90
private const val ScrimAlpha = 0.62f

/** Same bottom band the list and undo strip already keep clear of the floating dock. */
private val DockClearance = 96.dp

/**
 * The two-word share overlay: a dimmed list, the held card lifted in place, 保存 / コピー beneath
 * it. Tap the scrim or press back and the card settles into its slot over [ExitMs].
 *
 * The PNG is recorded from a hidden twin of the card (opaque fill, no scale) so the clipboard
 * image is the bubble, not a screenshot of this overlay.
 */
@Composable
fun NotificationShareOverlay(
    n: TempoNotification,
    cardBoundsInWindow: Rect,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = LocalTempoColors.current
    val s = LocalStrings.current
    val context = LocalContext.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val graphicsLayer = rememberGraphicsLayer()
    val progress = remember { Animatable(0f) }
    val shareHaptics = remember { ShareHaptics(context) }

    var overlayOrigin by remember { mutableStateOf<Offset?>(null) }
    var pngBytes by remember(n.key) { mutableStateOf<ByteArray?>(null) }
    var fault by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var leaving by remember { mutableStateOf(false) }
    var liftedH by remember { mutableStateOf(0f) }
    var captureArmed by remember(n.key) { mutableStateOf(true) }
    var scrimArmed by remember(n.key) { mutableStateOf(false) }

    LaunchedEffect(n.key) {
        shareHaptics.pop()
        progress.snapTo(0f)
        progress.animateTo(1f, tween(EnterMs, easing = LinearOutSlowInEasing))
        // The opening finger is still down. Arm the scrim only after that gesture is over
        // so the same press cannot settle the overlay and swallow the tap-to-open.
        scrimArmed = true
    }

    LaunchedEffect(n.key, graphicsLayer) {
        // Two frames: the hidden card must be measured and recorded before we read the layer.
        withFrameNanos { }
        withFrameNanos { }
        val image = runCatching { graphicsLayer.toImageBitmap() }.getOrNull()
        pngBytes = image?.let { withContext(Dispatchers.Default) { runCatching { it.toPngBytes() }.getOrNull() } }
        captureArmed = false
    }

    fun settle() {
        if (leaving) return
        leaving = true
        shareHaptics.close()
        scope.launch {
            progress.animateTo(0f, tween(ExitMs, easing = FastOutLinearInEasing))
            onDismiss()
        }
    }

    BackHandler(enabled = !leaving) { settle() }

    fun runExport(copy: Boolean) {
        if (busy || leaving) return
        busy = true
        scope.launch {
            val bytes = pngBytes ?: run {
                val image = runCatching { graphicsLayer.toImageBitmap() }.getOrNull()
                image?.let {
                    withContext(Dispatchers.Default) { runCatching { it.toPngBytes() }.getOrNull() }
                }
            }
            if (bytes == null) {
                fault = if (copy) s.notifications.copyFailed else s.notifications.saveFailed
                busy = false
                return@launch
            }
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    if (copy) {
                        copyPngToClipboard(context, bytes, s.notifications.copyImage)
                    } else {
                        val stem = exportFileStem(n.appLabel, n.postTime)
                        checkNotNull(savePngToPictures(context, bytes, stem))
                    }
                }.isSuccess
            }
            if (ok) {
                settle()
            } else {
                fault = if (copy) s.notifications.copyFailed else s.notifications.saveFailed
                busy = false
            }
        }
    }

    BoxWithConstraints(
        modifier
            .fillMaxSize()
            .onGloballyPositioned { overlayOrigin = it.positionInWindow() },
    ) {
        val t = progress.value
        val origin = overlayOrigin
        val liftPx = with(density) { LiftOffset.toPx() }
        val cardW = cardBoundsInWindow.width
        val canPlace = origin != null && cardW > 0f
        val measured = liftedH > 0f
        val localX = if (origin != null) cardBoundsInWindow.left - origin.x else 0f
        val localY = if (origin != null) cardBoundsInWindow.top - origin.y else 0f
        val scale = 1f + (LiftScale - 1f) * t
        val rise = -liftPx * t

        val gapPx = with(density) { 16.dp.toPx() }
        val optionH = with(density) { 48.dp.toPx() }
        val dockPx = with(density) { DockClearance.toPx() }
        val topPad = with(density) { 16.dp.toPx() }
        val reservedBelow = gapPx + optionH + dockPx
        val maxFacePx = (constraints.maxHeight - reservedBelow - topPad).coerceAtLeast(0f)
        val maxFaceDp = with(density) { maxFacePx.toDp() }
        val faceH = if (measured) liftedH else 0f
        val desiredY = localY + rise
        val maxCardY = (constraints.maxHeight - reservedBelow - faceH).coerceAtLeast(topPad)
        val cardY = if (canPlace) desiredY.coerceIn(topPad, maxCardY) else desiredY
        // Decide from the rest slot, not the animated lift, so the words do not jump sides mid-enter.
        val placeBelow = cardY + faceH + gapPx + optionH <= constraints.maxHeight - dockPx
        val minY = 0f
        val maxY = (constraints.maxHeight - optionH - dockPx).coerceAtLeast(minY)
        val rawOptionsY = if (placeBelow) {
            cardY + faceH * scale + gapPx
        } else {
            cardY - gapPx - optionH
        }
        val optionsY = rawOptionsY.coerceIn(minY, maxY)
        val shown = if (measured) t else 0f

        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = t }
                .background(c.bgSolid.copy(alpha = ScrimAlpha))
                .pointerInput(scrimArmed) {
                    if (!scrimArmed) return@pointerInput
                    awaitEachGesture { awaitFirstDown(); settle() }
                }
                .clearAndSetSemantics { },
        )

        if (canPlace) {
            // Twin first and off to the side so its expanded height cannot sit on 保存 / コピー.
            Box(
                Modifier
                    .offset { IntOffset((-cardW * 2f).roundToInt(), localY.roundToInt()) }
                    .width(with(density) { cardW.toDp() })
                    .graphicsLayer { alpha = 0f }
                    .drawWithContent {
                        if (captureArmed) {
                            graphicsLayer.record { this@drawWithContent.drawContent() }
                        }
                        drawLayer(graphicsLayer)
                    }
                    .clearAndSetSemantics { },
            ) {
                NotificationCardFace(n = n, fill = c.opaqueCardFill(), expanded = true)
            }

            Box(
                Modifier
                    .offset { IntOffset(localX.roundToInt(), cardY.roundToInt()) }
                    .width(with(density) { cardW.toDp() })
                    .onGloballyPositioned { liftedH = it.size.height.toFloat() }
                    .graphicsLayer {
                        alpha = shown
                        scaleX = scale
                        scaleY = scale
                        transformOrigin = TransformOrigin.Center
                    },
            ) {
                NotificationCardFace(
                    n = n,
                    fill = c.opaqueCardFill(),
                    expanded = true,
                    maxHeight = maxFaceDp,
                )
            }

            val emerge = with(density) { 8.dp.toPx() } * (1f - t)
            val optionsSlide = if (placeBelow) emerge else -emerge

            Row(
                modifier = Modifier
                    .offset { IntOffset(localX.roundToInt(), (optionsY + optionsSlide).roundToInt()) }
                    .width(with(density) { cardW.toDp() })
                    .graphicsLayer { alpha = shown },
                horizontalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ShareWord(
                    label = s.notifications.saveImage,
                    enabled = !busy && !leaving && measured,
                    onClick = { runExport(copy = false) },
                )
                ShareWord(
                    label = s.notifications.copyImage,
                    enabled = !busy && !leaving && measured,
                    onClick = { runExport(copy = true) },
                )
            }
        }

        fault?.let { message ->
            Text(
                text = message,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = DockClearance)
                    .graphicsLayer { alpha = t },
                style = TextStyle(
                    fontFamily = Mincho,
                    fontSize = 13.sp,
                    letterSpacing = 2.sp,
                    color = c.accent,
                ),
            )
        }
    }
}

@Composable
private fun ShareWord(label: String, enabled: Boolean, onClick: () -> Unit) {
    val c = LocalTempoColors.current
    Box(
        modifier = Modifier
            .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
            .pressable(TempoShapes.Word, enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = TextStyle(
                fontFamily = Mincho,
                fontSize = 13.sp,
                letterSpacing = 1.sp,
                color = c.accent,
            ),
        )
    }
}

/**
 * Icon, title, body, time, app name — the shareable face of a notification, without swipe chrome
 * or action chips. [fill] is [io.eddiegulay.tempo.notification.opaqueCardFill] when this is a
 * lifted / exported bubble, and [io.eddiegulay.tempo.ui.theme.TempoColors.card] when it sits in the list.
 *
 * [expanded] is the share / PNG face: full title and body, last-three thread when a reply
 * exists. The list keeps one-line title and three-line body. [maxHeight] is a cap only — the
 * bubble wraps its content and never stretches to fill the screen.
 */
@Composable
fun NotificationCardFace(
    n: TempoNotification,
    fill: Color,
    modifier: Modifier = Modifier,
    expanded: Boolean = false,
    maxHeight: Dp? = null,
) {
    val c = LocalTempoColors.current
    val thread = if (expanded) shareThread(n) else emptyList()
    val body = displayBody(n.body, hasPicture = false, thread)
    val scroll = rememberScrollState()

    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(if (maxHeight != null) Modifier.heightIn(max = maxHeight) else Modifier)
            .then(if (maxHeight != null) Modifier.verticalScroll(scroll) else Modifier)
            .clip(TempoShapes.Card)
            .background(fill)
            .padding(horizontal = 18.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (n.icon != null) {
            Image(
                bitmap = n.icon,
                contentDescription = n.appLabel,
                colorFilter = ColorFilter.tint(c.inkSoft),
                modifier = Modifier.padding(top = 2.dp).size(20.dp),
            )
        } else {
            Spacer(Modifier.width(20.dp))
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            FaceHeader(n = n, expanded = expanded)
            FaceMedia(expanded = expanded, body = body, thread = thread)
            Text(
                text = n.appLabel,
                style = TextStyle(
                    fontFamily = Mincho,
                    fontSize = 11.sp,
                    letterSpacing = 3.sp,
                    color = c.inkFaint,
                ),
            )
        }
    }
}

@Composable
private fun FaceHeader(
    n: TempoNotification,
    expanded: Boolean,
) {
    val c = LocalTempoColors.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = n.title,
            maxLines = if (expanded) Int.MAX_VALUE else 1,
            overflow = if (expanded) TextOverflow.Clip else TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = true),
            style = TextStyle(fontFamily = Mincho, fontSize = 16.sp, color = c.ink),
        )
        Text(
            text = n.time,
            style = TextStyle(fontFamily = Gothic, fontSize = 12.sp, color = c.inkFaint),
        )
    }
}

@Composable
private fun FaceMedia(
    expanded: Boolean,
    body: String?,
    thread: List<TempoNotificationMessage>,
) {
    val c = LocalTempoColors.current
    if (body != null) {
        Text(
            text = body,
            maxLines = if (expanded) Int.MAX_VALUE else 3,
            overflow = if (expanded) TextOverflow.Clip else TextOverflow.Ellipsis,
            style = TextStyle(
                fontFamily = Gothic,
                fontSize = 13.sp,
                lineHeight = 19.5.sp,
                color = c.inkSoft,
            ),
        )
    }
    if (thread.isNotEmpty()) {
        Box(
            Modifier
                .fillMaxWidth()
                .padding(top = 8.dp, bottom = 4.dp)
                .height(1.dp)
                .background(c.hair),
        )
        thread.forEach { message ->
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = if (message.isSelf) Alignment.End else Alignment.Start,
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                if (message.sender.isNotBlank()) {
                    Text(
                        text = message.sender,
                        style = TextStyle(
                            fontFamily = Mincho,
                            fontSize = 11.sp,
                            letterSpacing = 2.sp,
                            color = c.inkFaint,
                        ),
                    )
                }
                Text(
                    text = message.text,
                    style = TextStyle(
                        fontFamily = Gothic,
                        fontSize = 13.sp,
                        lineHeight = 19.5.sp,
                        color = if (message.isSelf) c.ink else c.inkSoft,
                    ),
                )
            }
        }
    }
}

/**
 * A still-finger hold, driven from [pressable]'s own [MutableInteractionSource].
 *
 * Swipe-to-dismiss and list scroll cancel the press, which cancels the timer — so the hold
 * never fights those gestures. The subsequent up still fires `onClick`; the caller suppresses
 * open with a flag. Pop / close haptics live on the overlay, not here.
 */
@Composable
fun ShareHoldEffect(
    enabled: Boolean,
    interactionSource: MutableInteractionSource,
    durationMillis: Long = SHARE_HOLD_MS,
    onHold: () -> Unit,
) {
    val latestOnHold by rememberUpdatedState(onHold)
    LaunchedEffect(enabled, interactionSource, durationMillis) {
        if (!enabled) return@LaunchedEffect
        var job: Job? = null
        interactionSource.interactions.collect { interaction ->
            when (interaction) {
                is PressInteraction.Press -> {
                    job?.cancel()
                    job = launch {
                        delay(durationMillis)
                        latestOnHold()
                    }
                }
                is PressInteraction.Release, is PressInteraction.Cancel -> {
                    job?.cancel()
                    job = null
                }
            }
        }
    }
}
