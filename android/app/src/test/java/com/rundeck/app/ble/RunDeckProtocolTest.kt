package com.rundeck.app.ble

import org.junit.Assert.assertEquals
import org.junit.Test

class RunDeckProtocolTest {
    @Test fun `live metrics round trip preserves every field`() {
        val metrics = LiveMetrics(15, 50500, 12345, 72, 70, 320, 780, 143)
        val decoded = RunDeckProtocol.decodeLiveMetrics(RunDeckProtocol.encodeLiveMetrics(42, 1234, metrics))
        assertEquals(42, decoded.sequence)
        assertEquals(1234, decoded.sourceMonotonicMs)
        assertEquals(metrics, decoded.metrics)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `malformed frame is rejected`() {
        RunDeckProtocol.decodeLiveMetrics(ByteArray(3))
    }
}
