package app.abot.android

import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
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
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions

private val AbotColorScheme = darkColorScheme(
    primary = Color.White,
    onPrimary = Color.Black,
    background = Color.Black,
    onBackground = Color.White,
    surface = Color(0xFF111111),
    onSurface = Color.White,
    surfaceVariant = Color(0xFF1C1C1C),
    onSurfaceVariant = Color(0xFFE0E0E0),
    outline = Color.White,
    error = Color.White,
    onError = Color.Black,
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
    var transferState by remember { mutableStateOf(TransferState.IDLE) }
    var statusMessage by rememberSaveable { mutableStateOf("Scan the QR code shown on Windows.") }
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

    fun sendFile() {
        val document = selectedUri ?: return
        val trustedPairing = verifiedPairing?.takeIf { pairingTrusted } ?: return
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
                            "Offer sent. Waiting for Windows approval; no file bytes have been sent.",
                        )
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
                updateState(
                    TransferState.COMPLETED,
                    "${result.fileName} was received, verified, and saved on Windows.",
                    1f,
                )
            } catch (error: Exception) {
                val detail = when (error) {
                    is IllegalArgumentException, is IllegalStateException -> error.message
                    else -> null
                }
                updateState(
                    TransferState.FAILED,
                    detail ?: "Transfer failed. Confirm both devices are on the same local network and try again.",
                )
            }
        }.start()
    }

    MaterialTheme(colorScheme = AbotColorScheme) {
        Surface(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .imePadding()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("abot.", fontSize = 24.sp, fontWeight = FontWeight.ExtraBold)
                    Spacer(Modifier.weight(1f))
                    Text("LOCAL TRANSFER", color = Color.LightGray, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
                Text("Send to Windows", fontSize = 34.sp, fontWeight = FontWeight.ExtraBold)
                Text(
                    "Pair with your PC, choose a file, then wait for Windows to accept it.",
                    color = Color.LightGray,
                    fontSize = 15.sp,
                    lineHeight = 21.sp,
                )
                PairingCard(
                    pairingLink = pairingLink,
                    verifiedPairing = verifiedPairing,
                    pairingTrusted = pairingTrusted,
                    showManualLink = showManualLink,
                    isDiscovering = isDiscovering,
                    isBusy = isBusy,
                    onDiscover = {
                        isDiscovering = true
                        statusMessage = "Looking for a previously trusted Windows receiver..."
                        trustedDiscovery.discover(
                            onFound = { discovered ->
                                isDiscovering = false
                                applyPairing(discovered.pairing, discovered.restorablePairingLink)
                                pairingTrusted = true
                                statusMessage = "Trusted Windows receiver found. Choose a file to continue."
                            },
                            onFinished = { message ->
                                isDiscovering = false
                                statusMessage = message
                            },
                        )
                    },
                    onScan = {
                        scanner.launch(
                            ScanOptions()
                                .setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                                .setPrompt("Scan the ABOT QR code on Windows")
                                .setBeepEnabled(false)
                                .setOrientationLocked(true),
                        )
                    },
                    onToggleManual = { showManualLink = !showManualLink },
                    onLinkChange = {
                        pairingLink = it
                        verifiedPairingLink = null
                        pairingTrusted = false
                    },
                    onVerifyLink = ::verifyPairingLink,
                    onTrust = { pairing ->
                        try {
                            trustedDevices.trust(pairing)
                            pairingTrusted = true
                            statusMessage = "Windows identity trusted on this phone."
                        } catch (error: IllegalArgumentException) {
                            transferState = TransferState.FAILED
                            statusMessage = error.message ?: "This identity could not be trusted."
                        }
                    },
                    onTrustLegacyOnce = {
                        pairingTrusted = true
                        statusMessage = "Legacy link approved for this transfer only."
                    },
                    onRevoke = { pairing ->
                        trustedDevices.revoke(pairing.deviceId)
                        pairingTrusted = false
                        statusMessage = "Trust revoked. Confirm the code again before sending."
                    },
                )
                TransferCard(
                    hasFile = selectedUri != null,
                    canSend = selectedUri != null && verifiedPairing != null && pairingTrusted && !isBusy,
                    isBusy = isBusy,
                    transferState = transferState,
                    progress = progress,
                    statusMessage = statusMessage,
                    onChooseFile = { picker.launch(arrayOf("*/*")) },
                    onSend = ::sendFile,
                )
            }
        }
    }
}

