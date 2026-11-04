package app.abot.android

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StablePalmGateTest {
    @Test fun acceptsOneStablePalmOnce() {
        val gate = StablePalmGate()
        assertFalse(gate.observe("Open_Palm", 0.9f, 1, 0))
        assertFalse(gate.observe("Open_Palm", 0.9f, 1, 300))
        assertTrue(gate.observe("Open_Palm", 0.9f, 1, 400))
        assertFalse(gate.observe("Open_Palm", 0.9f, 1, 800))
    }

    @Test fun rejectsLowConfidenceMultipleHandsAndInterruptedPalm() {
        val gate = StablePalmGate()
        assertFalse(gate.observe("Open_Palm", 0.5f, 1, 0))
        assertFalse(gate.observe("Open_Palm", 0.9f, 2, 500))
        assertFalse(gate.observe("Open_Palm", 0.9f, 1, 1_000))
        assertFalse(gate.observe("Closed_Fist", 0.9f, 1, 1_200))
        assertFalse(gate.observe("Open_Palm", 0.9f, 1, 1_300))
        assertTrue(gate.observe("Open_Palm", 0.9f, 1, 1_700))
    }

    @Test fun resetsAfterSlowFrameGap() {
        val gate = StablePalmGate()
        assertFalse(gate.observe("Open_Palm", 0.9f, 1, 0))
        assertFalse(gate.observe("Open_Palm", 0.9f, 1, 4_000))
        assertTrue(gate.observe("Open_Palm", 0.9f, 1, 4_400))
    }
}
