package app.abot.android

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
    val selectedFiles: List<SelectedDocument>,
    val discoveryEnabled: Boolean,
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
    onRetrySelectedFiles: () -> Unit,
    onReconnect: () -> Unit,
    onDiscoveryToggle: (Boolean) -> Unit,
    onToggleManualPairing: () -> Unit,
    onPairingLinkChange: (String) -> Unit,
    onVerifyPairingLink: () -> Unit,
) {
    AbotTheme {
        Scaffold(
            containerColor = AbotPaper,
            topBar = { AbotMobileTopBar(state.receiverReady, state.isDiscovering, !state.isBusy, onScan) },
            bottomBar = { AbotBottomNavigation(section, onSectionChange) },
        ) { padding ->
            when (section) {
                AbotMobileSection.HOME -> AbotHomeScreen(
                    state, onOpenGallery, onTrustPairing, onOpenReceiveGesture,
                    onReceiveWithoutGesture, onDecline, onChooseFile, onRetrySelectedFiles,
                    Modifier.padding(padding),
                )
                AbotMobileSection.ACTIVITY -> AbotActivityScreen(state, Modifier.padding(padding))
                AbotMobileSection.SETTINGS -> AbotSettingsScreen(
                    state, pairingLink, showManualPairing, onReconnect, onDiscoveryToggle,
                    onChooseFile, onToggleManualPairing, onPairingLinkChange, onVerifyPairingLink,
                    Modifier.padding(padding),
                )
            }
        }
    }
}

@Composable
private fun AbotMobileTopBar(receiverReady: Boolean, isDiscovering: Boolean, enabled: Boolean, onScan: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        AbotBrandMark(Modifier.size(28.dp))
        Text("abot", style = MaterialTheme.typography.titleLarge, letterSpacing = (-.7).sp)
        Spacer(Modifier.weight(1f))
        AbotStatusPill(
            label = when { receiverReady -> "Connected"; isDiscovering -> "Finding PC"; else -> "Offline" },
            connected = receiverReady,
        )
        Surface(shape = CircleShape, color = AbotNavy, modifier = Modifier.size(42.dp)) {
            IconButton(onClick = onScan, enabled = enabled) {
                Icon(painterResource(R.drawable.ic_qr_scan), "Scan QR code to pair a PC", tint = AbotPaper, modifier = Modifier.size(19.dp))
            }
        }
    }
}

