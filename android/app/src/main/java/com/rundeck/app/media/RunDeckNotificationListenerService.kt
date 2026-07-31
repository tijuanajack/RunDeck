package com.rundeck.app.media

import android.service.notification.NotificationListenerService

/**
 * Grants RunDeck access to active MediaSession controllers when the user
 * enables notification listener access. Notification payload forwarding will
 * be built later behind an explicit allowlist.
 */
class RunDeckNotificationListenerService : NotificationListenerService()
