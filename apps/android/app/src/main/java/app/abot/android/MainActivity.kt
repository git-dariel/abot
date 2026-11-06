package app.abot.android

import android.Manifest
import android.content.Context
import android.net.Uri
import android.content.Intent
import android.content.BroadcastReceiver
import android.content.IntentFilter
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions

class MainActivity : ComponentActivity() {
    companion object {
        const val ACTION_SHOW_RECEIVE = "app.abot.android.action.SHOW_RECEIVE"
    }

    private var requestedReceiveId by mutableStateOf<String?>(null)
    private var completedReceiveName by mutableStateOf<String?>(null)
    private var trustedConnection by mutableStateOf(TrustedConnectionSnapshot())
    private var receiverRegistered = false
    private val transferEvents = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                TrustedReceiverService.ACTION_OFFER_AVAILABLE -> {
                    requestedReceiveId = intent.getStringExtra(TrustedReceiverService.EXTRA_TRANSFER_ID)
                }
                TrustedReceiverService.ACTION_TRANSFER_COMPLETED -> {
                    val name = intent.getStringExtra(TrustedReceiverService.EXTRA_FILE_NAME) ?: "File"
                    completedReceiveName = name
                    Toast.makeText(context, "$name received from Windows.", Toast.LENGTH_LONG).show()
                }
                TrustedReceiverService.ACTION_CONNECTION_CHANGED -> {
                    trustedConnection = TrustedReceiverService.connectionSnapshot()
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestedReceiveId = intent.receiveTransferId()
            ?: TrustedReceiverService.pendingOffer()?.transferId?.toString()
        trustedConnection = TrustedReceiverService.connectionSnapshot()
        setContent {
            AbotApp(
                requestedReceiveId = requestedReceiveId,
                completedReceiveName = completedReceiveName,
                trustedConnection = trustedConnection,
                onReceiveRequestConsumed = { requestedReceiveId = null },
                onCompletedReceiveConsumed = { completedReceiveName = null },
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        requestedReceiveId = intent.receiveTransferId()
            ?: TrustedReceiverService.pendingOffer()?.transferId?.toString()
    }

    override fun onStart() {
        super.onStart()
        if (!receiverRegistered) {
            ContextCompat.registerReceiver(
                this,
                transferEvents,
                IntentFilter().apply {
                    addAction(TrustedReceiverService.ACTION_OFFER_AVAILABLE)
                    addAction(TrustedReceiverService.ACTION_TRANSFER_COMPLETED)
                    addAction(TrustedReceiverService.ACTION_CONNECTION_CHANGED)
                },
                ContextCompat.RECEIVER_NOT_EXPORTED,
            )
            receiverRegistered = true
        }
        TrustedReceiverService.consumeCompletedFile()?.let { completedReceiveName = it }
        trustedConnection = TrustedReceiverService.connectionSnapshot()
    }

    override fun onStop() {
        if (receiverRegistered) {
            unregisterReceiver(transferEvents)
            receiverRegistered = false
        }
        super.onStop()
    }

    private fun Intent.receiveTransferId(): String? =
        takeIf { action == ACTION_SHOW_RECEIVE }?.getStringExtra(TrustedReceiverService.EXTRA_TRANSFER_ID)
}

@Composable
private fun AbotApp(
    requestedReceiveId: String?,
    completedReceiveName: String?,
    trustedConnection: TrustedConnectionSnapshot,
    onReceiveRequestConsumed: () -> Unit,
    onCompletedReceiveConsumed: () -> Unit,
) {
    val context = LocalContext.current
    val mainHandler = remember { Handler(Looper.getMainLooper()) }
    val trustedDevices = remember { TrustedDeviceStore(context) }
    var selectedUri by rememberSaveable { mutableStateOf<Uri?>(null) }
    var galleryOpen by rememberSaveable { mutableStateOf(false) }
    var galleryRefreshKey by rememberSaveable { mutableIntStateOf(0) }
    var activeSection by rememberSaveable { mutableStateOf(AbotMobileSection.HOME) }
    var recentActivity by rememberSaveable { mutableStateOf<String?>(null) }
    var hasPhotoAccess by remember { mutableStateOf(hasGalleryPhotoAccess(context)) }
    var pairingLink by rememberSaveable { mutableStateOf("") }
    var verifiedPairingLink by rememberSaveable { mutableStateOf<String?>(null) }
    val verifiedPairing = remember(verifiedPairingLink) {
        verifiedPairingLink?.let { link ->
            runCatching { TrustedReconnectProtocol.resolve(link, trustedDevices) }.getOrNull()
        }
    }
    var pairingTrusted by rememberSaveable { mutableStateOf(false) }
    var pairingFromService by rememberSaveable { mutableStateOf(false) }
    var showManualLink by rememberSaveable { mutableStateOf(false) }
    var trustedReconnectReady by remember {
        mutableStateOf(trustedDevices.hasProvisionedReconnectCredential())
    }
    var transferState by remember { mutableStateOf(TransferState.IDLE) }
    var statusMessage by rememberSaveable {
        mutableStateOf(if (trustedReconnectReady) "Finding your trusted PC..." else "Scan once to pair this phone.")
    }
    var progress by remember { mutableFloatStateOf(0f) }
    var reverseOffer by remember { mutableStateOf<ReverseOffer?>(null) }
    val isBusy = transferState in setOf(
        TransferState.TRANSFER_OFFERED,
        TransferState.AWAITING_REMOTE_CONFIRMATION,
        TransferState.ACCEPTED,
        TransferState.TRANSFERRING,
        TransferState.VERIFYING,
    )
    val isDiscovering = trustedConnection.isSearching

    fun applyPairing(pairing: VerifiedPairing, link: String, fromService: Boolean = false) {
        pairingLink = link
        verifiedPairingLink = link
        pairingTrusted = trustedDevices.isTrusted(pairing)
        pairingFromService = fromService
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
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        TrustedReceiverService.start(context)
        if (!granted) {
            statusMessage = "Background receiver is active, but open ABOT to see incoming files because notifications are disabled."
        }
    }
    val scanner = rememberLauncherForActivityResult(ScanContract()) { result ->
        result.contents?.let { scanned ->
            try {
                applyPairing(PairingInvitationVerifier.verify(scanned), scanned)
            } catch (error: Exception) {
                verifiedPairingLink = null
                pairingTrusted = false
                pairingFromService = false
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
            pairingFromService = false
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

    fun reconnectTrustedPc() {
        if (!trustedReconnectReady) return
        verifiedPairingLink = null
        pairingTrusted = false
        pairingFromService = false
        statusMessage = "Reconnecting to your trusted PC..."
        TrustedReceiverService.reconnect(context)
    }

    fun sendFile() {
        if (isBusy) return
        val document = selectedUri ?: return
        val trustedPairing = verifiedPairing?.takeIf { pairingTrusted } ?: return
        if (
            !trustedPairing.isLegacy &&
            trustedPairing.reconnectProofHex == null &&
            System.currentTimeMillis() > trustedPairing.expiresAtUnixMs
        ) {
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
                mainHandler.post { recentActivity = "Sent ${result.fileName} to Windows" }
                mainHandler.postDelayed({
                    selectedUri = null
                    transferState = TransferState.IDLE
                    if (pairing.reconnectProofHex == null && result.reconnectProvisioned) {
                        verifiedPairingLink = null
                        pairingTrusted = false
                        pairingFromService = false
                        TrustedReceiverService.start(context)
                        statusMessage = "Ready for another photo. Reconnecting with your trusted PC..."
                    } else {
                        statusMessage = "Ready for another photo. Your trusted PC stays connected."
                    }
                }, 1_500)
            } catch (error: Exception) {
                val detail = when (error) {
                    is IllegalArgumentException, is IllegalStateException -> error.message
                    else -> null
                }
                if (
                    error.message?.contains("Connection refused", ignoreCase = true) == true ||
                    error.message?.contains("reset", ignoreCase = true) == true
                ) {
                    mainHandler.post {
                        verifiedPairingLink = null
                        pairingTrusted = false
                        pairingFromService = false
                        TrustedReceiverService.reconnect(context)
                    }
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
    val receiveGestureLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val transferId = result.data?.getStringExtra(ReceiveGestureActivity.EXTRA_TRANSFER_ID)
        if (result.resultCode == Activity.RESULT_OK && transferId != null) {
            TrustedReceiverService.accept(context, transferId)
            reverseOffer = null
            transferState = TransferState.ACCEPTED
            statusMessage = "Palm confirmed. Receiving encrypted file from Windows..."
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
    LaunchedEffect(trustedConnection.pairingLink, trustedConnection.message, trustedReconnectReady, isBusy) {
        val link = trustedConnection.pairingLink
        if (link != null && link != verifiedPairingLink) {
            runCatching { TrustedReconnectProtocol.resolve(link, trustedDevices) }
                .onSuccess { pairing ->
                    applyPairing(pairing, link, fromService = true)
                    pairingTrusted = true
                    statusMessage = "Trusted Windows receiver ready. Open a photo to continue."
                }
                .onFailure {
                    verifiedPairingLink = null
                    pairingTrusted = false
                    statusMessage = it.message ?: "The trusted connection could not be restored."
                }
        } else if (link == null && trustedReconnectReady && !isBusy) {
            if (pairingFromService) {
                verifiedPairingLink = null
                pairingTrusted = false
                pairingFromService = false
            }
            if (verifiedPairingLink == null) statusMessage = trustedConnection.message
        }
    }
    LaunchedEffect(trustedReconnectReady) {
        if (trustedReconnectReady) {
            if (
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                TrustedReceiverService.start(context)
            }
        }
    }
    LaunchedEffect(requestedReceiveId, isBusy) {
        val transferId = requestedReceiveId ?: return@LaunchedEffect
        if (isBusy) return@LaunchedEffect
        val offer = TrustedReceiverService.pendingOffer(transferId)
        onReceiveRequestConsumed()
        if (offer == null) {
            Toast.makeText(context, "This transfer offer is no longer available.", Toast.LENGTH_SHORT).show()
        } else {
            reverseOffer = offer
            statusMessage = "${offer.name} is waiting. Show an open palm to receive it."
            receiveGestureLauncher.launch(
                Intent(context, ReceiveGestureActivity::class.java)
                    .putExtra(ReceiveGestureActivity.EXTRA_TRANSFER_ID, transferId),
            )
        }
    }
    LaunchedEffect(completedReceiveName) {
        val name = completedReceiveName ?: return@LaunchedEffect
        reverseOffer = null
        transferState = TransferState.IDLE
        progress = 1f
        statusMessage = "$name was verified and saved to Downloads/ABOT."
        recentActivity = "Received $name from Windows"
        onCompletedReceiveConsumed()
    }

    if (galleryOpen) {
        AbotTheme {
            Surface(modifier = Modifier.fillMaxSize()) {
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
            }
        }
    } else {
        AbotMainShell(
            section = activeSection,
            state = AbotMainUiState(
                receiverReady = receiverReady,
                isDiscovering = isDiscovering,
                isBusy = isBusy,
                statusMessage = statusMessage,
                trustedReconnectReady = trustedReconnectReady,
                transferState = transferState,
                progress = progress,
                selectedFile = selectedUri != null,
                verifiedPairing = verifiedPairing,
                pairingTrusted = pairingTrusted,
                reverseOffer = reverseOffer,
                recentActivity = recentActivity,
            ),
            pairingLink = pairingLink,
            showManualPairing = showManualLink,
            onSectionChange = { activeSection = it },
            onScan = {
                scanner.launch(
                    ScanOptions()
                        .setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                        .setPrompt("Scan the ABOT QR code on Windows")
                        .setBeepEnabled(false)
                        .setOrientationLocked(true),
                )
            },
            onOpenGallery = {
                if (!receiverReady && trustedReconnectReady) reconnectTrustedPc()
                galleryRefreshKey += 1
                galleryOpen = true
            },
            onTrustPairing = { pairing ->
                trustedDevices.trust(pairing)
                pairingTrusted = true
                statusMessage = "PC trusted. Open the gallery."
            },
            onOpenReceiveGesture = { offer ->
                receiveGestureLauncher.launch(
                    Intent(context, ReceiveGestureActivity::class.java)
                        .putExtra(ReceiveGestureActivity.EXTRA_TRANSFER_ID, offer.transferId.toString()),
                )
            },
            onReceiveWithoutGesture = { offer ->
                TrustedReceiverService.accept(context, offer.transferId.toString())
                reverseOffer = null
                transferState = TransferState.ACCEPTED
                statusMessage = "Accepted. Receiving encrypted file..."
            },
            onDecline = { offer ->
                TrustedReceiverService.decline(context, offer.transferId.toString())
                reverseOffer = null
                statusMessage = "Windows transfer declined."
            },
            onChooseFile = { picker.launch(arrayOf("*/*")) },
            onGrabSelectedFile = { selectedUri?.let(::openGestureFor) },
            onReconnect = ::reconnectTrustedPc,
            onToggleManualPairing = { showManualLink = !showManualLink },
            onPairingLinkChange = { pairingLink = it },
            onVerifyPairingLink = ::verifyPairingLink,
        )
    }
}

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
