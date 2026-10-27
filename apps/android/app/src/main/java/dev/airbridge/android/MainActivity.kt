package dev.airbridge.android

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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val Background = Color(0xFF071711)
private val SurfaceGreen = Color(0xFF10291F)
private val Accent = Color(0xFF76E9B3)
private val Muted = Color(0xFFAAC0B6)
private val ErrorRed = Color(0xFFFFB4AB)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { AirBridgeApp() }
    }
}

@Composable
private fun AirBridgeApp() {
    val context = LocalContext.current
    val mainHandler = remember { Handler(Looper.getMainLooper()) }
    var selectedUri by remember { mutableStateOf<Uri?>(null) }
    var pairingLink by remember { mutableStateOf("") }
    var transferState by remember { mutableStateOf(TransferState.IDLE) }
    var statusMessage by remember { mutableStateOf("Select one file to begin.") }
    var progress by remember { mutableFloatStateOf(0f) }
    val isBusy = transferState in setOf(
        TransferState.TRANSFER_OFFERED,
        TransferState.AWAITING_REMOTE_CONFIRMATION,
        TransferState.ACCEPTED,
        TransferState.TRANSFERRING,
        TransferState.VERIFYING,
    )
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            selectedUri = uri
            transferState = TransferState.FILE_SELECTED
            statusMessage = "File selected. Start the Windows receiver and paste its pairing link."
            progress = 0f
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
        transferState = TransferState.TRANSFER_OFFERED
        statusMessage = "Hashing the file and preparing an encrypted offer…"
        progress = 0f
        Thread {
            try {
                val result = EncryptedTransferClient.send(
                    resolver = context.contentResolver,
                    document = document,
                    pairingUri = pairingLink,
                    onOfferSent = {
                        updateState(
                            TransferState.AWAITING_REMOTE_CONFIRMATION,
                            "Offer sent. Waiting for approval on Windows; no file bytes have been sent.",
                        )
                    },
                    onAccepted = {
                        updateState(TransferState.ACCEPTED, "Accepted. Starting encrypted upload…")
                    },
                    onProgress = { sent, total ->
                        val value = if (total == 0L) 1f else (sent.toDouble() / total).toFloat()
                        updateState(
                            TransferState.TRANSFERRING,
                            "Sending ${formatBytes(sent)} of ${formatBytes(total)}…",
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

    MaterialTheme {
        Surface(color = Background, contentColor = Color.White, modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .statusBarsPadding()
                    .padding(horizontal = 24.dp, vertical = 20.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("AirBridge", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.weight(1f))
                    Text("LOCAL TRANSFER", color = Muted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(12.dp))
                Text("ANDROID → WINDOWS", color = Accent, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Text(
                    "Send files\nsecurely.",
                    fontSize = 48.sp,
                    lineHeight = 47.sp,
                    fontWeight = FontWeight.ExtraBold,
                    fontStyle = FontStyle.Italic,
                )
                Text(
                    "Your file stays on this phone until the Windows receiver explicitly accepts it.",
                    color = Muted,
                    fontSize = 16.sp,
                    lineHeight = 23.sp,
                )
                Surface(shape = RoundedCornerShape(24.dp), color = SurfaceGreen, modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Text("01 / SELECT AND PAIR", color = Accent, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Text(
                            if (selectedUri == null) "No file selected" else "One file selected",
                            fontSize = 23.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        Button(
                            onClick = { picker.launch(arrayOf("*/*")) },
                            enabled = !isBusy,
                            colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = Background),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(if (selectedUri == null) "Select file" else "Change file", fontWeight = FontWeight.Bold)
                        }
                        OutlinedTextField(
                            value = pairingLink,
                            onValueChange = { pairingLink = it },
                            enabled = !isBusy,
                            label = { Text("Windows pairing link") },
                            placeholder = { Text("airbridge://pair?…") },
                            singleLine = false,
                            minLines = 2,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Button(
                            onClick = ::sendFile,
                            enabled = selectedUri != null && pairingLink.isNotBlank() && !isBusy,
                            colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = Background),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("Offer file to Windows", fontWeight = FontWeight.Bold)
                        }
                        if (transferState == TransferState.TRANSFERRING || transferState == TransferState.VERIFYING) {
                            LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                        }
                        Text(
                            statusMessage,
                            color = if (transferState == TransferState.FAILED) ErrorRed else Muted,
                            fontSize = 12.sp,
                            lineHeight = 18.sp,
                        )
                    }
                }
            }
        }
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
