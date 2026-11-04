package app.abot.android

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GestureSequenceTest {
    private fun hold(gate: GestureSequence, pose: String, start: Long, confidence: Float = 0.95f, hands: Int = 1): Boolean {
        var fired = false
        for (time in start..start + 700 step 100) {
            if (gate.observe(pose, confidence, hands, time)) fired = true
        }
        return fired
    }

    @Test fun requiresPalmThenFistAndFiresOnce() {
        val gate = GestureSequence()
        assertFalse(hold(gate, "Closed_Fist", 0))
        assertFalse(hold(gate, "Open_Palm", 800))
        assertTrue(hold(gate, "Closed_Fist", 1600))
        assertFalse(hold(gate, "Closed_Fist", 2400))
    }

    @Test fun rejectsLowConfidenceMultipleHandsAndInterruptions() {
        for ((confidence, hands) in listOf(0.5f to 1, 0.95f to 2)) {
            val gate = GestureSequence()
            hold(gate, "Open_Palm", 0, confidence, hands)
            assertFalse(hold(gate, "Closed_Fist", 800))
        }
        val gate = GestureSequence()
        hold(gate, "Open_Palm", 0)
        assertFalse(hold(gate, "Closed_Fist", 5000))
        gate.reset()
        assertFalse(hold(gate, "Closed_Fist", 6000))
    }

    @Test fun toleratesSlowOnDeviceInferenceFrames() {
        val gate = GestureSequence()
        assertFalse(gate.observe("Open_Palm", 0.75f, 1, 0))
        assertFalse(gate.observe("Open_Palm", 0.75f, 1, 1_200))
        assertFalse(gate.observe("Closed_Fist", 0.75f, 1, 2_400))
        assertTrue(gate.observe("Closed_Fist", 0.75f, 1, 3_600))
    }
}
