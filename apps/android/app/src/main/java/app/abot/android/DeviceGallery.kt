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

internal data class GalleryPhoto(val uri: Uri, val name: String)

internal object DeviceGallery {
    fun load(context: Context): List<GalleryPhoto> {
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
        )
        val photos = mutableListOf<GalleryPhoto>()
        context.contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            projection,
            null,
            null,
            "${MediaStore.Images.Media.DATE_ADDED} DESC",
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
            while (cursor.moveToNext()) {
                photos += GalleryPhoto(
                    uri = ContentUris.withAppendedId(
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                        cursor.getLong(idColumn),
                    ),
                    name = cursor.getString(nameColumn) ?: "Photo",
                )
            }
        }
        return photos
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
    onOpenPhoto: (Uri) -> Unit,
) {
    val context = LocalContext.current
    var photos by remember(refreshKey) { mutableStateOf<List<GalleryPhoto>>(emptyList()) }
    var loading by remember(refreshKey) { mutableStateOf(hasPhotoAccess) }
    var loadError by remember(refreshKey) { mutableStateOf<String?>(null) }

    LaunchedEffect(hasPhotoAccess, refreshKey) {
        if (!hasPhotoAccess) {
            photos = emptyList()
            loading = false
            return@LaunchedEffect
        }
        loading = true
        loadError = null
        runCatching { withContext(Dispatchers.IO) { DeviceGallery.load(context) } }
            .onSuccess { photos = it }
            .onFailure { loadError = "ABOT could not read the photos allowed on this phone." }
        loading = false
    }

    Column(modifier = Modifier.fillMaxSize().systemBarsPadding()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedButton(
                onClick = onBack,
                enabled = !isBusy,
                shape = androidx.compose.foundation.shape.RoundedCornerShape(2.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, AbotInk),
            ) { Text("← Back") }
            Column(modifier = Modifier.padding(start = 14.dp).weight(1f)) {
                AbotSectionLabel("MediaStore photos")
                Text("Gallery.", style = MaterialTheme.typography.headlineLarge)
            }
            AbotStatusPill(
                label = if (receiverReady) "PC ready" else if (isDiscovering) "Finding" else "Offline",
                connected = receiverReady,
            )
        }
        Surface(color = if (receiverReady) AbotInk else AbotSoft, modifier = Modifier.fillMaxWidth()) {
            Row(Modifier.padding(horizontal = 18.dp, vertical = 13.dp), verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = androidx.compose.foundation.shape.CircleShape, color = if (receiverReady) AbotBlue else Color(0xFFAAAAAA)) {
                    Text(" ", fontSize = 5.sp, modifier = Modifier.padding(3.dp))
                }
                Column(Modifier.padding(start = 10.dp)) {
                    Text(
                        if (receiverReady) "Open a photo, then grab it." else statusMessage,
                        color = if (receiverReady) AbotPaper else AbotInk,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                    )
                    if (receiverReady) Text("Palm → fist sends the encrypted offer.", color = Color(0xFFBDBDBD), fontSize = 11.sp)
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
                    Text("Photos stay private.", style = MaterialTheme.typography.headlineMedium)
                    Text("Allow access only to the photos you want available inside ABOT.", color = AbotMuted)
                    Button(onClick = onRequestAccess, colors = abotBlueButtonColors(), shape = androidx.compose.foundation.shape.RoundedCornerShape(2.dp)) {
                        Text("Allow photo access", fontWeight = FontWeight.Bold)
                    }
                }
            }
            loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = AbotBlue)
            }
            loadError != null -> Text(loadError!!, color = AbotDanger, modifier = Modifier.padding(20.dp))
            photos.isEmpty() -> Column(Modifier.fillMaxWidth().padding(40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("No photos yet.", style = MaterialTheme.typography.headlineMedium)
                Text("Allow more photos from Android settings.", color = AbotMuted, modifier = Modifier.padding(top = 8.dp))
            }
            else -> LazyVerticalGrid(
                columns = GridCells.Adaptive(118.dp),
                modifier = Modifier.fillMaxSize().padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                items(photos, key = { it.uri.toString() }) { photo ->
                    GalleryThumbnail(
                        photo = photo,
                        enabled = receiverReady && !isBusy,
                        onClick = { onOpenPhoto(photo.uri) },
                    )
                }
            }
        }
    }
}

@Composable
private fun GalleryThumbnail(photo: GalleryPhoto, enabled: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(132.dp)
            .background(AbotSoft)
            .clickable(enabled = enabled, onClick = onClick),
    ) {
        MediaStoreImage(
            uri = photo.uri,
            requestedSize = 320,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
            contentDescription = photo.name,
        )
        if (!enabled) Box(Modifier.fillMaxSize().background(AbotPaper.copy(alpha = 0.62f)))
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
