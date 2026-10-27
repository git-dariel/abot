package app.abot.android

import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters
import org.bouncycastle.crypto.signers.Ed25519Signer
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.nio.charset.StandardCharsets
import java.util.Base64
import java.util.UUID

class PairingInvitationVerifierTest {
    @Test
    fun acceptsLegacyOneTimeLinkWithoutPersistableIdentity() {
        val secret = Base64.getUrlEncoder().withoutPadding().encodeToString(ByteArray(32) { 7 })
        val pairing = PairingInvitationVerifier.parse(
            "airbridge://pair?endpoint=192.168.1.8:47741&secret=$secret",
        )

        check(pairing.isLegacy)
        assertEquals("", pairing.identityKeyHex)
        assertEquals(32, pairing.secret.size)
    }

    @Test
    fun verifiesSignedInvitationAndRejectsTampering() {
        val invitation = invitationUri()
        val verified = PairingInvitationVerifier.verify(invitation, 10_000)

        assertEquals("ABOT Windows", verified.displayName)
        assertEquals("192.168.1.8", verified.host)
        assertEquals(47741, verified.port)
        assertEquals(6, verified.comparisonCode.length)

        val decoded = JSONObject(String(Base64.getUrlDecoder().decode(invitation.substringAfter("data="))))
        decoded.put("displayName", "Impostor")
        val tampered = "abot://pair?data=" + Base64.getUrlEncoder().withoutPadding()
            .encodeToString(decoded.toString().toByteArray(StandardCharsets.UTF_8))
        val failure = runCatching { PairingInvitationVerifier.verify(tampered, 10_000) }.exceptionOrNull()
        check(failure is IllegalArgumentException)
    }

    private fun invitationUri(): String {
        val deviceId = UUID.fromString("123e4567-e89b-12d3-a456-426614174000")
        val displayName = "ABOT Windows"
        val endpoint = "192.168.1.8:47741"
        val expiresAt = 20_000L
        val nonce = ByteArray(32) { it.toByte() }
        val privateKey = Ed25519PrivateKeyParameters(ByteArray(32) { (it + 1).toByte() }, 0)
        val identityKey = privateKey.generatePublicKey().encoded
        val transcript = ByteArrayOutputStream().also { bytes ->
            DataOutputStream(bytes).use { output ->
                output.write("AIRBRIDGE-PAIRING\u0000".toByteArray(StandardCharsets.UTF_8))
                output.writeShort(1)
                output.writeLong(deviceId.mostSignificantBits)
                output.writeLong(deviceId.leastSignificantBits)
                output.writeField(displayName)
                output.writeField(endpoint)
                output.writeLong(expiresAt)
                output.write(nonce)
                output.write(identityKey)
            }
        }.toByteArray()
        val signer = Ed25519Signer().apply {
            init(true, privateKey)
            update(transcript, 0, transcript.size)
        }
        val json = JSONObject()
            .put("version", 1)
            .put("deviceId", deviceId.toString())
            .put("displayName", displayName)
            .put("endpoint", endpoint)
            .put("expiresAtUnixMs", expiresAt)
            .put("nonceHex", nonce.toHex())
            .put("identityKeyHex", identityKey.toHex())
            .put("signatureHex", signer.generateSignature().toHex())
        return "abot://pair?data=" + Base64.getUrlEncoder().withoutPadding()
            .encodeToString(json.toString().toByteArray(StandardCharsets.UTF_8))
    }

    private fun DataOutputStream.writeField(value: String) {
        val bytes = value.toByteArray(StandardCharsets.UTF_8)
        writeShort(bytes.size)
        write(bytes)
    }

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
}
