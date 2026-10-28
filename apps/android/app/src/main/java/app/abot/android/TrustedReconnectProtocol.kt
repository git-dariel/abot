package app.abot.android

import java.net.URI
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.util.UUID
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

internal object TrustedReconnectProtocol {
    private val keyDomain = "AIRBRIDGE-RECONNECT-KEY-V1\u0000".toByteArray(StandardCharsets.UTF_8)
    private val proofDomain = "AIRBRIDGE-RECONNECT-PROOF-V1\u0000".toByteArray(StandardCharsets.UTF_8)
    private const val marker = "trustedReconnect=1"

    fun mark(pairingUri: String): String = "$pairingUri&$marker"

    fun resolve(value: String, trustedDevices: TrustedDeviceStore): VerifiedPairing {
        val pairing = PairingInvitationVerifier.parse(value)
        if (!isMarked(value)) return pairing
        val credential = trustedDevices.getReconnectCredential(pairing)
            ?: throw IllegalArgumentException("This PC needs one successful QR transfer before trusted reconnect")
        return create(pairing, credential)
    }

    fun create(pairing: VerifiedPairing, credential: ReconnectCredential): VerifiedPairing {
        require(!pairing.isLegacy) { "Legacy links cannot be used for trusted reconnect" }
        val senderId = UUID.fromString(credential.senderDeviceId)
        val proof = mac(credential.token, proofDomain, pairing, senderId)
        val key = mac(credential.token, keyDomain, pairing, senderId)
        return pairing.copy(
            secret = key,
            senderDeviceId = senderId.toString(),
            reconnectSecretHex = null,
            reconnectProofHex = proof.toHex(),
        )
    }

    fun prepareQrTransfer(pairing: VerifiedPairing, trustedDevices: TrustedDeviceStore): VerifiedPairing {
        if (pairing.isLegacy || pairing.reconnectProofHex != null) return pairing
        val credential = trustedDevices.getOrCreateReconnectCredential(pairing)
        return pairing.copy(
            senderDeviceId = credential.senderDeviceId,
            reconnectSecretHex = credential.token.toHex(),
        )
    }

    private fun isMarked(value: String): Boolean = URI(value).rawQuery
        ?.split('&')
        ?.any { it == marker } == true

    private fun mac(
        token: ByteArray,
        domain: ByteArray,
        pairing: VerifiedPairing,
        senderDeviceId: UUID,
    ): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(token, "HmacSHA256"))
        mac.update(domain)
        mac.update(pairing.secret)
        mac.update(pairing.identityKeyHex.decodeHex())
        mac.update(
            ByteBuffer.allocate(16)
                .putLong(senderDeviceId.mostSignificantBits)
                .putLong(senderDeviceId.leastSignificantBits)
                .array(),
        )
        return mac.doFinal()
    }

    private fun String.decodeHex(): ByteArray = chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
}
