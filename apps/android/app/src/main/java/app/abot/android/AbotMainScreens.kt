package app.abot.android

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

internal enum class AbotMobileSection { HOME, ACTIVITY, SETTINGS }

internal data class AbotMainUiState(
    val receiverReady: Boolean,
    val isDiscovering: Boolean,
    val isBusy: Boolean,
    val statusMessage: String,
    val trustedReconnectReady: Boolean,
    val transferState: TransferState,
    val progress: Float,
    val selectedFile: Boolean,
    val verifiedPairing: VerifiedPairing?,
    val pairingTrusted: Boolean,
    val reverseOffer: ReverseOffer?,
    val recentActivity: String?,
)

@Composable
internal fun AbotMainShell(
    section: AbotMobileSection,
    state: AbotMainUiState,
    pairingLink: String,
    showManualPairing: Boolean,
    onSectionChange: (AbotMobileSection) -> Unit,
    onScan: () -> Unit,
    onOpenGallery: () -> Unit,
    onTrustPairing: (VerifiedPairing) -> Unit,
    onOpenReceiveGesture: (ReverseOffer) -> Unit,
    onReceiveWithoutGesture: (ReverseOffer) -> Unit,
    onDecline: (ReverseOffer) -> Unit,
    onChooseFile: () -> Unit,
    onGrabSelectedFile: () -> Unit,
    onReconnect: () -> Unit,
    onToggleManualPairing: () -> Unit,
    onPairingLinkChange: (String) -> Unit,
    onVerifyPairingLink: () -> Unit,
) {
    AbotTheme {
        Scaffold(
            containerColor = AbotPaper,
            topBar = {
                AbotMobileTopBar(
                    receiverReady = state.receiverReady,
                    isDiscovering = state.isDiscovering,
                    enabled = !state.isBusy,
                    onScan = onScan,
                )
            },
            bottomBar = { AbotBottomNavigation(section, onSectionChange) },
        ) { padding ->
            when (section) {
                AbotMobileSection.HOME -> AbotHomeScreen(
                    state = state,
                    onOpenGallery = onOpenGallery,
                    onTrustPairing = onTrustPairing,
                    onOpenReceiveGesture = onOpenReceiveGesture,
                    onReceiveWithoutGesture = onReceiveWithoutGesture,
                    onDecline = onDecline,
                    onChooseFile = onChooseFile,
                    onGrabSelectedFile = onGrabSelectedFile,
                    modifier = Modifier.padding(padding),
                )
                AbotMobileSection.ACTIVITY -> AbotActivityScreen(state, Modifier.padding(padding))
                AbotMobileSection.SETTINGS -> AbotSettingsScreen(
                    state = state,
                    pairingLink = pairingLink,
                    showManualPairing = showManualPairing,
                    onReconnect = onReconnect,
                    onChooseFile = onChooseFile,
                    onToggleManualPairing = onToggleManualPairing,
                    onPairingLinkChange = onPairingLinkChange,
                    onVerifyPairingLink = onVerifyPairingLink,
                    modifier = Modifier.padding(padding),
                )
            }
        }
    }
}

@Composable
private fun AbotMobileTopBar(
    receiverReady: Boolean,
    isDiscovering: Boolean,
    enabled: Boolean,
    onScan: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 20.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("abot.", fontSize = 27.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-1.8).sp)
        Spacer(Modifier.weight(1f))
        AbotStatusPill(
            label = when {
                receiverReady -> "Connected"
                isDiscovering -> "Finding PC"
                else -> "Offline"
            },
            connected = receiverReady,
        )
        IconButton(onClick = onScan, enabled = enabled, modifier = Modifier.padding(start = 4.dp)) {
            Icon(
                painter = painterResource(R.drawable.ic_qr_scan),
                contentDescription = "Scan QR code to pair a PC",
                tint = AbotInk,
            )
        }
    }
}

