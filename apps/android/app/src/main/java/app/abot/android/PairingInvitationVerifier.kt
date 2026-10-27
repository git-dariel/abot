package app.abot.android

import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters
import org.bouncycastle.crypto.signers.Ed25519Signer
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.net.URI
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.Base64
import java.util.UUID

internal data class VerifiedPairing(
    val deviceId: String,
    val displayName: String,
    val host: String,
    val port: Int,
    val secret: ByteArray,
    val identityKeyHex: String,
    val comparisonCode: String,
    val expiresAtUnixMs: Long,
    val isLegacy: Boolean = false,
)

internal object PairingInvitationVerifier {
    private const val invitationVersion = 1

    fun parse(value: String, nowUnixMs: Long = System.currentTimeMillis()): VerifiedPairing {
        val uri = URI(value.trim())
        return if (queryParameter(uri.rawQuery, "data") != null) {
            verify(value, nowUnixMs)
        } else {
            parseLegacy(uri)
        }
    }

    fun verify(value: String, nowUnixMs: Long = System.currentTimeMillis()): VerifiedPairing {
        val uri = URI(value.trim())
        require(uri.scheme in setOf("abot", "airbridge") && uri.host == "pair") {
            "Scan or paste a valid ABOT pairing link"
        }
        val encoded = queryParameter(uri.rawQuery, "data")
            ?: throw IllegalArgumentException("The pairing link is not a signed invitation")
        val payload = String(Base64.getUrlDecoder().decode(encoded), StandardCharsets.UTF_8)
        val json = JSONObject(payload)
        require(json.getInt("version") == invitationVersion) { "This pairing version is not supported" }
        val expiresAt = json.getLong("expiresAtUnixMs")
        require(nowUnixMs <= expiresAt) { "The pairing invitation has expired" }
        val deviceId = json.getString("deviceId")
        val displayName = json.getString("displayName")
        require(displayName.isNotBlank() && displayName.toByteArray(StandardCharsets.UTF_8).size <= 80) {
            "The device name is invalid"
        }
        val endpoint = json.getString("endpoint")
        val separator = endpoint.lastIndexOf(':')
        require(separator > 0) { "The pairing endpoint is invalid" }
        val host = endpoint.substring(0, separator)
        val port = endpoint.substring(separator + 1).toIntOrNull()
        require(port != null && port in 1..65535 && isPrivateIpv4(host)) {
            "The pairing endpoint is not on a private IPv4 network"
        }
        val nonce = json.getString("nonceHex").decodeHex(32, "pairing nonce")
        val identityKey = json.getString("identityKeyHex").decodeHex(32, "identity key")
        val signature = json.getString("signatureHex").decodeHex(64, "signature")
        val transcript = transcript(deviceId, displayName, endpoint, expiresAt, nonce, identityKey)
        val verifier = Ed25519Signer().apply {
            init(false, Ed25519PublicKeyParameters(identityKey, 0))
            update(transcript, 0, transcript.size)
        }
        require(verifier.verifySignature(signature)) { "The pairing invitation signature is invalid" }
        val digest = MessageDigest.getInstance("SHA-256").digest(transcript)
        val codeValue = ((digest[0].toLong() and 0xff) shl 24) or
            ((digest[1].toLong() and 0xff) shl 16) or
            ((digest[2].toLong() and 0xff) shl 8) or (digest[3].toLong() and 0xff)
        return VerifiedPairing(
            deviceId = deviceId,
            displayName = displayName,
            host = host,
            port = port,
            secret = nonce,
            identityKeyHex = identityKey.toHex(),
            comparisonCode = (codeValue % 1_000_000).toString().padStart(6, '0'),
            expiresAtUnixMs = expiresAt,
        )
    }

    private fun parseLegacy(uri: URI): VerifiedPairing {
        require(uri.scheme in setOf("abot", "airbridge") && uri.host == "pair") {
            "Paste a valid ABOT pairing link"
        }
        val endpoint = queryParameter(uri.rawQuery, "endpoint")
            ?: throw IllegalArgumentException("The pairing endpoint is missing")
        val separator = endpoint.lastIndexOf(':')
        require(separator > 0) { "The pairing endpoint is invalid" }
        val host = endpoint.substring(0, separator)
        val port = endpoint.substring(separator + 1).toIntOrNull()
        require(port != null && port in 1..65535 && isPrivateIpv4(host)) {
            "The pairing endpoint is not on a private IPv4 network"
        }
        val secret = Base64.getUrlDecoder().decode(
            queryParameter(uri.rawQuery, "secret")
                ?: throw IllegalArgumentException("The pairing secret is missing"),
        )
        require(secret.size == 32) { "The pairing secret is invalid" }
        return VerifiedPairing(
            deviceId = "legacy-$endpoint",
            displayName = "Legacy Windows receiver",
            host = host,
            port = port,
            secret = secret,
            identityKeyHex = "",
            comparisonCode = "",
            expiresAtUnixMs = Long.MAX_VALUE,
            isLegacy = true,
        )
    }

    private fun transcript(
        deviceId: String,
        displayName: String,
        endpoint: String,
        expiresAt: Long,
        nonce: ByteArray,
        identityKey: ByteArray,
    ): ByteArray = ByteArrayOutputStream().also { bytes ->
        DataOutputStream(bytes).use { output ->
            output.write("AIRBRIDGE-PAIRING\u0000".toByteArray(StandardCharsets.UTF_8))
            output.writeShort(invitationVersion)
            val uuid = UUID.fromString(deviceId)
            output.writeLong(uuid.mostSignificantBits)
            output.writeLong(uuid.leastSignificantBits)
            output.writeField(displayName)
            output.writeField(endpoint)
            output.writeLong(expiresAt)
            output.write(nonce)
            output.write(identityKey)
        }
    }.toByteArray()

    private fun DataOutputStream.writeField(value: String) {
        val bytes = value.toByteArray(StandardCharsets.UTF_8)
        require(bytes.size <= 65535) { "The pairing invitation is malformed" }
        writeShort(bytes.size)
        write(bytes)
    }

    private fun queryParameter(query: String?, name: String): String? = query
        ?.split('&')
        ?.firstOrNull { it.substringBefore('=') == name }
        ?.substringAfter('=', "")
        ?.takeIf(String::isNotEmpty)

    private fun isPrivateIpv4(value: String): Boolean {
        val parts = value.split('.').mapNotNull(String::toIntOrNull)
        if (parts.size != 4 || parts.any { it !in 0..255 }) return false
        return parts[0] == 10 ||
            (parts[0] == 172 && parts[1] in 16..31) ||
            (parts[0] == 192 && parts[1] == 168)
    }

    private fun String.decodeHex(size: Int, label: String): ByteArray {
        require(length == size * 2 && all { it.isDigit() || it.lowercaseChar() in 'a'..'f' }) {
            "The $label is invalid"
        }
        return chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    }

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
}

