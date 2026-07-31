package com.rundeck.app.notifications

import android.app.Notification
import android.content.Context
import android.os.Build
import android.service.notification.StatusBarNotification
import android.os.SystemClock
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

data class RunDeckNotificationPayload(
    val app: String,
    val title: String,
    val body: String,
)

object RunDeckNotificationBridge {
    private const val RECENT_BUNDLE_WINDOW_MS = 120_000L
    private const val DUPLICATE_SUPPRESSION_MS = 90_000L
    private const val MAX_BUNDLED_MESSAGES_PER_POST = 4
    private val allowedPackageHints = listOf(
        "messag", "mms", "whatsapp", "signal", "telegram", "messenger",
    )
    private val _events = MutableSharedFlow<RunDeckNotificationPayload>(extraBufferCapacity = 12)
    val events: SharedFlow<RunDeckNotificationPayload> = _events.asSharedFlow()
    private val recentSignatures = linkedMapOf<String, Long>()

    fun onNotificationPosted(context: Context, sbn: StatusBarNotification) {
        if (!sbn.isClearable || sbn.packageName == context.packageName) return
        if (!isAllowedMessageNotification(sbn)) return
        val now = SystemClock.elapsedRealtime()
        pruneRecentSignatures(now)

        val candidates = bundledMessageCandidates(sbn).ifEmpty { listOfNotNull(summaryCandidate(sbn)) }
        val app = appLabel(context, sbn.packageName).sanitize(16).ifBlank { "MESSAGE" }

        candidates.forEach { candidate ->
            if (candidate.title.isBlank() || candidate.body.isBlank()) return@forEach
            val signature = "${sbn.packageName}|${candidate.title}|${candidate.body}"
            if (recentSignatures.containsKey(signature)) return@forEach
            recentSignatures[signature] = now
            _events.tryEmit(
                RunDeckNotificationPayload(
                    app = app,
                    title = candidate.title,
                    body = candidate.body,
                ),
            )
        }
    }

    private fun bundledMessageCandidates(sbn: StatusBarNotification): List<NotificationCandidate> {
        val extras = sbn.notification.extras
        val rawMessages = extras.getParcelableArray(Notification.EXTRA_MESSAGES) ?: return emptyList()
        val fallbackTitle = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()
        return Notification.MessagingStyle.Message.getMessagesFromBundleArray(rawMessages)
            .asSequence()
            .filter { message ->
                message.timestamp == 0L || message.timestamp >= sbn.postTime - RECENT_BUNDLE_WINDOW_MS
            }
            .mapNotNull { message ->
                val body = message.text?.toString()?.sanitize(96) ?: return@mapNotNull null
                val title = (message.senderName() ?: fallbackTitle)?.sanitize(32) ?: return@mapNotNull null
                NotificationCandidate(title = title, body = body, timestamp = message.timestamp)
            }
            .filter { it.title.isNotBlank() && it.body.isNotBlank() }
            .sortedBy { it.timestamp }
            .toList()
            .takeLast(MAX_BUNDLED_MESSAGES_PER_POST)
    }

    private fun summaryCandidate(sbn: StatusBarNotification): NotificationCandidate? {
        val extras = sbn.notification.extras
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.sanitize(32)
            ?: return null
        val body = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()?.sanitize(96)
            ?: extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()?.sanitize(96)
            ?: return null
        return NotificationCandidate(title = title, body = body, timestamp = sbn.postTime)
    }

    private fun isAllowedMessageNotification(sbn: StatusBarNotification): Boolean {
        if (sbn.notification.category == Notification.CATEGORY_MESSAGE) return true
        val packageName = sbn.packageName.lowercase()
        return allowedPackageHints.any(packageName::contains)
    }

    private fun appLabel(context: Context, packageName: String): String = runCatching {
        val info = context.packageManager.getApplicationInfo(packageName, 0)
        context.packageManager.getApplicationLabel(info).toString()
    }.getOrElse { packageName.substringAfterLast('.') }

    private fun Notification.MessagingStyle.Message.senderName(): String? {
        val personName = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            senderPerson?.name?.toString()
        } else {
            null
        }
        return personName ?: sender?.toString()
    }

    private fun pruneRecentSignatures(now: Long) {
        val iterator = recentSignatures.iterator()
        while (iterator.hasNext()) {
            if (now - iterator.next().value >= DUPLICATE_SUPPRESSION_MS) {
                iterator.remove()
            }
        }
    }

    private fun String.sanitize(max: Int): String = asSequence()
        .map { if (it.code in 32..126) it else ' ' }
        .joinToString("")
        .replace(Regex("\\s+"), " ")
        .trim()
        .take(max)

    private data class NotificationCandidate(
        val title: String,
        val body: String,
        val timestamp: Long,
    )
}
