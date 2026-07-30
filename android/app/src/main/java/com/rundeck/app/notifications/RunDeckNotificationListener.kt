package com.rundeck.app.notifications

import android.service.notification.NotificationListenerService

/** Sends only allowlisted, sanitized, short-lived notification content to BLE. */
class RunDeckNotificationListener : NotificationListenerService()
