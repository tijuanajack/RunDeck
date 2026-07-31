package com.rundeck.app.media

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.rundeck.app.notifications.RunDeckNotificationBridge

/**
 * Grants RunDeck access to active MediaSession controllers when the user
 * enables notification listener access. It also forwards only sanitized,
 * short-lived message-style notification payloads to the in-process BLE bridge.
 */
class RunDeckNotificationListenerService : NotificationListenerService() {
    override fun onNotificationPosted(sbn: StatusBarNotification) {
        RunDeckNotificationBridge.onNotificationPosted(applicationContext, sbn)
    }
}
