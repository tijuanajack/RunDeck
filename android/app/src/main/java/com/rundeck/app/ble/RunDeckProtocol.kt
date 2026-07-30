package com.rundeck.app.ble

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID

object RunDeckProtocol {
    const val VERSION: Byte = 1
    val SERVICE_UUID: UUID = UUID.fromString("7b2e0000-6d1f-4a91-8a5f-6c796a25a000")
    val LIVE_METRICS_UUID: UUID = UUID.fromString("7b2e0001-6d1f-4a91-8a5f-6c796a25a000")
    val RUN_STATE_UUID: UUID = UUID.fromString("7b2e0002-6d1f-4a91-8a5f-6c796a25a000")
    val MEDIA_UUID: UUID = UUID.fromString("7b2e0003-6d1f-4a91-8a5f-6c796a25a000")
    val NOTIFICATION_UUID: UUID = UUID.fromString("7b2e0004-6d1f-4a91-8a5f-6c796a25a000")
    val DEVICE_EVENT_UUID: UUID = UUID.fromString("7b2e0005-6d1f-4a91-8a5f-6c796a25a000")
    val SETTINGS_UUID: UUID = UUID.fromString("7b2e0006-6d1f-4a91-8a5f-6c796a25a000")
    val HEARTBEAT_UUID: UUID = UUID.fromString("7b2e0007-6d1f-4a91-8a5f-6c796a25a000")

    const val LIVE_METRICS_TYPE: Byte = 1
    const val HEADER_BYTES = 12
    const val LIVE_METRICS_BYTES = 21

    fun encodeLiveMetrics(sequence: Int, sourceMonotonicMs: Long, metrics: LiveMetrics): ByteArray {
        require(sequence in 0..0xFFFF)
        require(sourceMonotonicMs in 0..0xFFFF_FFFFL)
        val frame = ByteBuffer.allocate(HEADER_BYTES + LIVE_METRICS_BYTES).order(ByteOrder.LITTLE_ENDIAN)
        frame.put(VERSION).put(LIVE_METRICS_TYPE).putShort(sequence.toShort()).putInt(sourceMonotonicMs.toInt())
        frame.putShort(LIVE_METRICS_BYTES.toShort())
        // Two reserved bytes make the fixed v1 header 12 bytes. Firmware
        // begins the payload at byte 12; keep this explicit to prevent field
        // shifts that can otherwise look like plausible run data.
        frame.putShort(0)
        frame.putShort(metrics.flags.toShort()).putShort(metrics.paceCentisecondsPerMile.toShort())
        frame.putInt(metrics.distanceCentimeters.toInt()).putInt(metrics.elapsedSeconds.toInt())
        frame.putInt(metrics.movingSeconds.toInt()).putShort(metrics.speedCentimetersPerSecond.toShort())
        frame.putShort(metrics.temperatureDeciF.toShort()).put(metrics.forwardedHeartRate.toByte())
        return frame.array()
    }

    fun decodeLiveMetrics(frame: ByteArray): DecodedLiveMetrics {
        require(frame.size == HEADER_BYTES + LIVE_METRICS_BYTES) { "Unexpected live metrics length" }
        val input = ByteBuffer.wrap(frame).order(ByteOrder.LITTLE_ENDIAN)
        require(input.get() == VERSION) { "Incompatible protocol version" }
        require(input.get() == LIVE_METRICS_TYPE) { "Unexpected frame type" }
        val sequence = input.short.toInt() and 0xFFFF
        val sourceMs = input.int.toLong() and 0xFFFF_FFFFL
        require((input.short.toInt() and 0xFFFF) == LIVE_METRICS_BYTES) { "Bad payload length" }
        require(input.short.toInt() == 0) { "Unsupported header extension" }
        val metrics = LiveMetrics(
            flags = input.short.toInt() and 0xFFFF,
            paceCentisecondsPerMile = input.short.toInt() and 0xFFFF,
            distanceCentimeters = input.int.toLong() and 0xFFFF_FFFFL,
            elapsedSeconds = input.int.toLong() and 0xFFFF_FFFFL,
            movingSeconds = input.int.toLong() and 0xFFFF_FFFFL,
            speedCentimetersPerSecond = input.short.toInt() and 0xFFFF,
            temperatureDeciF = input.short.toInt(),
            forwardedHeartRate = input.get().toInt() and 0xFF,
        )
        return DecodedLiveMetrics(sequence, sourceMs, metrics)
    }
}

data class LiveMetrics(
    val flags: Int,
    val paceCentisecondsPerMile: Int,
    val distanceCentimeters: Long,
    val elapsedSeconds: Long,
    val movingSeconds: Long,
    val speedCentimetersPerSecond: Int,
    val temperatureDeciF: Int,
    val forwardedHeartRate: Int,
)

data class DecodedLiveMetrics(val sequence: Int, val sourceMonotonicMs: Long, val metrics: LiveMetrics)
