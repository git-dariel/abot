package app.abot.android

/** One deliberate, stable two-pose sequence per enabled camera session. */
internal class GestureSequence {
    companion object {
        const val MIN_CONFIDENCE = 0.55f
        const val HOLD_MS = 350L
        const val MAX_FRAME_GAP_MS = 3_000L
        const val SEQUENCE_TIMEOUT_MS = 12_000L
    }

    var armed = false
        private set
    private var fired = false
    private var pose: String? = null
    private var since = 0L
    private var last = -1L
    private var armedAt = 0L

    fun observe(name: String?, confidence: Float, hands: Int, timeMs: Long): Boolean {
        if (fired) return false
        if (last >= 0 && (timeMs <= last || timeMs - last > MAX_FRAME_GAP_MS)) reset()
        last = timeMs
        if (armed && timeMs - armedAt > SEQUENCE_TIMEOUT_MS) reset()
        if (hands != 1 || confidence < MIN_CONFIDENCE || name !in setOf("Open_Palm", "Closed_Fist")) {
            pose = null
            return false
        }
        if (pose != name) {
            pose = name
            since = timeMs
        }
        if (timeMs - since < HOLD_MS) return false
        if (!armed && name == "Open_Palm") {
            armed = true
            armedAt = timeMs
            pose = null
        } else if (armed && name == "Closed_Fist") {
            fired = true
            return true
        }
        return false
    }

    fun reset() {
        armed = false
        fired = false
        pose = null
        last = -1
    }
}
