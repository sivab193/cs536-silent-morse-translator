package edu.purdue.cs536.morse

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LightSignalDetectorTest {
    @Test fun calibratesAndClassifiesBrightness() {
        val detector = LightSignalDetector()
        detector.begin(0)
        repeat(20) { assertFalse(detector.update(30.0, it * 100L)) }
        assertFalse(detector.update(30.0, 2_000L))
        repeat(20) { assertFalse(detector.update(210.0, 2_100L + it * 100L)) }
        assertTrue(detector.update(210.0, 4_100L))
        assertEquals(120.0, detector.threshold, 1.0)
        assertTrue(detector.update(220.0, 4_200L))
        assertFalse(detector.update(25.0, 4_300L))
    }

    @Test fun closeCalibrationKeepsPreviousThreshold() {
        val detector = LightSignalDetector()
        detector.begin(0)
        detector.update(100.0, 2_000L)
        detector.update(110.0, 4_000L)
        assertEquals(120.0, detector.threshold, 0.0)
        assertTrue(detector.warning?.startsWith("Calibration too close") == true)
    }
}
