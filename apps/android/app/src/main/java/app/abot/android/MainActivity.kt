package app.abot.android

import android.Manifest
import android.content.Context
import android.net.Uri
import android.content.Intent
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions

private val AbotColorScheme = lightColorScheme(
    primary = Color.Black,
    onPrimary = Color.White,
    background = Color.White,
    onBackground = Color(0xFF111111),
    surface = Color.White,
    onSurface = Color(0xFF111111),
    surfaceVariant = Color(0xFFF4F4F4),
    onSurfaceVariant = Color(0xFF444444),
    outline = Color(0xFFBDBDBD),
    error = Color(0xFF9F1C1C),
    onError = Color.White,
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { AbotApp() }
    }
}

@Composable
private fun AbotApp() {
    val context = LocalContext.current
    val mainHandler = remember { Handler(Looper.getMainLooper()) }
    val trustedDevices = remember { TrustedDeviceStore(context) }
    val trustedDiscovery = remember { TrustedDeviceDiscovery(context, trustedDevices) }
    var selectedUri by rememberSaveable { mutableStateOf<Uri?>(null) }
    var galleryOpen by rememberSaveable { mutableStateOf(false) }
    var galleryRefreshKey by rememberSaveable { mutableStateOf(0) }
    var galleryDiscoveryAttempted by rememberSaveable { mutableStateOf(false) }
    var discoveryRetryToken by rememberSaveable { mutableStateOf(0) }
    var showAdvanced by rememberSaveable { mutableStateOf(false) }
    var hasPhotoAccess by remember { mutableStateOf(hasGalleryPhotoAccess(context)) }
    var pairingLink by rememberSaveable { mutableStateOf("") }
    var verifiedPairingLink by rememberSaveable { mutableStateOf<String?>(null) }
    val verifiedPairing = remember(verifiedPairingLink) {
        verifiedPairingLink?.let { link ->
            runCatching { TrustedReconnectProtocol.resolve(link, trustedDevices) }.getOrNull()
        }
    }
    var pairingTrusted by rememberSaveable { mutableStateOf(false) }
    var showManualLink by rememberSaveable { mutableStateOf(false) }
    var isDiscovering by remember { mutableStateOf(false) }
    var trustedReconnectReady by remember {
        mutableStateOf(trustedDevices.hasProvisionedReconnectCredential())
    }
    var transferState by remember { mutableStateOf(TransferState.IDLE) }
    var statusMessage by rememberSaveable {
        mutableStateOf(if (trustedReconnectReady) "Finding your trusted PC..." else "Scan once to pair this phone.")
    }
    var progress by remember { mutableFloatStateOf(0f) }
    val isBusy = transferState in setOf(
        TransferState.TRANSFER_OFFERED,
        TransferState.AWAITING_REMOTE_CONFIRMATION,
        TransferState.ACCEPTED,
        TransferState.TRANSFERRING,
        TransferState.VERIFYING,
    )

    DisposableEffect(trustedDiscovery) {
        onDispose { trustedDiscovery.stop() }
    }

    fun applyPairing(pairing: VerifiedPairing, link: String) {
        pairingLink = link
        verifiedPairingLink = link
        pairingTrusted = trustedDevices.isTrusted(pairing)
        showManualLink = false
        statusMessage = if (pairing.isLegacy) {
            "Legacy link detected. It has no verified receiver identity."
        } else {
            "QR verified. Confirm that the six-digit code matches Windows."
        }
        if (transferState == TransferState.FAILED) transferState = TransferState.IDLE
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            selectedUri = uri
            transferState = TransferState.FILE_SELECTED
            statusMessage = "File selected. Pair with Windows, then offer the file."
            progress = 0f
        }
    }
    val galleryPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        hasPhotoAccess = hasGalleryPhotoAccess(context)
        if (!hasPhotoAccess) statusMessage = "Photo access is required to show ABOT Gallery."
    }
    val scanner = rememberLauncherForActivityResult(ScanContract()) { result ->
        result.contents?.let { scanned ->
            try {
                applyPairing(PairingInvitationVerifier.verify(scanned), scanned)
            } catch (error: Exception) {
                verifiedPairingLink = null
                pairingTrusted = false
                transferState = TransferState.FAILED
                statusMessage = error.message ?: "The pairing QR code is invalid."
            }
        }
    }

    fun verifyPairingLink() {
        try {
            applyPairing(PairingInvitationVerifier.parse(pairingLink), pairingLink)
        } catch (error: Exception) {
            verifiedPairingLink = null
            pairingTrusted = false
            transferState = TransferState.FAILED
            statusMessage = error.message ?: "The pairing link is invalid."
        }
    }

    fun updateState(state: TransferState, message: String, value: Float? = null) {
        mainHandler.post {
            transferState = state
            statusMessage = message
            if (value != null) progress = value
        }
    }

    fun discoverTrustedPc(foundMessage: String = "Trusted Windows receiver ready. Open a photo to continue.") {
        if (isDiscovering) return
        isDiscovering = true
        statusMessage = "Looking for a previously trusted Windows receiver..."
        trustedDiscovery.discover(
            onFound = { discovered ->
                isDiscovering = false
                applyPairing(discovered.pairing, discovered.restorablePairingLink)
                pairingTrusted = true
                statusMessage = foundMessage
            },
            onFinished = { message ->
                isDiscovering = false
                statusMessage = message
                if (trustedReconnectReady) {
                    mainHandler.postDelayed({
                        galleryDiscoveryAttempted = false
                        discoveryRetryToken += 1
                    }, 3_000)
                }
            },
        )
    }

    fun sendFile() {
        if (isBusy) return
        val document = selectedUri ?: return
        val trustedPairing = verifiedPairing?.takeIf { pairingTrusted } ?: return
        if (!trustedPairing.isLegacy && System.currentTimeMillis() > trustedPairing.expiresAtUnixMs) {
            statusMessage = "This receiver session expired. Find the PC again or scan a new QR."
            return
        }
        transferState = TransferState.TRANSFER_OFFERED
        statusMessage = "Hashing the file and preparing an encrypted offer..."
        progress = 0f
        Thread {
            try {
                val pairing = TrustedReconnectProtocol.prepareQrTransfer(trustedPairing, trustedDevices)
                val result = EncryptedTransferClient.send(
                    resolver = context.contentResolver,
                    document = document,
                    pairing = pairing,
                    onOfferSent = {
                        updateState(
                            TransferState.AWAITING_REMOTE_CONFIRMATION,
                            "Offer reached Windows. Hold a fist at the PC camera, then open your palm. No file bytes have been sent yet.",
                        )
                        mainHandler.post {
                            Toast.makeText(
                                context,
                                "Windows is ready: hold a fist at the PC, then open your palm.",
                                Toast.LENGTH_LONG,
                            ).show()
                        }
                    },
                    onAccepted = {
                        updateState(TransferState.ACCEPTED, "Accepted. Starting encrypted upload...")
                    },
                    onProgress = { sent, total ->
                        val value = if (total == 0L) 1f else (sent.toDouble() / total).toFloat()
                        updateState(
                            TransferState.TRANSFERRING,
                            "Sending ${formatBytes(sent)} of ${formatBytes(total)}...",
                            value,
                        )
                    },
                )
                if (pairing.reconnectSecretHex != null && result.reconnectProvisioned) {
                    trustedDevices.markReconnectProvisioned(pairing)
                    mainHandler.post { trustedReconnectReady = true }
                }
                updateState(
                    TransferState.COMPLETED,
                    "${result.fileName} was received, verified, and saved on Windows.",
                    1f,
                )
                mainHandler.postDelayed({
                    selectedUri = null
                    verifiedPairingLink = null
                    pairingTrusted = false
                    galleryDiscoveryAttempted = false
                    discoveryRetryToken += 1
                    transferState = TransferState.IDLE
                    statusMessage = "Ready for another photo. Reconnecting automatically..."
                }, 1_500)
            } catch (error: Exception) {
                val detail = when (error) {
                    is IllegalArgumentException, is IllegalStateException -> error.message
                    else -> null
                }
                updateState(
                    TransferState.FAILED,
                    detail ?: "Transfer failed. Confirm both devices are on the same local network and try again.",
                )
                mainHandler.post {
                    Toast.makeText(context, detail ?: "Could not reach Windows.", Toast.LENGTH_LONG).show()
                }
            }
        }.start()
    }

    val gestureLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            Toast.makeText(context, "Photo grabbed. Preparing the Windows offer...", Toast.LENGTH_SHORT).show()
            sendFile()
        }
    }

    fun openGestureFor(document: Uri) {
        if (isBusy) return
        selectedUri = document
        transferState = TransferState.FILE_SELECTED
        statusMessage = "Photo ready. Hold an open palm, then close your fist to offer it."
        progress = 0f
        gestureLauncher.launch(
            Intent(context, GestureCaptureActivity::class.java)
                .putExtra(GestureCaptureActivity.EXTRA_MEDIA_URI, document.toString()),
        )
    }

    val receiverReady = verifiedPairing != null && pairingTrusted
    LaunchedEffect(receiverReady, trustedReconnectReady, discoveryRetryToken) {
        if (!receiverReady && trustedReconnectReady && !galleryDiscoveryAttempted && !isBusy) {
            galleryDiscoveryAttempted = true
            discoverTrustedPc()
        }
    }

    MaterialTheme(colorScheme = AbotColorScheme) {
        Surface(modifier = Modifier.fillMaxSize()) {
            if (galleryOpen) {
                AbotGalleryScreen(
                    hasPhotoAccess = hasPhotoAccess,
                    receiverReady = receiverReady,
                    isDiscovering = isDiscovering,
                    isBusy = isBusy,
                    statusMessage = statusMessage,
                    refreshKey = galleryRefreshKey,
                    onBack = { galleryOpen = false },
                    onRequestAccess = { galleryPermission.launch(galleryPermissions()) },
                    onOpenPhoto = ::openGestureFor,
                )
            } else Column(
                modifier = Modifier
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .imePadding()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("abot.", fontSize = 24.sp, fontWeight = FontWeight.ExtraBold)
                    Spacer(Modifier.weight(1f))
                    IconButton(
                        onClick = {
                            scanner.launch(
                                ScanOptions()
                                    .setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                                    .setPrompt("Scan the ABOT QR code on Windows")
                                    .setBeepEnabled(false)
                                    .setOrientationLocked(true),
                            )
                        },
                        enabled = !isBusy,
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_qr_scan),
                            contentDescription = "Scan QR code to pair a PC",
                            tint = Color.Black,
                        )
                    }
                }
                Text("Send a photo", fontSize = 38.sp, fontWeight = FontWeight.ExtraBold)
                Text(
                    "Open it. Grab it. Release it at your PC.",
                    color = Color(0xFF666666),
                    fontSize = 15.sp,
                    lineHeight = 21.sp,
                )
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            color = if (receiverReady) Color.Black else Color(0xFFBDBDBD),
                            shape = RoundedCornerShape(999.dp),
                            modifier = Modifier.padding(end = 12.dp),
                        ) { Text(" ", fontSize = 8.sp, modifier = Modifier.padding(4.dp)) }
                        Column {
                            Text(
                                when {
                                    receiverReady -> "PC connected"
                                    isDiscovering -> "Finding trusted PC..."
                                    trustedReconnectReady -> "Waiting for PC"
                                    else -> "Not paired"
                                },
                                fontWeight = FontWeight.Bold,
                            )
                            Text(statusMessage, color = Color(0xFF666666), fontSize = 12.sp)
                        }
                    }
                }
                verifiedPairing?.takeIf { !pairingTrusted }?.let { pairing ->
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("Confirm this code on Windows", fontWeight = FontWeight.Bold)
                            Text(pairing.comparisonCode, fontSize = 34.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 4.sp)
                            Button(
                                onClick = {
                                    trustedDevices.trust(pairing)
                                    pairingTrusted = true
                                    statusMessage = "PC trusted. Open the gallery."
                                },
                                modifier = Modifier.fillMaxWidth(),
                            ) { Text("Code matches") }
                        }
                    }
                }
                Button(
                    onClick = {
                        galleryDiscoveryAttempted = false
                        discoveryRetryToken += 1
                        galleryRefreshKey += 1
                        galleryOpen = true
                    },
                    enabled = !isBusy,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Open ABOT Gallery", fontWeight = FontWeight.Bold) }
                if (transferState == TransferState.TRANSFERRING || transferState == TransferState.VERIFYING) {
                    LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                }
                Text(statusMessage, color = Color(0xFF555555), fontSize = 13.sp, lineHeight = 19.sp)
                TextButton(onClick = { showAdvanced = !showAdvanced }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                    Text(if (showAdvanced) "Hide options" else "More options")
                }
                if (showAdvanced) {
                    OutlinedButton(
                        onClick = {
                            galleryDiscoveryAttempted = false
                            discoveryRetryToken += 1
                        },
                        enabled = trustedReconnectReady && !isDiscovering && !isBusy,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Reconnect trusted PC") }
                    OutlinedButton(onClick = { picker.launch(arrayOf("*/*")) }, enabled = !isBusy, modifier = Modifier.fillMaxWidth()) {
                        Text(if (selectedUri == null) "Choose another file type" else "Change selected file")
                    }
                    if (selectedUri != null) {
                        Button(
                            onClick = { selectedUri?.let(::openGestureFor) },
                            enabled = receiverReady && !isBusy,
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("Grab selected file") }
                    }
                    TextButton(onClick = { showManualLink = !showManualLink }, modifier = Modifier.fillMaxWidth()) {
                        Text(if (showManualLink) "Hide manual pairing" else "Manual pairing")
                    }
                    if (showManualLink) {
                        OutlinedTextField(
                            value = pairingLink,
                            onValueChange = { pairingLink = it },
                            label = { Text("Pairing link") },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        OutlinedButton(onClick = ::verifyPairingLink, modifier = Modifier.fillMaxWidth()) { Text("Verify link") }
                    }
                }
            }
        }
    }
}

@Composable
internal fun monochromeButtonColors() = ButtonDefaults.buttonColors(
    containerColor = Color.Black,
    contentColor = Color.White,
    disabledContainerColor = Color(0xFFE0E0E0),
    disabledContentColor = Color(0xFF888888),
)

private fun formatBytes(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val units = arrayOf("KB", "MB", "GB", "TB")
    var value = bytes.toDouble() / 1024
    var unit = 0
    while (value >= 1024 && unit < units.lastIndex) {
        value /= 1024
        unit += 1
    }
    return "%.1f %s".format(value, units[unit])
}

private fun galleryPermissions(): Array<String> = when {
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE -> arrayOf(
        Manifest.permission.READ_MEDIA_IMAGES,
        Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED,
    )
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> arrayOf(Manifest.permission.READ_MEDIA_IMAGES)
    else -> arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
}

private fun hasGalleryPhotoAccess(context: Context): Boolean = galleryPermissions().any { permission ->
    ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
}
