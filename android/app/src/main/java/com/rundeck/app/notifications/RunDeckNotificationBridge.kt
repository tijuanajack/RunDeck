package com.rundeck.app.notifications

import android.app.Notification
import android.content.Context
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
    private const val MIN_NOTIFICATION_INTERVAL_MS = 90_000L
    private val allowedPackageHints = listOf(
        "messag", "mms", "whatsapp", "signal", "telegram", "messenger",
    )
    private val _events = MutableSharedFlow<RunDeckNotificationPayload>(extraBufferCapacity = 4)
    val events: SharedFlow<RunDeckNotificationPayload> = _events.asSharedFlow()
    private var lastForwardedAtMs = 0L

    fun onNotificationPosted(context: Context, sbn: StatusBarNotification) {
        if (!sbn.isClearable || sbn.packageName == context.packageName) return
        if (!isAllowedMessageNotification(sbn)) return
        val now = SystemClock.elapsedRealtime()
        if (now - lastForwardedAtMs < MIN_NOTIFICATION_INTERVAL_MS) return

        val extras = sbn.notification.extras
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.sanitize(32)
            ?: return
        val body = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()?.sanitize(96)
            ?: extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()?.sanitize(96)
            ?: return
        if (title.isBlank() || body.isBlank()) return

        lastForwardedAtMs = now
        _events.tryEmit(
            RunDeckNotificationPayload(
                app = appLabel(context, sbn.packageName).sanitize(16).ifBlank { "MESSAGE" },
                title = title,
                body = body,
            ),
        )
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

    private fun String.sanitize(max: Int): String = asSequence()
        .map { if (it.code in 32..126) it else ' ' }
        .joinToString("")
        .replace(Regex("\\s+"), " ")
        .trim()
        .take(max)
}
