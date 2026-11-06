package app.abot.android

internal class ReconnectBackoff(
    private val initialDelayMs: Long = 1_000,
    private val maximumDelayMs: Long = 15_000,
) {
    private var nextDelayMs = initialDelayMs

    init {
        require(initialDelayMs > 0) { "The initial reconnect delay must be positive" }
        require(maximumDelayMs >= initialDelayMs) { "The maximum reconnect delay is invalid" }
    }

    fun nextDelay(): Long = nextDelayMs.also {
        nextDelayMs = (nextDelayMs * 2).coerceAtMost(maximumDelayMs)
    }

    fun reset() {
        nextDelayMs = initialDelayMs
    }
}
