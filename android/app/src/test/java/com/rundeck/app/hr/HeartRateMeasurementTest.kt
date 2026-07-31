package com.rundeck.app.hr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HeartRateMeasurementTest {
    @Test
    fun decodesUnsignedEightBitMeasurement() {
        assertEquals(143, decodeHeartRateMeasurement(byteArrayOf(0x00, 0x8F.toByte())))
    }

    @Test
    fun decodesLittleEndianSixteenBitMeasurement() {
        assertEquals(180, decodeHeartRateMeasurement(byteArrayOf(0x01, 0xB4.toByte(), 0x00)))
    }

    @Test
    fun rejectsMalformedAndImplausibleMeasurements() {
        assertNull(decodeHeartRateMeasurement(byteArrayOf(0x00)))
        assertNull(decodeHeartRateMeasurement(byteArrayOf(0x01, 0xB4.toByte())))
        assertNull(decodeHeartRateMeasurement(byteArrayOf(0x00, 0x10)))
        assertNull(decodeHeartRateMeasurement(byteArrayOf(0x00, 0xF5.toByte())))
    }
}
