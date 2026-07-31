package com.rundeck.app.run

import org.junit.Assert.assertEquals
import org.junit.Test

class RunTargetTest {
    @Test fun `high heart rate dominates on pace`() {
        assertEquals(CombinedTargetStatus.BackOff, LongRunTarget.combinedStatus(540.0, 165))
    }

    @Test fun `high heart rate and fast pace ease off`() {
        assertEquals(CombinedTargetStatus.EaseOff, LongRunTarget.combinedStatus(520.0, 165))
    }

    @Test fun `missing heart rate preserves pace status`() {
        assertEquals(CombinedTargetStatus.Pace(PaceTargetStatus.PickItUp), LongRunTarget.combinedStatus(600.0, null))
    }
}