@Composable
private fun AbotBottomNavigation(section: AbotMobileSection, onSectionChange: (AbotMobileSection) -> Unit) {
    Surface(color = AbotPaper, shadowElevation = 12.dp) {
        Row(
            modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 18.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceAround,
        ) {
            AbotMobileSection.entries.forEach { item ->
                val active = section == item
                TextButton(
                    onClick = { onSectionChange(item) },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(18.dp),
                    colors = ButtonDefaults.textButtonColors(containerColor = if (active) AbotSoft else Color.Transparent, contentColor = AbotNavy),
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        AbotNavIcon(item, active)
                        Text(item.name.lowercase().replaceFirstChar(Char::uppercase), fontSize = 10.sp, fontWeight = if (active) FontWeight.ExtraBold else FontWeight.Medium, color = if (active) AbotNavy else AbotMuted)
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
    onRetrySelectedFiles: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize().wrapContentWidth(Alignment.CenterHorizontally).widthIn(max = 720.dp)
            .imePadding().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
            AbotSectionLabel("Private local transfer")
            Text("Ready when you are.", style = MaterialTheme.typography.headlineLarge)
            Text("Send, receive, and keep every file between your devices.", style = MaterialTheme.typography.bodyMedium, color = AbotMuted)
        }

        AbotConnectionStrip(state)

        state.verifiedPairing?.takeIf { !state.pairingTrusted }?.let { pairing ->
            AbotPanel(color = AbotSky) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    AbotSectionLabel("Confirm this PC", color = AbotNavy)
                    Text(pairing.comparisonCode, style = MaterialTheme.typography.headlineLarge, letterSpacing = 3.sp)
                    Text("Confirm that this code matches the one on Windows.", color = AbotNavy.copy(alpha = .72f))
                    Button(onClick = { onTrustPairing(pairing) }, modifier = Modifier.fillMaxWidth().height(50.dp), colors = abotPrimaryButtonColors(), shape = AbotButtonShape) { Text("The codes match") }
                }
            }
        }

        AbotPanel(color = AbotSky) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(verticalAlignment = Alignment.Top) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        AbotSectionLabel("Send", color = AbotNavy)
                        Text("Share from this phone", style = MaterialTheme.typography.headlineMedium)
                        Text("Choose photos, videos, or any file.", color = AbotNavy.copy(alpha = .72f), style = MaterialTheme.typography.bodyMedium)
                    }
                    AbotTransferArtwork(Modifier.size(width = 116.dp, height = 78.dp))
                }
                Button(onClick = onOpenGallery, enabled = !state.isBusy, modifier = Modifier.fillMaxWidth().height(52.dp), colors = abotPrimaryButtonColors(), shape = AbotButtonShape) { Text("Open ABOT Gallery") }
                OutlinedButton(
                    onClick = onChooseFile, enabled = !state.isBusy, modifier = Modifier.fillMaxWidth().height(48.dp),
                    border = BorderStroke(1.dp, AbotNavy.copy(alpha = .3f)), shape = AbotButtonShape,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = AbotNavy),
                ) { Text("Choose Any File") }
                if (state.selectedFiles.isNotEmpty()) AbotSelectedFiles(state, onRetrySelectedFiles)
            }
        }

        AbotPanel(color = AbotNavy) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(13.dp)) {
                Row(verticalAlignment = Alignment.Top) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        AbotSectionLabel("Receive", color = AbotSky)
                        Text(state.reverseOffer?.name ?: "Catch files from Windows", style = MaterialTheme.typography.headlineMedium, color = AbotPaper, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(
                            state.reverseOffer?.let { "${formatAbotBytes(it.size)} is waiting for your approval." }
                                ?: if (state.receiverReady) "Your trusted PC is connected and ready." else "Enable discovery when you want this phone to listen.",
                            color = AbotPaper.copy(alpha = .7f), style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    AbotTransferArtwork(Modifier.size(width = 108.dp, height = 74.dp), reversed = true)
                }
                state.reverseOffer?.let { offer ->
                    Button(onClick = { onOpenReceiveGesture(offer) }, modifier = Modifier.fillMaxWidth().height(50.dp), colors = abotBlueButtonColors(), shape = AbotButtonShape) { Text("Receive with open palm") }
                    OutlinedButton(
                        onClick = { onReceiveWithoutGesture(offer) }, modifier = Modifier.fillMaxWidth(), shape = AbotButtonShape,
                        border = BorderStroke(1.dp, AbotPaper.copy(alpha = .35f)), colors = ButtonDefaults.outlinedButtonColors(contentColor = AbotPaper),
                    ) { Text("Receive without gesture") }
                    TextButton(onClick = { onDecline(offer) }, modifier = Modifier.fillMaxWidth()) { Text("Decline", color = AbotSky) }
                } ?: AbotStatusPill(
                    if (state.receiverReady) "Ready to receive" else if (state.discoveryEnabled) "Looking for trusted PC" else "Discovery is off",
                    state.receiverReady,
                    dark = true,
                )
            }
        }

        if (state.transferState == TransferState.TRANSFERRING || state.transferState == TransferState.VERIFYING) {
            AbotPanel {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(Modifier.fillMaxWidth()) {
                        Text(if (state.transferState == TransferState.VERIFYING) "Verifying" else "Transferring", fontWeight = FontWeight.Bold)
                        Spacer(Modifier.weight(1f))
                        Text("${(state.progress * 100).toInt()}%", fontWeight = FontWeight.Bold)
                    }
                    LinearProgressIndicator(progress = { state.progress }, modifier = Modifier.fillMaxWidth().height(7.dp), color = AbotBlue, trackColor = AbotPaper)
                }
            }
        }

        AbotRecentCard(state)
        Text(state.statusMessage, style = MaterialTheme.typography.bodySmall, color = AbotMuted, modifier = Modifier.padding(horizontal = 4.dp))
        Spacer(Modifier.height(4.dp))
    }
}

