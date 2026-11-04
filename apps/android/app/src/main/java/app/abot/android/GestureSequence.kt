package app.abot.android

/** One deliberate, stable two-pose sequence per enabled camera session. */
internal class GestureSequence {
    var armed = false
        private set
    private var fired = false
    private var pose: String? = null
    private var since = 0L
    private var last = -1L
    private var armedAt = 0L

    fun observe(name: String?, confidence: Float, hands: Int, timeMs: Long): Boolean {
        if (fired) return false
        if (last >= 0 && (timeMs <= last || timeMs - last > 1_000)) reset()
        last = timeMs
        if (armed && timeMs - armedAt > 8_000) reset()
        if (hands != 1 || confidence < 0.8f || name !in setOf("Open_Palm", "Closed_Fist")) {
            pose = null
            return false
        }
        if (pose != name) {
            pose = name
            since = timeMs
        }
        if (timeMs - since < 600) return false
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
