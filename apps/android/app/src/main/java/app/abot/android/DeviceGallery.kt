package app.abot.android

import android.content.ContentUris
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Size
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal data class GalleryMedia(
    val uri: Uri,
    val name: String,
    val mimeType: String,
    val size: Long,
    val isVideo: Boolean,
)

internal object DeviceGallery {
    fun load(context: Context): List<GalleryMedia> {
        val projection = arrayOf(
            MediaStore.Files.FileColumns._ID,
            MediaStore.Files.FileColumns.DISPLAY_NAME,
            MediaStore.Files.FileColumns.MIME_TYPE,
            MediaStore.Files.FileColumns.SIZE,
            MediaStore.Files.FileColumns.MEDIA_TYPE,
        )
        val media = mutableListOf<GalleryMedia>()
        context.contentResolver.query(
            MediaStore.Files.getContentUri("external"),
            projection,
            "${MediaStore.Files.FileColumns.MEDIA_TYPE}=? OR ${MediaStore.Files.FileColumns.MEDIA_TYPE}=?",
            arrayOf(
                MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE.toString(),
                MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO.toString(),
            ),
            "${MediaStore.Files.FileColumns.DATE_ADDED} DESC",
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)
            val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DISPLAY_NAME)
            val mimeColumn = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.MIME_TYPE)
            val sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.SIZE)
            val mediaTypeColumn = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.MEDIA_TYPE)
            while (cursor.moveToNext()) {
                val mediaType = cursor.getInt(mediaTypeColumn)
                media += GalleryMedia(
                    uri = ContentUris.withAppendedId(
                        MediaStore.Files.getContentUri("external"),
                        cursor.getLong(idColumn),
                    ),
                    name = cursor.getString(nameColumn)
                        ?: if (mediaType == MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO) "Video" else "Photo",
                    mimeType = cursor.getString(mimeColumn)
                        ?: if (mediaType == MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO) "video/*" else "image/*",
                    size = cursor.getLong(sizeColumn).coerceAtLeast(0),
                    isVideo = mediaType == MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO,
                )
            }
        }
        return media
    }
}

@Composable
internal fun AbotGalleryScreen(
    hasPhotoAccess: Boolean,
    receiverReady: Boolean,
    isDiscovering: Boolean,
    isBusy: Boolean,
    statusMessage: String,
    refreshKey: Int,
    onBack: () -> Unit,
    onRequestAccess: () -> Unit,
    onConfirmSelection: (List<SelectedDocument>) -> Unit,
) {
    val context = LocalContext.current
    var media by remember(refreshKey) { mutableStateOf<List<GalleryMedia>>(emptyList()) }
    var selectedKeys by remember(refreshKey) { mutableStateOf<Set<String>>(emptySet()) }
    var loading by remember(refreshKey) { mutableStateOf(hasPhotoAccess) }
    var loadError by remember(refreshKey) { mutableStateOf<String?>(null) }

    LaunchedEffect(hasPhotoAccess, refreshKey) {
        if (!hasPhotoAccess) {
            media = emptyList()
            selectedKeys = emptySet()
            loading = false
            return@LaunchedEffect
        }
        loading = true
        loadError = null
        runCatching { withContext(Dispatchers.IO) { DeviceGallery.load(context) } }
            .onSuccess {
                media = it
                val available = it.mapTo(HashSet()) { item -> item.uri.toString() }
                selectedKeys = selectedKeys.filterTo(LinkedHashSet()) { key -> key in available }
            }
            .onFailure { loadError = "ABOT could not read the photos and videos allowed on this phone." }
        loading = false
    }

    Column(modifier = Modifier.fillMaxSize().background(AbotPaper).systemBarsPadding()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedButton(
                onClick = onBack,
                enabled = !isBusy,
                shape = AbotButtonShape,
                border = androidx.compose.foundation.BorderStroke(1.dp, AbotLine),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = AbotNavy),
            ) { Text("← Back") }
            Column(modifier = Modifier.padding(start = 14.dp).weight(1f)) {
                AbotSectionLabel("Photos & videos")
                Text("Gallery", style = MaterialTheme.typography.headlineMedium)
            }
            AbotStatusPill(
                label = if (receiverReady) "PC ready" else if (isDiscovering) "Finding" else "Offline",
                connected = receiverReady,
            )
        }
        Surface(
            color = if (receiverReady) AbotNavy else AbotSoft,
            shape = AbotCardShape,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 6.dp),
        ) {
            Row(Modifier.padding(horizontal = 18.dp, vertical = 13.dp), verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = androidx.compose.foundation.shape.CircleShape, color = if (receiverReady) AbotBlue else Color(0xFFAAAAAA)) {
                    Text(" ", fontSize = 5.sp, modifier = Modifier.padding(3.dp))
                }
                Column(Modifier.padding(start = 10.dp)) {
                    Text(
                        if (receiverReady) "Select one or more items to grab." else statusMessage,
                        color = if (receiverReady) AbotPaper else AbotInk,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                    )
                    if (receiverReady) {
                        Text("Each item stays private until Windows accepts it.", color = AbotPaper.copy(alpha = .68f), fontSize = 11.sp)
                    }
                }
            }
        }
        when {
            !hasPhotoAccess -> {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 28.dp, vertical = 64.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Text("Media stays private.", style = MaterialTheme.typography.headlineMedium)
                    Text("Allow access only to the photos and videos you want available inside ABOT.", color = AbotMuted)
                    Button(
                        onClick = onRequestAccess,
                        colors = abotBlueButtonColors(),
                        shape = AbotButtonShape,
                    ) { Text("Allow media access", fontWeight = FontWeight.Bold) }
                }
            }
            loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = AbotBlue)
            }
            loadError != null -> Text(loadError!!, color = AbotDanger, modifier = Modifier.padding(20.dp))
            media.isEmpty() -> Column(Modifier.fillMaxWidth().padding(40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("No photos or videos yet.", style = MaterialTheme.typography.headlineMedium)
                Text("Allow more media from Android settings.", color = AbotMuted, modifier = Modifier.padding(top = 8.dp))
            }
            else -> Column(Modifier.fillMaxSize()) {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(118.dp),
                    modifier = Modifier.weight(1f).padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(media, key = { it.uri.toString() }) { item ->
                        val key = item.uri.toString()
                        GalleryThumbnail(
                            media = item,
                            selected = key in selectedKeys,
                            enabled = !isBusy,
                            onClick = {
                                selectedKeys = selectedKeys.toMutableSet().apply {
                                    if (!add(key)) remove(key)
                                }
                            },
                        )
                    }
                }
                Surface(color = AbotPaper, shadowElevation = 10.dp) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("${selectedKeys.size} selected", fontWeight = FontWeight.Bold)
                            Text("Photos and videos can be mixed.", color = AbotMuted, fontSize = 11.sp)
                        }
                        Button(
                            onClick = {
                                onConfirmSelection(media.filter { it.uri.toString() in selectedKeys }.map {
                                    SelectedDocument(
                                        uri = it.uri,
                                        name = it.name,
                                        mimeType = it.mimeType,
                                        extension = it.name.substringAfterLast('.', "").takeIf(String::isNotBlank),
                                        size = it.size,
                                    )
                                })
                            },
                            enabled = selectedKeys.isNotEmpty() && receiverReady && !isBusy,
                            colors = abotPrimaryButtonColors(),
                            shape = AbotButtonShape,
                        ) { Text("Continue") }
                    }
                }
            }
        }
    }
}

