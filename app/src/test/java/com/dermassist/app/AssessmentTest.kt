package com.dermassist.app

import org.junit.Assert.*
import org.junit.Test

class AssessmentTest {
    private val complete = Symptoms("Arms or hands", "1–2 weeks", "A little", "No", "Not sure")

    @Test fun `every symptom answer is required including uncertainty`() {
        assertTrue(complete.complete)
        listOf(
            complete.copy(location = ""), complete.copy(duration = ""),
            complete.copy(itching = ""), complete.copy(pain = ""), complete.copy(spread = " "),
        ).forEach { assertFalse(it.complete) }
    }

    @Test fun `resolution rejects a narrow image even when its other edge is large`() {
        assertTrue(PhotoRules.tooSmall(319, 4000))
        assertTrue(PhotoRules.tooSmall(4000, 319))
        assertFalse(PhotoRules.tooSmall(320, 320))
    }

    @Test fun `shared record always carries unavailable screening and no diagnosis`() {
        val report = Assessment("record", 0L, complete).report()
        assertTrue(report.contains("Unable to assess"))
        assertTrue(report.contains("No model is installed"))
        assertTrue(report.contains("no diagnosis"))
        assertTrue(report.contains("No photo is included"))
        assertTrue(report.contains("Arms or hands"))
        assertFalse(report.contains("file://"))
        assertFalse(report.contains("content://"))
    }
}
