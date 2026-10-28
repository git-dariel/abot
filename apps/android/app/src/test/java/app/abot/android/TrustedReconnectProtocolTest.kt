package app.abot.android

import org.junit.Assert.assertEquals
import org.junit.Test

class TrustedReconnectProtocolTest {
    @Test
    fun derivesCrossPlatformReconnectKeyAndProof() {
        val pairing = VerifiedPairing(
            deviceId = "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa",
            displayName = "ABOT Windows",
            host = "192.168.1.8",
            port = 47741,
            secret = ByteArray(32) { it.toByte() },
            identityKeyHex = "11".repeat(32),
            comparisonCode = "123456",
            expiresAtUnixMs = Long.MAX_VALUE,
        )
        val reconnect = TrustedReconnectProtocol.create(
            pairing,
            ReconnectCredential(
                senderDeviceId = "123e4567-e89b-12d3-a456-426614174000",
                token = ByteArray(32) { (it + 1).toByte() },
            ),
        )

        assertEquals(
            "5a5f69b4e018fb6a2a5f2dae2eeb066cb3182957a88d5a596d8e39c79e16e15c",
            reconnect.secret.toHex(),
        )
        assertEquals(
            "f0a0000021dd40df6fd0406b451b2f9baf8f8a06e5d7b04859276f496d5c4b6e",
            reconnect.reconnectProofHex,
        )
    }

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
}
