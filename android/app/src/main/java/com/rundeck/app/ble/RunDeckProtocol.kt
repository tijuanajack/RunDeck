package com.rundeck.app.ble

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.io.ByteArrayOutputStream
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
    const val DEVICE_EVENT_ACK_TYPE: Byte = 0x51
    const val COMMAND_RUN_STATE: Byte = 2
    const val ACK_OK: Byte = 0
    const val HEADER_BYTES = 12
    const val LIVE_METRICS_BYTES = 21
    const val DEVICE_EVENT_ACK_BYTES = 8
    const val MAX_RUN_STATE_BYTES = 128

    private const val RUN_KEY_VERSION = 0
    private const val RUN_KEY_SEQUENCE = 1
    private const val RUN_KEY_ACTIVE = 2
    private const val RUN_KEY_PRESET = 3
    private const val RUN_KEY_TARGET_LABEL = 4
    private const val RUN_KEY_PACE_LOW_SECONDS = 5
    private const val RUN_KEY_PACE_HIGH_SECONDS = 6
    private const val RUN_KEY_HR_LOW = 7
    private const val RUN_KEY_HR_HIGH = 8

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
        frame.putShort(metrics.flags.toShort()).putShort(metrics.paceSecondsPerMile.toShort())
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
            paceSecondsPerMile = input.short.toInt() and 0xFFFF,
            distanceCentimeters = input.int.toLong() and 0xFFFF_FFFFL,
            elapsedSeconds = input.int.toLong() and 0xFFFF_FFFFL,
            movingSeconds = input.int.toLong() and 0xFFFF_FFFFL,
            speedCentimetersPerSecond = input.short.toInt() and 0xFFFF,
            temperatureDeciF = input.short.toInt(),
            forwardedHeartRate = input.get().toInt() and 0xFF,
        )
        return DecodedLiveMetrics(sequence, sourceMs, metrics)
    }

    fun decodeDeviceEvent(frame: ByteArray): DeviceEvent {
        require(frame.size == DEVICE_EVENT_ACK_BYTES) { "Unexpected device event length" }
        val input = ByteBuffer.wrap(frame).order(ByteOrder.LITTLE_ENDIAN)
        require(input.get() == VERSION) { "Incompatible event version" }
        return when (val type = input.get()) {
            DEVICE_EVENT_ACK_TYPE -> {
                val sequence = input.short.toInt() and 0xFFFF
                val commandType = input.get()
                val status = input.get()
                require(input.short.toInt() == 0) { "Unsupported event extension" }
                DeviceEvent.Ack(sequence, commandType, status)
            }
            else -> error("Unknown device event type $type")
        }
    }

    fun encodeRunState(sequence: Int, state: RunStatePacket): ByteArray {
        require(sequence in 0..0xFFFF)
        require(state.presetName.length <= 20)
        require(state.targetLabel.length <= 28)
        val output = ByteArrayOutputStream()
        output.write(0xA9) // fixed CBOR map with 9 integer-keyed entries.
        output.writeUintEntry(RUN_KEY_VERSION, VERSION.toInt())
        output.writeUintEntry(RUN_KEY_SEQUENCE, sequence)
        output.writeBoolEntry(RUN_KEY_ACTIVE, state.active)
        output.writeTextEntry(RUN_KEY_PRESET, state.presetName)
        output.writeTextEntry(RUN_KEY_TARGET_LABEL, state.targetLabel)
        output.writeUintEntry(RUN_KEY_PACE_LOW_SECONDS, state.paceLowSecondsPerMile)
        output.writeUintEntry(RUN_KEY_PACE_HIGH_SECONDS, state.paceHighSecondsPerMile)
        output.writeUintEntry(RUN_KEY_HR_LOW, state.hrLowBpm)
        output.writeUintEntry(RUN_KEY_HR_HIGH, state.hrHighBpm)
        return output.toByteArray().also { require(it.size <= MAX_RUN_STATE_BYTES) }
    }

    fun decodeRunState(payload: ByteArray): DecodedRunStatePacket {
        require(payload.size <= MAX_RUN_STATE_BYTES) { "Run state too large" }
        val input = CborInput(payload)
        val entries = input.readMapEntries()
        var version: Int? = null
        var sequence: Int? = null
        var active: Boolean? = null
        var preset: String? = null
        var target: String? = null
        var paceLow: Int? = null
        var paceHigh: Int? = null
        var hrLow: Int? = null
        var hrHigh: Int? = null
        repeat(entries) {
            when (input.readUInt()) {
                RUN_KEY_VERSION -> version = input.readUInt()
                RUN_KEY_SEQUENCE -> sequence = input.readUInt()
                RUN_KEY_ACTIVE -> active = input.readBool()
                RUN_KEY_PRESET -> preset = input.readText()
                RUN_KEY_TARGET_LABEL -> target = input.readText()
                RUN_KEY_PACE_LOW_SECONDS -> paceLow = input.readUInt()
                RUN_KEY_PACE_HIGH_SECONDS -> paceHigh = input.readUInt()
                RUN_KEY_HR_LOW -> hrLow = input.readUInt()
                RUN_KEY_HR_HIGH -> hrHigh = input.readUInt()
                else -> error("Unknown run-state key")
            }
        }
        require(!input.hasRemaining()) { "Trailing run-state bytes" }
        require(version == VERSION.toInt()) { "Incompatible run-state version" }
        val decoded = RunStatePacket(
            active = requireNotNull(active),
            presetName = requireNotNull(preset),
            targetLabel = requireNotNull(target),
            paceLowSecondsPerMile = requireNotNull(paceLow),
            paceHighSecondsPerMile = requireNotNull(paceHigh),
            hrLowBpm = requireNotNull(hrLow),
            hrHighBpm = requireNotNull(hrHigh),
        )
        return DecodedRunStatePacket(requireNotNull(sequence), decoded)
    }

    private fun ByteArrayOutputStream.writeUintEntry(key: Int, value: Int) {
        writeUInt(key)
        writeUInt(value)
    }

    private fun ByteArrayOutputStream.writeBoolEntry(key: Int, value: Boolean) {
        writeUInt(key)
        write(if (value) 0xF5 else 0xF4)
    }

    private fun ByteArrayOutputStream.writeTextEntry(key: Int, value: String) {
        writeUInt(key)
        val bytes = value.encodeToByteArray()
        require(bytes.size <= 255)
        if (bytes.size <= 23) write(0x60 or bytes.size) else write(0x78).also { write(bytes.size) }
        write(bytes)
    }

    private fun ByteArrayOutputStream.writeUInt(value: Int) {
        require(value >= 0)
        when {
            value <= 23 -> write(value)
            value <= 0xFF -> { write(0x18); write(value) }
            value <= 0xFFFF -> { write(0x19); write(value shr 8); write(value) }
            else -> error("Value too large for RunDeck v1 CBOR")
        }
    }

    private class CborInput(private val bytes: ByteArray) {
        private var offset = 0

        fun hasRemaining(): Boolean = offset < bytes.size

        fun readMapEntries(): Int {
            val first = next()
            require(first in 0xA0..0xB7) { "Expected fixed CBOR map" }
            return first and 0x1F
        }

        fun readUInt(): Int {
            val first = next()
            require(first ushr 5 == 0) { "Expected unsigned integer" }
            return readArgument(first)
        }

        fun readBool(): Boolean = when (next()) {
            0xF4 -> false
            0xF5 -> true
            else -> error("Expected boolean")
        }

        fun readText(): String {
            val first = next()
            require(first ushr 5 == 3) { "Expected text string" }
            val length = readArgument(first)
            require(offset + length <= bytes.size) { "Truncated text string" }
            return bytes.copyOfRange(offset, offset + length).decodeToString().also { offset += length }
        }

        private fun readArgument(first: Int): Int = when (val additional = first and 0x1F) {
            in 0..23 -> additional
            24 -> next()
            25 -> (next() shl 8) or next()
            else -> error("Unsupported CBOR additional info")
        }

        private fun next(): Int {
            require(offset < bytes.size) { "Unexpected end of CBOR" }
            return bytes[offset++].toInt() and 0xFF
        }
    }
}

data class LiveMetrics(
    val flags: Int,
    val paceSecondsPerMile: Int,
    val distanceCentimeters: Long,
    val elapsedSeconds: Long,
    val movingSeconds: Long,
    val speedCentimetersPerSecond: Int,
    val temperatureDeciF: Int,
    val forwardedHeartRate: Int,
)

data class DecodedLiveMetrics(val sequence: Int, val sourceMonotonicMs: Long, val metrics: LiveMetrics)

sealed interface DeviceEvent {
    data class Ack(val acknowledgedSequence: Int, val commandType: Byte, val status: Byte) : DeviceEvent
}

data class RunStatePacket(
    val active: Boolean,
    val presetName: String,
    val targetLabel: String,
    val paceLowSecondsPerMile: Int,
    val paceHighSecondsPerMile: Int,
    val hrLowBpm: Int,
    val hrHighBpm: Int,
)

data class DecodedRunStatePacket(val sequence: Int, val state: RunStatePacket)
