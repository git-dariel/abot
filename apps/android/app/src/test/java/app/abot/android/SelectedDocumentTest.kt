package app.abot.android

import org.junit.Assert.assertEquals
import org.junit.Test

class SelectedDocumentTest {
    @Test
    fun `selection keys retain order and remove duplicates`() {
        assertEquals(
            listOf("content://one", "content://two"),
            distinctSelectionKeys(listOf("content://one", "content://one", "", "content://two")),
        )
    }

    @Test
    fun `batch progress includes completed and current file bytes`() {
        assertEquals(0.5f, aggregateBatchProgress(100, 50, 300), 0.0001f)
        assertEquals(1f, aggregateBatchProgress(300, 100, 300), 0.0001f)
        assertEquals(0f, aggregateBatchProgress(0, 0, 0), 0.0001f)
    }
}
