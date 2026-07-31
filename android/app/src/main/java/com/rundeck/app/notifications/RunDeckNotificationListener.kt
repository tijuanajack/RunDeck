package com.rundeck.app.notifications

import android.service.notification.NotificationListenerService

/** Sends only allowlisted, sanitized, short-lived notification content to BLE. */
class RunDeckNotificationListener : NotificationListenerService() {
    override fun onListenerConnected() {
        instance = this
    }

    override fun onListenerDisconnected() {
        if (instance === this) instance = null
    }

    companion object {
        private var instance: RunDeckNotificationListener? = null

        fun dismiss(key: String) {
            instance?.let { service -> runCatching { service.cancelNotification(key) } }
        }
    }
}
