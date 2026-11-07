package app.abot.android

import android.content.ContentResolver
import android.database.Cursor
import android.net.Uri
import android.provider.OpenableColumns

internal data class SelectedDocument(
    val uri: Uri,
    val name: String,
    val mimeType: String,
    val extension: String?,
    val size: Long,
)

internal object SelectedDocumentReader {
    fun read(resolver: ContentResolver, uris: Iterable<Uri>): List<SelectedDocument> =
        distinctSelectionKeys(uris.map(Uri::toString)).mapNotNull { value ->
            val uri = Uri.parse(value)
            runCatching { readOne(resolver, uri) }.getOrNull()
        }

    private fun readOne(resolver: ContentResolver, uri: Uri): SelectedDocument {
        var displayName: String? = null
        var size: Long? = null
        val cursor: Cursor? = resolver.query(
            uri,
            arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE),
            null,
            null,
            null,
        )
        cursor.use {
            if (it?.moveToFirst() == true) {
                displayName = it.stringOrNull(OpenableColumns.DISPLAY_NAME)
                size = it.longOrNull(OpenableColumns.SIZE)
            }
        }
        val safeName = displayName?.takeIf(String::isNotBlank)
            ?: uri.lastPathSegment?.substringAfterLast('/')?.takeIf(String::isNotBlank)
            ?: "Selected file"
        val mimeType = resolver.getType(uri) ?: "application/octet-stream"
        return SelectedDocument(
            uri = uri,
            name = safeName,
            mimeType = mimeType,
            extension = safeName.substringAfterLast('.', "").takeIf(String::isNotBlank),
            size = size?.coerceAtLeast(0) ?: 0,
        )
    }

    private fun Cursor.stringOrNull(columnName: String): String? {
        val index = getColumnIndex(columnName)
        return if (index >= 0 && !isNull(index)) getString(index) else null
    }

    private fun Cursor.longOrNull(columnName: String): Long? {
        val index = getColumnIndex(columnName)
        return if (index >= 0 && !isNull(index)) getLong(index) else null
    }
}

internal fun distinctSelectionKeys(values: Iterable<String>): List<String> {
    val seen = LinkedHashSet<String>()
    values.forEach { value -> if (value.isNotBlank()) seen += value }
    return seen.toList()
}

internal fun aggregateBatchProgress(
    completedBytes: Long,
    currentFileBytes: Long,
    totalBytes: Long,
): Float = when {
    totalBytes <= 0 -> 0f
    else -> ((completedBytes + currentFileBytes).toDouble() / totalBytes)
        .toFloat()
        .coerceIn(0f, 1f)
}
