package com.rundeck.app.ble

import org.junit.Assert.assertEquals
import org.junit.Assert.assertArrayEquals
import org.junit.Test

class RunDeckProtocolTest {
    @Test fun `live metrics round trip preserves every field`() {
        val metrics = LiveMetrics(15, 50500, 12345, 72, 70, 320, 780, 143)
        val decoded = RunDeckProtocol.decodeLiveMetrics(RunDeckProtocol.encodeLiveMetrics(42, 1234, metrics))
        assertEquals(42, decoded.sequence)
        assertEquals(1234, decoded.sourceMonotonicMs)
        assertEquals(metrics, decoded.metrics)
    }

    @Test fun `live metrics payload begins after the complete 12 byte header`() {
        val frame = RunDeckProtocol.encodeLiveMetrics(1, 2, LiveMetrics(0x0003, 50500, 1, 2, 2, 0, 0, 0))
        // flags (03 00) and pace (44 C5) must begin at offsets 12 and 14.
        assertArrayEquals(byteArrayOf(3, 0, 0x44, 0xC5.toByte()), frame.copyOfRange(12, 16))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `malformed frame is rejected`() {
        RunDeckProtocol.decodeLiveMetrics(ByteArray(3))
    }

    @Test fun `run state cbor round trip preserves preset and targets`() {
        val state = RunStatePacket(
            active = true,
            presetName = "LONG RUN",
            targetLabel = "8:50-9:20",
            paceLowSecondsPerMile = 530,
            paceHighSecondsPerMile = 560,
            hrLowBpm = 135,
            hrHighBpm = 150,
        )

        val decoded = RunDeckProtocol.decodeRunState(RunDeckProtocol.encodeRunState(44, state))

        assertEquals(44, decoded.sequence)
        assertEquals(state, decoded.state)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `oversized run state is rejected`() {
        RunDeckProtocol.decodeRunState(ByteArray(RunDeckProtocol.MAX_RUN_STATE_BYTES + 1))
    }
}
