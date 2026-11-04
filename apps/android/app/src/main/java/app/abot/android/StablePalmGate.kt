package app.abot.android

internal class StablePalmGate {
    companion object {
        const val MIN_CONFIDENCE = 0.55f
        const val HOLD_MS = 350L
        const val MAX_FRAME_GAP_MS = 3_000L
    }

    private var since = -1L
    private var last = -1L
    private var fired = false

    fun observe(name: String?, confidence: Float, hands: Int, timeMs: Long): Boolean {
        if (fired) return false
        if (last >= 0 && (timeMs <= last || timeMs - last > MAX_FRAME_GAP_MS)) reset()
        last = timeMs
        if (hands != 1 || confidence < MIN_CONFIDENCE || name != "Open_Palm") {
            since = -1L
            return false
        }
        if (since < 0) since = timeMs
        if (timeMs - since < HOLD_MS) return false
        fired = true
        return true
    }

    fun reset() {
        since = -1L
        last = -1L
        fired = false
    }
}