@Composable
private fun AbotBottomNavigation(section: AbotMobileSection, onSectionChange: (AbotMobileSection) -> Unit) {
    Surface(color = AbotPaper, border = BorderStroke(1.dp, AbotInk)) {
        Row(
            modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 8.dp, vertical = 7.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            AbotMobileSection.entries.forEachIndexed { index, item ->
                TextButton(
                    onClick = { onSectionChange(item) },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(2.dp),
                    colors = androidx.compose.material3.ButtonDefaults.textButtonColors(
                        containerColor = if (section == item) AbotBlue else Color.Transparent,
                        contentColor = AbotInk,
                    ),
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("0${index + 1}", fontSize = 8.sp, fontWeight = FontWeight.ExtraBold)
                        Text(item.name.lowercase().replaceFirstChar(Char::uppercase), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun AbotHomeScreen(
    state: AbotMainUiState,
    onOpenGallery: () -> Unit,
    onTrustPairing: (VerifiedPairing) -> Unit,
    onOpenReceiveGesture: (ReverseOffer) -> Unit,
    onReceiveWithoutGesture: (ReverseOffer) -> Unit,
    onDecline: (ReverseOffer) -> Unit,
    onChooseFile: () -> Unit,
    onGrabSelectedFile: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize().wrapContentWidth(Alignment.CenterHorizontally).widthIn(max = 720.dp).imePadding().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            AbotSectionLabel("Private. Local. Immediate.")
            Text("Move files.", style = MaterialTheme.typography.displayLarge)
            Text("Not friction.", style = MaterialTheme.typography.displayLarge, color = AbotBlue)
            Text(
                "Open a photo and grab it with your hand. Your trusted PC handles the rest.",
                style = MaterialTheme.typography.bodyLarge,
                color = AbotMuted,
                modifier = Modifier.padding(top = 4.dp),
            )
        }

        AbotConnectionStrip(state)

        state.verifiedPairing?.takeIf { !state.pairingTrusted }?.let { pairing ->
            AbotPanel {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    AbotSectionLabel("Confirm this PC")
                    Text(pairing.comparisonCode, style = MaterialTheme.typography.headlineLarge, letterSpacing = 4.sp)
                    Text("Make sure this six-digit code matches Windows.", color = AbotMuted)
                    Button(
                        onClick = { onTrustPairing(pairing) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = abotBlueButtonColors(),
                        shape = RoundedCornerShape(2.dp),
                    ) { Text("The codes match") }
                }
            }
        }

        state.reverseOffer?.let { offer ->
            AbotPanel {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Surface(color = AbotBlue, shape = RoundedCornerShape(1.dp)) {
                        Text("INCOMING FILE", Modifier.padding(horizontal = 8.dp, vertical = 5.dp), style = MaterialTheme.typography.labelSmall, color = AbotInk)
                    }
                    Text(offer.name, style = MaterialTheme.typography.headlineMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(formatAbotBytes(offer.size), color = AbotMuted)
                    Button(onClick = { onOpenReceiveGesture(offer) }, modifier = Modifier.fillMaxWidth(), colors = abotBlueButtonColors(), shape = RoundedCornerShape(2.dp)) { Text("Receive with open palm") }
                    OutlinedButton(onClick = { onReceiveWithoutGesture(offer) }, modifier = Modifier.fillMaxWidth(), border = BorderStroke(1.dp, AbotInk), shape = RoundedCornerShape(2.dp)) { Text("Receive without gesture") }
                    TextButton(onClick = { onDecline(offer) }, modifier = Modifier.fillMaxWidth()) { Text("Decline", color = AbotDanger) }
                }
            }
        }

        AbotPanel {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(verticalAlignment = Alignment.Top) {
                    Column(Modifier.weight(1f)) {
                        AbotSectionLabel("Send to PC")
                        Text("Your gallery, ready to grab.", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(top = 8.dp))
                    }
                    Surface(shape = CircleShape, color = AbotBlue) { Text("↗", Modifier.padding(horizontal = 13.dp, vertical = 8.dp), fontSize = 20.sp, fontWeight = FontWeight.Bold) }
                }
                Text("Photos stay on your phone until Windows accepts the encrypted offer.", color = AbotMuted)
                Button(
                    onClick = onOpenGallery,
                    enabled = !state.isBusy,
                    modifier = Modifier.fillMaxWidth().height(54.dp),
                    colors = abotPrimaryButtonColors(),
                    shape = RoundedCornerShape(2.dp),
                ) { Text(if (state.receiverReady) "Open ABOT Gallery" else "Open gallery while reconnecting") }
                OutlinedButton(onClick = onChooseFile, enabled = !state.isBusy, modifier = Modifier.fillMaxWidth(), border = BorderStroke(1.dp, AbotInk), shape = RoundedCornerShape(2.dp)) { Text("Choose any file") }
                if (state.selectedFile) {
                    Button(onClick = onGrabSelectedFile, enabled = state.receiverReady && !state.isBusy, modifier = Modifier.fillMaxWidth(), colors = abotBlueButtonColors(), shape = RoundedCornerShape(2.dp)) { Text("Grab selected file") }
                }
            }
        }

        if (state.transferState == TransferState.TRANSFERRING || state.transferState == TransferState.VERIFYING) {
            LinearProgressIndicator(progress = { state.progress }, modifier = Modifier.fillMaxWidth(), color = AbotBlue, trackColor = AbotLine)
        }
        Text(state.statusMessage, style = MaterialTheme.typography.bodySmall, color = AbotMuted)
        Spacer(Modifier.height(4.dp))
    }
}

@Composable
private fun AbotConnectionStrip(state: AbotMainUiState) {
    Surface(color = if (state.receiverReady) AbotInk else AbotSoft, shape = RoundedCornerShape(2.dp)) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = CircleShape, color = if (state.receiverReady) AbotBlue else Color(0xFFAAAAAA)) {
                Text(" ", fontSize = 6.sp, modifier = Modifier.padding(4.dp))
            }
            Column(Modifier.padding(start = 12.dp).weight(1f)) {
                Text(
                    when {
                        state.receiverReady -> "Trusted PC connected"
                        state.isDiscovering -> "Finding your trusted PC"
                        state.trustedReconnectReady -> "PC temporarily unavailable"
                        else -> "Pair a PC to begin"
                    },
                    color = if (state.receiverReady) AbotPaper else AbotInk,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    if (state.receiverReady) "Ready for a private local transfer" else state.statusMessage,
                    color = if (state.receiverReady) Color(0xFFBDBDBD) else AbotMuted,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun AbotActivityScreen(state: AbotMainUiState, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize().wrapContentWidth(Alignment.CenterHorizontally).widthIn(max = 720.dp).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        AbotSectionLabel("Transfer log")
        Text("Activity.", style = MaterialTheme.typography.displayLarge)
        Surface(border = BorderStroke(1.dp, AbotInk), color = AbotPaper) {
            if (state.recentActivity == null) {
                Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 64.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("00", style = MaterialTheme.typography.displayLarge, color = AbotBlue)
                    Text("No completed transfers yet.", style = MaterialTheme.typography.titleLarge)
                    Text("Your latest verified transfer will appear here.", color = AbotMuted, modifier = Modifier.padding(top = 8.dp))
                }
            } else {
                Row(Modifier.fillMaxWidth().padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                    Surface(color = AbotBlue, shape = RoundedCornerShape(1.dp)) { Text("✓", Modifier.padding(12.dp), fontWeight = FontWeight.ExtraBold) }
                    Column(Modifier.padding(start = 14.dp).weight(1f)) {
                        Text(state.recentActivity, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text("Verified this session", style = MaterialTheme.typography.bodySmall, color = AbotMuted)
                    }
                }
            }
        }
        AbotPanel {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                AbotSectionLabel("Current status")
                Text(state.statusMessage, style = MaterialTheme.typography.titleLarge)
                Text(if (state.isBusy) "Transfer in progress" else "Ready for the next transfer", color = AbotMuted)
            }
        }
    }
}

@Composable
private fun AbotSettingsScreen(
    state: AbotMainUiState,
    pairingLink: String,
    showManualPairing: Boolean,
    onReconnect: () -> Unit,
    onChooseFile: () -> Unit,
    onToggleManualPairing: () -> Unit,
    onPairingLinkChange: (String) -> Unit,
    onVerifyPairingLink: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize().wrapContentWidth(Alignment.CenterHorizontally).widthIn(max = 720.dp).imePadding().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        AbotSectionLabel("Device & privacy")
        Text("Settings.", style = MaterialTheme.typography.displayLarge)
        Column {
            AbotSettingRow("Connection", if (state.receiverReady) "Trusted PC connected" else "Not connected")
            AbotSettingRow("Incoming files", "Downloads/ABOT")
            AbotSettingRow("Gesture camera", "Processed on this phone")
            AbotSettingRow("Background readiness", if (state.trustedReconnectReady) "Enabled after app launch" else "Pairing required")
        }
        Button(onClick = onReconnect, enabled = state.trustedReconnectReady && !state.isDiscovering && !state.isBusy, modifier = Modifier.fillMaxWidth(), colors = abotBlueButtonColors(), shape = RoundedCornerShape(2.dp)) { Text("Reconnect trusted PC") }
        OutlinedButton(onClick = onChooseFile, enabled = !state.isBusy, modifier = Modifier.fillMaxWidth(), border = BorderStroke(1.dp, AbotInk), shape = RoundedCornerShape(2.dp)) { Text("Choose another file type") }
        TextButton(onClick = onToggleManualPairing, modifier = Modifier.fillMaxWidth()) { Text(if (showManualPairing) "Hide manual pairing" else "Use manual pairing link", color = AbotInk) }
        if (showManualPairing) {
            OutlinedTextField(value = pairingLink, onValueChange = onPairingLinkChange, label = { Text("Pairing link") }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(2.dp))
            Button(onClick = onVerifyPairingLink, modifier = Modifier.fillMaxWidth(), colors = abotPrimaryButtonColors(), shape = RoundedCornerShape(2.dp)) { Text("Verify link") }
        }
        Text("ABOT does not require an account or cloud service. Discovery is only a hint; your approved device identity establishes trust.", color = AbotMuted, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun AbotSettingRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 17.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), fontWeight = FontWeight.Bold)
        Text(value, color = AbotMuted, style = MaterialTheme.typography.bodySmall)
    }
    Surface(color = AbotLine, modifier = Modifier.fillMaxWidth().height(1.dp)) {}
}

internal fun formatAbotBytes(bytes: Long): String {
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
