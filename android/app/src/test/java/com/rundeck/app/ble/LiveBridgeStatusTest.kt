package com.rundeck.app.ble

import org.junit.Assert.assertEquals
import org.junit.Test

class LiveBridgeStatusTest {
    @Test fun `offline status is explicit`() {
        assertEquals("DISPLAY OFFLINE", LiveBridgeStatus().label(nowMs = 10_000))
    }

    @Test fun `confirmed run packets report phone live`() {
        val status = LiveBridgeStatus(
            connected = true,
            streamingRun = true,
            lastSequence = 7,
            lastAttemptMs = 10_000,
            lastWriteConfirmedMs = 10_500,
        )

        assertEquals("PHONE LIVE → RUNDECK", status.label(nowMs = 11_000))
    }

    @Test fun `write errors are shown before generic connected state`() {
        val status = LiveBridgeStatus(connected = true, lastError = "METRIC WRITE FAILED (133)")

        assertEquals("METRIC WRITE FAILED (133)", status.label(nowMs = 10_000))
    }

    @Test fun `recent run state ack reports preset accepted`() {
        val status = LiveBridgeStatus(
            connected = true,
            streamingRun = true,
            lastRunStateAckSequence = 42,
            lastRunStateAckMs = 10_000,
        )

        assertEquals("PRESET ACCEPTED", status.label(nowMs = 12_000))
    }

    @Test fun `recent settings ack reports settings accepted`() {
        val status = LiveBridgeStatus(connected = true, lastSettingsAckMs = 10_000)
        assertEquals("SETTINGS ACCEPTED", status.label(nowMs = 12_000))
    }
}
