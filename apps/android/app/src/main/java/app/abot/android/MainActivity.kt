package app.abot.android

import android.Manifest
import android.content.Context
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

    override fun onDestroy() {
        if (isFinishing) TrustedReceiverService.disableDiscovery(this)
        super.onDestroy()
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
    var selectedDocuments by remember { mutableStateOf<List<SelectedDocument>>(emptyList()) }
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
        mutableStateOf(if (trustedReconnectReady) "Windows receiver discovery is off." else "Scan once to pair this phone.")
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

    val galleryPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        hasPhotoAccess = hasGalleryPhotoAccess(context)
        galleryRefreshKey += 1
        if (!hasPhotoAccess) statusMessage = "Photo access is required to show ABOT Gallery."
    }
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        TrustedReceiverService.enableDiscovery(context)
        if (!granted) {
            statusMessage = "Discovery is on for this session, but Android may hide incoming-file alerts because notifications are disabled."
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
        if (!trustedReconnectReady || !TrustedReceiverService.isDiscoveryEnabled()) return
        verifiedPairingLink = null
        pairingTrusted = false
        pairingFromService = false
        statusMessage = "Reconnecting to your trusted PC..."
        TrustedReceiverService.reconnect(context)
    }

    fun sendFiles() {
        if (isBusy) return
        val documents = selectedDocuments
        if (documents.isEmpty()) return
        val trustedPairing = verifiedPairing?.takeIf { pairingTrusted } ?: run {
            transferState = TransferState.FAILED
            statusMessage = "Turn on Windows Receiver Discovery or scan the PC QR before sending."
            return
        }
        if (documents.size > 1 && trustedPairing.reconnectProofHex == null) {
            transferState = TransferState.FAILED
            statusMessage = "Send one file first to finish trusted pairing, then turn on receiver discovery for batch transfers."
            return
        }
        if (
            !trustedPairing.isLegacy &&
            trustedPairing.reconnectProofHex == null &&
            System.currentTimeMillis() > trustedPairing.expiresAtUnixMs
        ) {
            statusMessage = "This receiver session expired. Find the PC again or scan a new QR."
            return
        }
        transferState = TransferState.TRANSFER_OFFERED
        statusMessage = "Preparing ${documents.size} encrypted ${if (documents.size == 1) "offer" else "offers"}..."
        progress = 0f
        Thread {
            val totalBytes = documents.sumOf(SelectedDocument::size).coerceAtLeast(1)
            var completedBytes = 0L
            val completed = mutableListOf<String>()
            val failed = mutableListOf<SelectedDocument>()
            documents.forEachIndexed { index, document ->
                val pairing = TrustedReconnectProtocol.prepareQrTransfer(trustedPairing, trustedDevices)
                try {
                    val result = EncryptedTransferClient.send(
                        resolver = context.contentResolver,
                        document = document.uri,
                        pairing = pairing,
                        onOfferSent = {
                            updateState(
                                TransferState.AWAITING_REMOTE_CONFIRMATION,
                                "${index + 1} of ${documents.size}: ${document.name} is waiting for Windows acceptance. No bytes have moved yet.",
                            )
                            mainHandler.post {
                                Toast.makeText(context, "Accept ${document.name} on Windows.", Toast.LENGTH_LONG).show()
                            }
                        },
                        onAccepted = {
                            updateState(TransferState.ACCEPTED, "${index + 1} of ${documents.size} accepted. Starting encrypted upload...")
                        },
                        onProgress = { sent, total ->
                            updateState(
                                TransferState.TRANSFERRING,
                                "${index + 1} of ${documents.size}: sending ${document.name} · ${formatBytes(sent)} of ${formatBytes(total)}",
                                aggregateBatchProgress(completedBytes, sent, totalBytes),
                            )
                        },
                    )
                    if (pairing.reconnectSecretHex != null && result.reconnectProvisioned) {
                        trustedDevices.markReconnectProvisioned(pairing)
                        mainHandler.post { trustedReconnectReady = true }
                    }
                    completed += result.fileName
                    completedBytes += document.size
                } catch (error: Exception) {
                    failed += document
                }
            }
            mainHandler.post {
                selectedDocuments = failed
                progress = if (failed.isEmpty()) 1f else (completedBytes.toDouble() / totalBytes).toFloat().coerceIn(0f, 1f)
                if (completed.isNotEmpty()) recentActivity = "Sent ${completed.size} file${if (completed.size == 1) "" else "s"} to Windows"
                when {
                    failed.isEmpty() -> {
                        transferState = TransferState.COMPLETED
                        statusMessage = "${completed.size} file${if (completed.size == 1) "" else "s"} received and verified on Windows."
                    }
                    completed.isEmpty() -> {
                        transferState = TransferState.FAILED
                        statusMessage = "No files were sent. Check the Windows receiver and try selecting the files again."
                    }
                    else -> {
                        transferState = TransferState.FAILED
                        statusMessage = "${completed.size} sent; ${failed.size} failed: ${failed.joinToString { it.name }}"
                    }
                }
            }
        }.start()
    }

    val gestureLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            Toast.makeText(context, "Selection grabbed. Preparing Windows offers...", Toast.LENGTH_SHORT).show()
            sendFiles()
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

    fun openGestureFor(documents: List<SelectedDocument>) {
        if (isBusy) return
        if (documents.isEmpty()) return
        selectedDocuments = documents
        transferState = TransferState.FILE_SELECTED
        statusMessage = "${documents.size} item${if (documents.size == 1) "" else "s"} ready. Hold an open palm, then close your fist."
        progress = 0f
        gestureLauncher.launch(
            Intent(context, GestureCaptureActivity::class.java)
                .putStringArrayListExtra(GestureCaptureActivity.EXTRA_MEDIA_URIS, ArrayList(documents.map { it.uri.toString() }))
                .putStringArrayListExtra(GestureCaptureActivity.EXTRA_MEDIA_NAMES, ArrayList(documents.map(SelectedDocument::name)))
                .putExtra(GestureCaptureActivity.EXTRA_MEDIA_SIZES, documents.map(SelectedDocument::size).toLongArray())
                .putStringArrayListExtra(GestureCaptureActivity.EXTRA_MEDIA_TYPES, ArrayList(documents.map(SelectedDocument::mimeType))),
        )
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        uris.forEach { uri ->
            runCatching {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        }
        val documents = SelectedDocumentReader.read(context.contentResolver, uris)
        if (documents.isNotEmpty()) openGestureFor(documents)
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
                    onConfirmSelection = { documents ->
                        galleryOpen = false
                        openGestureFor(documents)
                    },
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
                selectedFiles = selectedDocuments,
                discoveryEnabled = trustedConnection.discoveryEnabled,
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
                        .setCaptureActivity(QrScanActivity::class.java)
                        .setBeepEnabled(false)
                        .setOrientationLocked(true),
                )
            },
            onOpenGallery = {
                galleryRefreshKey += 1
                galleryOpen = true
                val missingPermissions = missingGalleryPermissions(context)
                if (missingPermissions.isNotEmpty()) galleryPermission.launch(missingPermissions)
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
            onRetrySelectedFiles = { openGestureFor(selectedDocuments) },
            onReconnect = ::reconnectTrustedPc,
            onDiscoveryToggle = { enabled ->
                if (!enabled) {
                    TrustedReceiverService.disableDiscovery(context)
                    if (pairingFromService) {
                        verifiedPairingLink = null
                        pairingTrusted = false
                        pairingFromService = false
                    }
                    statusMessage = "Windows receiver discovery is off."
                } else if (!trustedReconnectReady) {
                    statusMessage = "Complete one QR-approved transfer before enabling trusted receiver discovery."
                } else if (
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                    ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                ) {
                    notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    TrustedReceiverService.enableDiscovery(context)
                    statusMessage = "Finding your trusted PC..."
                }
            },
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
        Manifest.permission.READ_MEDIA_VIDEO,
        Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED,
    )
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> arrayOf(
        Manifest.permission.READ_MEDIA_IMAGES,
        Manifest.permission.READ_MEDIA_VIDEO,
    )
    else -> arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
}

private fun hasGalleryPhotoAccess(context: Context): Boolean = galleryPermissions().any { permission ->
    ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
}

private fun missingGalleryPermissions(context: Context): Array<String> {
    if (
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE &&
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED) == PackageManager.PERMISSION_GRANTED
    ) return emptyArray()
    return galleryPermissions()
        .filterNot { it == Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED }
        .filter { ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED }
        .toTypedArray()
}
