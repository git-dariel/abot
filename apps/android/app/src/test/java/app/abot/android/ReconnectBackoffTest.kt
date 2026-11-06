package app.abot.android

import org.junit.Assert.assertEquals
import org.junit.Test

class ReconnectBackoffTest {
    @Test
    fun `reconnect delay increases and remains capped`() {
        val backoff = ReconnectBackoff(initialDelayMs = 1_000, maximumDelayMs = 5_000)

        assertEquals(1_000, backoff.nextDelay())
        assertEquals(2_000, backoff.nextDelay())
        assertEquals(4_000, backoff.nextDelay())
        assertEquals(5_000, backoff.nextDelay())
        assertEquals(5_000, backoff.nextDelay())
    }

    @Test
    fun `successful connection resets reconnect delay`() {
        val backoff = ReconnectBackoff()
        backoff.nextDelay()
        backoff.nextDelay()

        backoff.reset()

        assertEquals(1_000, backoff.nextDelay())
    }
}
