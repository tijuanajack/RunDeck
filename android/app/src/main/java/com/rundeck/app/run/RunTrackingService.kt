package com.rundeck.app.run

import android.app.Service
import android.content.Intent
import android.os.IBinder

/** Foreground notification, location client, and persisted run checkpoint are Phase 3 work. */
class RunTrackingService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null
}