@Composable
private fun GalleryThumbnail(media: GalleryMedia, selected: Boolean, enabled: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(128.dp)
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(18.dp))
            .background(AbotSoft)
            .clickable(enabled = enabled, onClick = onClick),
    ) {
        MediaStoreImage(
            uri = media.uri,
            requestedSize = 320,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
            contentDescription = media.name,
        )
        if (!enabled) Box(Modifier.fillMaxSize().background(AbotPaper.copy(alpha = 0.62f)))
        if (media.isVideo) {
            Surface(color = AbotNavy, shape = androidx.compose.foundation.shape.RoundedCornerShape(50), modifier = Modifier.align(Alignment.BottomStart).padding(7.dp)) {
                Text("VIDEO", color = AbotPaper, fontSize = 9.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp))
            }
        }
        if (selected) {
            Box(Modifier.fillMaxSize().background(AbotBlue.copy(alpha = 0.28f)))
            Surface(color = AbotSky, shape = androidx.compose.foundation.shape.CircleShape, modifier = Modifier.align(Alignment.TopEnd).padding(7.dp)) {
                Text("✓", color = AbotInk, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
            }
        }
    }
}

@Composable
internal fun MediaStoreImage(
    uri: Uri,
    requestedSize: Int,
    contentScale: ContentScale,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
) {
    val resolver = LocalContext.current.contentResolver
    var bitmap by remember(uri, requestedSize) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(uri, requestedSize) {
        bitmap = withContext(Dispatchers.IO) {
            loadMediaBitmap(resolver = resolver, uri = uri, requestedSize = requestedSize)?.asImageBitmap()
        }
    }
    bitmap?.let {
        Image(
            bitmap = it,
            contentDescription = contentDescription,
            contentScale = contentScale,
            modifier = modifier,
        )
    }
}

private fun loadMediaBitmap(
    resolver: android.content.ContentResolver,
    uri: Uri,
    requestedSize: Int,
): Bitmap? = runCatching {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        resolver.loadThumbnail(uri, Size(requestedSize, requestedSize), null)
    } else {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (bounds.outWidth / sample > requestedSize * 2 || bounds.outHeight / sample > requestedSize * 2) {
            sample *= 2
        }
        val options = BitmapFactory.Options().apply { inSampleSize = sample.coerceAtLeast(1) }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
    }
}.getOrNull()