@Composable
private fun AbotSelectedFiles(state: AbotMainUiState, onRetry: () -> Unit) {
    Surface(color = AbotPaper.copy(alpha = .72f), shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text("${state.selectedFiles.size} item${if (state.selectedFiles.size == 1) "" else "s"} selected", fontWeight = FontWeight.ExtraBold)
            state.selectedFiles.take(3).forEach { item ->
                Text("${item.name}  •  ${formatAbotBytes(item.size)}", color = AbotMuted, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (state.selectedFiles.size > 3) Text("+${state.selectedFiles.size - 3} more", color = AbotMuted, style = MaterialTheme.typography.bodySmall)
            if (state.transferState == TransferState.FAILED) {
                Button(onClick = onRetry, enabled = state.receiverReady && !state.isBusy, modifier = Modifier.fillMaxWidth().padding(top = 6.dp), colors = abotPrimaryButtonColors(), shape = AbotButtonShape) { Text("Retry failed files") }
            }
        }
    }
}

@Composable
private fun AbotConnectionStrip(state: AbotMainUiState) {
    AbotPanel(color = AbotSoft) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = CircleShape, color = if (state.receiverReady) AbotBlue else Color(0xFFB4BFCC), modifier = Modifier.size(10.dp)) {}
            Column(Modifier.padding(start = 12.dp).weight(1f)) {
                Text(
                    when {
                        state.receiverReady -> "Trusted PC connected"
                        state.isDiscovering -> "Finding your trusted PC"
                        !state.discoveryEnabled && state.trustedReconnectReady -> "Receiver discovery is off"
                        state.trustedReconnectReady -> "PC temporarily unavailable"
                        else -> "Pair a PC to begin"
                    },
                    fontWeight = FontWeight.ExtraBold,
                )
                Text(if (state.receiverReady) "Ready for a private local transfer" else state.statusMessage, color = AbotMuted, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun AbotRecentCard(state: AbotMainUiState) {
    AbotPanel {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(13.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Recent item", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.weight(1f))
                Text("This session", color = AbotMuted, style = MaterialTheme.typography.bodySmall)
            }
            state.recentActivity?.let { recent ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(color = AbotSky, shape = RoundedCornerShape(14.dp), modifier = Modifier.size(48.dp)) {
                        Box(contentAlignment = Alignment.Center) { Text("✓", fontWeight = FontWeight.ExtraBold, fontSize = 18.sp) }
                    }
                    Column(Modifier.padding(start = 12.dp).weight(1f)) {
                        Text(recent, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("Received and verified", color = AbotMuted, style = MaterialTheme.typography.bodySmall)
                    }
                }
            } ?: Column(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("No completed transfers yet", fontWeight = FontWeight.Bold)
                Text("Your latest verified file will appear here.", color = AbotMuted, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun AbotActivityScreen(state: AbotMainUiState, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize().wrapContentWidth(Alignment.CenterHorizontally).widthIn(max = 720.dp)
            .verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        AbotSectionLabel("Transfer log")
        Text("Activity", style = MaterialTheme.typography.headlineLarge)
        Text("Verified transfers from this app session.", color = AbotMuted, style = MaterialTheme.typography.bodyMedium)
        AbotRecentCard(state)
        AbotPanel(color = AbotSky) {
            Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = CircleShape, color = AbotNavy, modifier = Modifier.size(42.dp)) { Box(contentAlignment = Alignment.Center) { Text(if (state.isBusy) "…" else "✓", color = AbotPaper, fontWeight = FontWeight.ExtraBold) } }
                Column(Modifier.padding(start = 13.dp).weight(1f)) {
                    Text("Current status", style = MaterialTheme.typography.titleMedium)
                    Text(state.statusMessage, color = AbotNavy.copy(alpha = .72f), style = MaterialTheme.typography.bodySmall, maxLines = 3, overflow = TextOverflow.Ellipsis)
                }
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
    onDiscoveryToggle: (Boolean) -> Unit,
    onChooseFile: () -> Unit,
    onToggleManualPairing: () -> Unit,
    onPairingLinkChange: (String) -> Unit,
    onVerifyPairingLink: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize().wrapContentWidth(Alignment.CenterHorizontally).widthIn(max = 720.dp)
            .imePadding().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        AbotSectionLabel("Device & privacy")
        Text("Settings", style = MaterialTheme.typography.headlineLarge)
        Text("Control when this phone looks for your trusted Windows receiver.", color = AbotMuted, style = MaterialTheme.typography.bodyMedium)

        AbotPanel(color = AbotNavy) {
            Row(Modifier.fillMaxWidth().padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Windows Receiver Discovery", color = AbotPaper, style = MaterialTheme.typography.titleMedium)
                    Text(if (state.discoveryEnabled) "On for this app session" else "Off — no scanning or receiver notification", color = AbotPaper.copy(alpha = .68f), style = MaterialTheme.typography.bodySmall)
                }
                Switch(
                    checked = state.discoveryEnabled,
                    onCheckedChange = onDiscoveryToggle,
                    enabled = !state.isBusy,
                    colors = SwitchDefaults.colors(checkedThumbColor = AbotNavy, checkedTrackColor = AbotSky, uncheckedThumbColor = AbotPaper, uncheckedTrackColor = AbotMuted),
                )
            }
        }

        AbotPanel {
            Column(Modifier.padding(horizontal = 18.dp, vertical = 4.dp)) {
                AbotSettingRow("Connection", if (state.receiverReady) "Trusted PC connected" else "Not connected")
                AbotSettingRow("Incoming files", "Downloads/ABOT")
                AbotSettingRow("Gesture camera", "Processed on this phone")
                AbotSettingRow("Background readiness", if (state.discoveryEnabled) "Enabled by you" else "Inactive", divider = false)
            }
        }

        Button(onClick = onReconnect, enabled = state.discoveryEnabled && state.trustedReconnectReady && !state.isDiscovering && !state.isBusy, modifier = Modifier.fillMaxWidth().height(50.dp), colors = abotBlueButtonColors(), shape = AbotButtonShape) { Text("Find trusted PC now") }
        OutlinedButton(onClick = onChooseFile, enabled = !state.isBusy, modifier = Modifier.fillMaxWidth().height(48.dp), border = BorderStroke(1.dp, AbotLine), shape = AbotButtonShape, colors = ButtonDefaults.outlinedButtonColors(contentColor = AbotNavy)) { Text("Choose another file type") }
        TextButton(onClick = onToggleManualPairing, modifier = Modifier.fillMaxWidth()) { Text(if (showManualPairing) "Hide manual pairing" else "Use manual pairing link", color = AbotNavy) }
        if (showManualPairing) {
            OutlinedTextField(value = pairingLink, onValueChange = onPairingLinkChange, label = { Text("Pairing link") }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp))
            Button(onClick = onVerifyPairingLink, modifier = Modifier.fillMaxWidth().height(50.dp), colors = abotPrimaryButtonColors(), shape = AbotButtonShape) { Text("Verify link") }
        }
        Text("ABOT requires no account or cloud service. Discovery starts only when you turn it on, and pairing—not discovery—establishes trust.", color = AbotMuted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(4.dp))
    }
}

@Composable
private fun AbotSettingRow(label: String, value: String, divider: Boolean = true) {
    Row(Modifier.fillMaxWidth().padding(vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), fontWeight = FontWeight.Bold)
        Text(value, color = AbotMuted, style = MaterialTheme.typography.bodySmall)
    }
    if (divider) Surface(color = AbotPaper, modifier = Modifier.fillMaxWidth().height(1.dp)) {}
}

internal fun formatAbotBytes(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val units = arrayOf("KB", "MB", "GB", "TB")
    var value = bytes.toDouble() / 1024
    var unit = 0
    while (value >= 1024 && unit < units.lastIndex) { value /= 1024; unit += 1 }
    return "%.1f %s".format(value, units[unit])
}
