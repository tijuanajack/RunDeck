package com.rundeck.app.ble

import org.junit.Assert.assertEquals
import org.junit.Assert.assertArrayEquals
import org.junit.Test

class RunDeckProtocolTest {
    @Test fun `live metrics round trip preserves every field`() {
        val metrics = LiveMetrics(15, 505, 12345, 72, 70, 320, 780, 143)
        val decoded = RunDeckProtocol.decodeLiveMetrics(RunDeckProtocol.encodeLiveMetrics(42, 1234, metrics))
        assertEquals(42, decoded.sequence)
        assertEquals(1234, decoded.sourceMonotonicMs)
        assertEquals(metrics, decoded.metrics)
    }

    @Test fun `live metrics payload begins after the complete 12 byte header`() {
        val frame = RunDeckProtocol.encodeLiveMetrics(1, 2, LiveMetrics(0x0003, 505, 1, 2, 2, 0, 0, 0))
        // flags (03 00) and pace seconds (F9 01) must begin at offsets 12 and 14.
        assertArrayEquals(byteArrayOf(3, 0, 0xF9.toByte(), 0x01), frame.copyOfRange(12, 16))
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

    @Test fun `media state cbor round trip preserves bounded metadata`() {
        val state = MediaStatePacket(
            available = true,
            playing = true,
            source = "Spotify",
            title = "Hungersite",
            artist = "Goose",
        )

        val decoded = RunDeckProtocol.decodeMediaState(RunDeckProtocol.encodeMediaState(45, state))

        assertEquals(45, decoded.sequence)
        assertEquals(state, decoded.state)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `oversized media state is rejected`() {
        RunDeckProtocol.decodeMediaState(ByteArray(RunDeckProtocol.MAX_MEDIA_STATE_BYTES + 1))
    }

    @Test fun `notification payload encodes bounded text`() {
        val payload = RunDeckProtocol.encodeNotification(
            46,
            NotificationPacket(app = "Messages", title = "Ellen", body = "Leaving now"),
        )

        assertEquals(0xA5, payload[0].toInt() and 0xFF)
        assert(payload.size <= RunDeckProtocol.MAX_NOTIFICATION_BYTES)
    }

    @Test fun `display context encodes clock and stale-safe weather fields`() {
        val payload = RunDeckProtocol.encodeDisplayContext(
            47,
            DisplayContextPacket("10:42 AM", weatherState = 0, temperatureAvailable = true, temperatureF = 78),
        )

        assertEquals(0xA6, payload[0].toInt() and 0xFF)
        assert(payload.size <= RunDeckProtocol.MAX_DISPLAY_CONTEXT_BYTES)
    }

    @Test fun `device ack event decodes command and sequence`() {
        val event = RunDeckProtocol.decodeDeviceEvent(byteArrayOf(
            1,
            RunDeckProtocol.DEVICE_EVENT_ACK_TYPE,
            0x2A,
            0x00,
            RunDeckProtocol.COMMAND_RUN_STATE,
            RunDeckProtocol.ACK_OK,
            0,
            0,
        ))

        assertEquals(DeviceEvent.Ack(42, RunDeckProtocol.COMMAND_RUN_STATE, RunDeckProtocol.ACK_OK), event)
    }

    @Test fun `device media control event decodes action and sequence`() {
        val event = RunDeckProtocol.decodeDeviceEvent(byteArrayOf(
            1,
            RunDeckProtocol.DEVICE_EVENT_MEDIA_CONTROL_TYPE,
            0x2B,
            0x00,
            RunDeckProtocol.MEDIA_CONTROL_PLAY_PAUSE,
            0,
            0,
            0,
        ))

        assertEquals(DeviceEvent.MediaControl(43, RunDeckProtocol.MEDIA_CONTROL_PLAY_PAUSE), event)
    }

    @Test fun `device run control event decodes pause`() {
        val event = RunDeckProtocol.decodeDeviceEvent(byteArrayOf(
            1,
            RunDeckProtocol.DEVICE_EVENT_RUN_CONTROL_TYPE,
            0x2C,
            0x00,
            RunDeckProtocol.RUN_CONTROL_PAUSE,
            0,
            0,
            0,
        ))

        assertEquals(DeviceEvent.RunControl(44, RunDeckProtocol.RUN_CONTROL_PAUSE), event)
    }

    @Test fun `device notification dismiss event decodes notification sequence`() {
        val event = RunDeckProtocol.decodeDeviceEvent(byteArrayOf(
            1,
            RunDeckProtocol.DEVICE_EVENT_NOTIFICATION_DISMISSED_TYPE,
            0x2D,
            0x00,
            0,
            0,
            0,
            0,
        ))

        assertEquals(DeviceEvent.NotificationDismissed(45), event)
    }
}
