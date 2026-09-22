package io.eddiegulay.tempo.notification

/**
 * The share-face rules that can be checked without a device: when to hide a sticker caption,
 * and which conversation lines belong on the lifted card.
 */

internal const val SHARE_THREAD_LIMIT = 3

/**
 * Body text that is only a media caption — "Sticker", スタンプ — once we already have the pixels.
 *
 * Compared case-insensitively and trimmed. A real caption ("look at this") is never in this list
 * and stays on the card.
 */
private val MEDIA_PLACEHOLDER_BODIES = setOf(
    "sticker",
    "スタンプ",
    "ステッカー",
    "photo",
    "image",
    "gif",
    "写真",
    "画像",
)

/** Last [SHARE_THREAD_LIMIT] messages, only when the notification can be replied to. Never pads. */
fun shareThread(n: TempoNotification): List<TempoNotificationMessage> =
    shareThread(n.messages, n.actions.any { it.isReply })

fun shareThread(
    messages: List<TempoNotificationMessage>,
    hasReply: Boolean,
    limit: Int = SHARE_THREAD_LIMIT,
): List<TempoNotificationMessage> {
    if (!hasReply) return emptyList()
    return messages.takeLast(limit)
}

/**
 * Body to draw on a card.
 *
 * Hidden when it is only a media placeholder and [hasPicture] is true, or when it merely repeats
 * the last thread line (the shade often copies EXTRA_TEXT from the newest message).
 */
fun displayBody(
    body: String,
    hasPicture: Boolean,
    shareMessages: List<TempoNotificationMessage> = emptyList(),
): String? {
    val trimmed = body.trim()
    if (trimmed.isEmpty()) return null
    if (hasPicture && isMediaPlaceholderBody(trimmed)) return null
    if (shareMessages.isNotEmpty() && shareMessages.last().text.trim() == trimmed) return null
    return body
}

fun isMediaPlaceholderBody(body: String): Boolean =
    body.trim().lowercase() in MEDIA_PLACEHOLDER_BODIES
