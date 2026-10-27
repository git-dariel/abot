package app.abot.android

import org.junit.Assert.assertEquals
import org.junit.Test

class TransferStateTest {
    @Test
    fun protocolStateNamesRemainStable() {
        assertEquals("AWAITING_REMOTE_CONFIRMATION", TransferState.AWAITING_REMOTE_CONFIRMATION.name)
        assertEquals(11, TransferState.entries.size)
    }
}
