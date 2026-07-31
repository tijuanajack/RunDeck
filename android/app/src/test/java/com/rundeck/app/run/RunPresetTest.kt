package com.rundeck.app.run

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RunPresetTest {
    @Test
    fun catalogDefaultsToLongRunAndKeepsDistinctTargets() {
        assertEquals("long_run", RunPresetCatalog.longRun.id)
        assertEquals("8:50-9:20 / HR < 150", RunPresetCatalog.longRun.targetLabel)
        assertTrue(RunPresetCatalog.easy.targetLabel != RunPresetCatalog.steady.targetLabel)
        assertTrue(RunPresetCatalog.all.all { it.targetLabel.length <= 28 })
    }

    @Test
    fun selectedPresetUsesItsOwnPaceAndHeartRateRules() {
        val steady = RunPresetCatalog.steady
        assertEquals(PaceTargetStatus.OnTarget, steady.paceStatus(8 * 60.0 + 52.0))
        assertEquals(HeartRateTargetStatus.High, steady.heartRateStatus(151))
        assertEquals(CombinedTargetStatus.BackOff, steady.combinedStatus(8 * 60.0 + 52.0, 151))
    }
}