@Composable
private fun PairingCard(
    pairingLink: String,
    verifiedPairing: VerifiedPairing?,
    pairingTrusted: Boolean,
    showManualLink: Boolean,
    isDiscovering: Boolean,
    isBusy: Boolean,
    onDiscover: () -> Unit,
    onScan: () -> Unit,
    onToggleManual: () -> Unit,
    onLinkChange: (String) -> Unit,
    onVerifyLink: () -> Unit,
    onTrust: (VerifiedPairing) -> Unit,
    onTrustLegacyOnce: () -> Unit,
    onRevoke: (VerifiedPairing) -> Unit,
) {
    Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("1. PAIR WITH WINDOWS", fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
            Button(
                onClick = onDiscover,
                enabled = !isBusy && !isDiscovering,
                colors = monochromeButtonColors(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (isDiscovering) "Looking for trusted PCs..." else "Find trusted PC", fontWeight = FontWeight.Bold)
            }
            Button(onClick = onScan, enabled = !isBusy, colors = monochromeButtonColors(), modifier = Modifier.fillMaxWidth()) {
                Text(if (verifiedPairing == null) "Scan QR code" else "Scan a different QR code", fontWeight = FontWeight.Bold)
            }
            OutlinedButton(onClick = onToggleManual, enabled = !isBusy, modifier = Modifier.fillMaxWidth()) {
                Text(if (showManualLink) "Hide manual link" else "Enter link manually")
            }
            if (showManualLink) {
                OutlinedTextField(
                    value = pairingLink,
                    onValueChange = onLinkChange,
                    enabled = !isBusy,
                    label = { Text("Windows pairing link") },
                    placeholder = { Text("abot://pair?...") },
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedButton(
                    onClick = onVerifyLink,
                    enabled = pairingLink.isNotBlank() && !isBusy,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Verify link", fontWeight = FontWeight.Bold) }
            }
            verifiedPairing?.let { pairing ->
                Surface(shape = RoundedCornerShape(10.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(pairing.displayName, fontWeight = FontWeight.Bold)
                        if (pairing.isLegacy) {
                            Text("Compatibility mode: receiver identity is not verified.", fontSize = 13.sp)
                        } else {
                            Text(pairing.comparisonCode, fontSize = 34.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 4.sp)
                            Text("Confirm this code matches Windows.", color = Color.LightGray, fontSize = 13.sp)
                        }
                        when {
                            pairingTrusted && !pairing.isLegacy -> {
                                Text("Trusted receiver identity", fontWeight = FontWeight.Bold)
                                OutlinedButton(
                                    onClick = { onRevoke(pairing) },
                                    enabled = !isBusy,
                                    modifier = Modifier.fillMaxWidth(),
                                ) { Text("Revoke trust") }
                            }
                            !pairing.isLegacy -> {
                                Button(
                                    onClick = { onTrust(pairing) },
                                    enabled = !isBusy,
                                    colors = monochromeButtonColors(),
                                    modifier = Modifier.fillMaxWidth(),
                                ) { Text("Code matches - trust this PC", fontWeight = FontWeight.Bold) }
                            }
                            !pairingTrusted -> {
                                Button(
                                    onClick = onTrustLegacyOnce,
                                    enabled = !isBusy,
                                    colors = monochromeButtonColors(),
                                    modifier = Modifier.fillMaxWidth(),
                                ) { Text("Use legacy link once", fontWeight = FontWeight.Bold) }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TransferCard(
    hasFile: Boolean,
    canSend: Boolean,
    isBusy: Boolean,
    transferState: TransferState,
    progress: Float,
    statusMessage: String,
    onChooseFile: () -> Unit,
    onSend: () -> Unit,
) {
    Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("2. CHOOSE AND SEND", fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
            Text(if (hasFile) "File ready to offer" else "No file selected", fontSize = 20.sp, fontWeight = FontWeight.Bold)
            OutlinedButton(onClick = onChooseFile, enabled = !isBusy, modifier = Modifier.fillMaxWidth()) {
                Text(if (hasFile) "Change file" else "Select file", fontWeight = FontWeight.Bold)
            }
            Button(onClick = onSend, enabled = canSend, colors = monochromeButtonColors(), modifier = Modifier.fillMaxWidth()) {
                Text("Offer file to Windows", fontWeight = FontWeight.Bold)
            }
            if (transferState == TransferState.TRANSFERRING || transferState == TransferState.VERIFYING) {
                LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
            }
            Text(statusMessage, color = Color.White, fontSize = 13.sp, lineHeight = 19.sp)
        }
    }
}

@Composable
private fun monochromeButtonColors() = ButtonDefaults.buttonColors(
    containerColor = Color.White,
    contentColor = Color.Black,
    disabledContainerColor = Color.DarkGray,
    disabledContentColor = Color.LightGray,
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
