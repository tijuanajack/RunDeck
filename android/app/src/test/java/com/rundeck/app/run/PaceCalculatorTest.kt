package com.rundeck.app.run

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class PaceCalculatorTest {
    @Test fun `computes pace over an accepted window`() {
        val result = PaceCalculator().currentPace(listOf(
            LocationSample(0, 0.0, 5f), LocationSample(10_000, 100.0, 5f)
        ))
        assertFalse(result.gpsWeak)
        assertEquals(160.9344, result.secondsPerMile!!, 0.01)
    }
}
