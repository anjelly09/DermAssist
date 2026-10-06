package com.dermassist.app

import org.junit.Assert.*
import org.junit.Test

class ScreeningTest {
    @Test fun `stable softmax handles very large logits`() {
        val probabilities = ScreeningMath.probabilities(floatArrayOf(10000f, 10001f, 10002f), 2.0)
        assertEquals(1.0, probabilities.sum(), 1e-9)
        assertTrue(probabilities[2] > probabilities[1])
        assertTrue(probabilities.all { it.isFinite() })
    }
    @Test fun `non-finite scores and disabled policy fail closed`() {
        assertFalse(ScreeningMath.accepts(Double.NaN, .1, .5, .2, true))
        assertFalse(ScreeningMath.accepts(.99, .01, .5, .2, false))
        assertFalse(ScreeningMath.accepts(.99, .3, .5, .2, true))
        assertTrue(ScreeningMath.accepts(.7, .1, .7, .1, true))
    }
    @Test fun `cosine distance is independent of embedding magnitude`() {
        assertEquals(0.0, ScreeningMath.distance(floatArrayOf(2f, 0f), listOf(doubleArrayOf(7.0, 0.0))), 1e-9)
        assertEquals(1.0, ScreeningMath.distance(floatArrayOf(0f, 2f), listOf(doubleArrayOf(7.0, 0.0))), 1e-9)
    }
    @Test fun `extreme exposure and flat images are rejected`() {
        assertNotNull(ImageQuality.rejection(IntArray(100) { 0xff000000.toInt() }, 10, 10, 8.0, 247.0, 5.0))
        assertNotNull(ImageQuality.rejection(IntArray(100) { 0xffffffff.toInt() }, 10, 10, 8.0, 247.0, 5.0))
        assertNotNull(ImageQuality.rejection(IntArray(100) { 0xff888888.toInt() }, 10, 10, 8.0, 247.0, 5.0))
    }
    @Test fun `saved and shared suggestions retain their experimental limitation`() {
        val result = ScreeningResult(ScreeningStatus.EXPERIMENTAL_MATCH, "Eczema", "Visual pattern only", "test-model")
        val record = Assessment("id", 0, Symptoms(), result).report()
        assertTrue(record.contains("Eczema"))
        assertTrue(record.contains("not a diagnosis"))
        assertTrue(record.contains("test-model"))
        assertFalse(record.contains("probability"))
    }
}
