package app.abot.android

import android.content.ContentResolver
import android.content.ContentValues
import android.os.Build
import android.provider.MediaStore
import androidx.annotation.RequiresApi
import org.json.JSONObject
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URLConnection
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

internal data class ReverseOffer(
    val transferId: UUID,
    val fileId: UUID,
    val name: String,
    val size: Long,
    val sha256: String,
)

internal object ReverseTransferClient {
    private val reverseOfferAad = "AIRBRIDGE-REVERSE-OFFER-V1".toByteArray(StandardCharsets.UTF_8)
    private val decisionAad = "AIRBRIDGE-DECISION-V1".toByteArray(StandardCharsets.UTF_8)
    private val completeAad = "AIRBRIDGE-COMPLETE-V1".toByteArray(StandardCharsets.UTF_8)
    private const val maxControlFrame = 16 * 1024
    private const val maxDataFrame = 64 * 1024 + 16
    private const val maxFileSize = 20L * 1024 * 1024 * 1024

    fun poll(
        resolver: ContentResolver,
        cacheDirectory: File,
        pairing: VerifiedPairing,
        decide: (ReverseOffer) -> Boolean,
        onProgress: (Long, Long) -> Unit,
    ): String? {
        require(pairing.reconnectProofHex != null && pairing.senderDeviceId != null) {
            "Trusted reconnect is required for laptop-to-phone transfer"
        }
        return Socket().use { socket ->
            socket.connect(InetSocketAddress(pairing.host, pairing.port), 10_000)
            socket.soTimeout = 10 * 60 * 1000
            val output = DataOutputStream(socket.getOutputStream().buffered())
            val input = DataInputStream(socket.getInputStream().buffered())
            output.write(byteArrayOf(0x41, 0x42, 0x52, 0x33))
            val hello = JSONObject()
                .put("senderDeviceId", pairing.senderDeviceId)
                .put("proofHex", pairing.reconnectProofHex)
                .toString()
                .toByteArray(StandardCharsets.UTF_8)
            output.writeInt(hello.size)
            output.write(hello)
            output.flush()

            val payload = JSONObject(
                String(readEncryptedFrame(input, pairing.secret, reverseOfferAad, maxControlFrame), StandardCharsets.UTF_8),
            )
            if (!payload.optBoolean("available")) return@use null
            require(payload.getString("receiverDeviceId") == pairing.senderDeviceId) {
                "The Windows offer is not bound to this trusted phone"
            }
            val offer = ReverseOffer(
                transferId = UUID.fromString(payload.getString("transferId")),
                fileId = UUID.fromString(payload.getString("fileId")),
                name = payload.getString("name"),
                size = payload.getLong("size"),
                sha256 = payload.getString("sha256"),
            )
            require(isSafeLeafName(offer.name)) { "Windows offered an unsafe file name" }
            require(offer.size in 0..maxFileSize) { "The offered file exceeds the 20 GiB limit" }
            require(offer.sha256.matches(Regex("[0-9a-fA-F]{64}"))) { "Windows offered an invalid SHA-256 value" }
            val accepted = decide(offer)
            val decision = JSONObject()
                .put("accepted", accepted)
                .put("reason", if (accepted) JSONObject.NULL else "The phone declined the transfer")
                .toString()
                .toByteArray(StandardCharsets.UTF_8)
            writeEncryptedFrame(output, pairing.secret, decisionAad, decision)
            if (!accepted) return@use null

            val temporary = File.createTempFile("abot-", ".part", cacheDirectory)
            try {
                val digest = MessageDigest.getInstance("SHA-256")
                var received = 0L
                var sequence = 0L
                temporary.outputStream().buffered().use { target ->
                    while (true) {
                        val length = input.readInt()
                        if (length == 0) break
                        require(length in 16..maxDataFrame) { "Windows sent an invalid data frame" }
                        val nonce = ByteArray(12).also(input::readFully)
                        val ciphertext = ByteArray(length).also(input::readFully)
                        val plaintext = decrypt(pairing.secret, nonce, ciphertext, dataAad(offer.transferId, sequence))
                        received = Math.addExact(received, plaintext.size.toLong())
                        require(received <= offer.size) { "Windows sent more bytes than declared" }
                        target.write(plaintext)
                        digest.update(plaintext)
                        sequence = Math.addExact(sequence, 1)
                        onProgress(received, offer.size)
                    }
                }
                require(received == offer.size) { "The received file size did not match the offer" }
                require(digest.digest().toHex().equals(offer.sha256, ignoreCase = true)) {
                    "The received file SHA-256 did not match the offer"
                }
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                    throw IllegalStateException("Laptop-to-phone Downloads requires Android 10 or newer")
                }
                publishVerifiedFile(resolver, temporary, offer.name)
                writeEncryptedFrame(
                    output,
                    pairing.secret,
                    completeAad,
                    JSONObject().put("verified", true).toString().toByteArray(StandardCharsets.UTF_8),
                )
                offer.name
            } finally {
                temporary.delete()
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun publishVerifiedFile(resolver: ContentResolver, source: File, name: String) {
        val mime = URLConnection.guessContentTypeFromName(name) ?: "application/octet-stream"
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, mime)
            put(MediaStore.MediaColumns.RELATIVE_PATH, "Download/ABOT")
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val collection = MediaStore.Downloads.EXTERNAL_CONTENT_URI
        val uri = requireNotNull(resolver.insert(collection, values)) { "Android Downloads is unavailable" }
        try {
            resolver.openOutputStream(uri, "w").use { output ->
                requireNotNull(output) { "The Downloads file could not be opened" }
                source.inputStream().buffered().use { input -> input.copyTo(output, 64 * 1024) }
            }
            resolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
        } catch (error: Exception) {
            resolver.delete(uri, null, null)
            throw error
        }
    }

    private fun writeEncryptedFrame(output: DataOutputStream, key: ByteArray, aad: ByteArray, plaintext: ByteArray) {
        val nonce = ByteArray(12).also(SecureRandom()::nextBytes)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, nonce))
        cipher.updateAAD(aad)
        val ciphertext = cipher.doFinal(plaintext)
        output.writeInt(ciphertext.size)
        output.write(nonce)
        output.write(ciphertext)
        output.flush()
    }

    private fun readEncryptedFrame(input: DataInputStream, key: ByteArray, aad: ByteArray, maximum: Int): ByteArray {
        val length = input.readInt()
        require(length in 16..maximum) { "Windows sent an invalid control frame" }
        val nonce = ByteArray(12).also(input::readFully)
        val ciphertext = ByteArray(length).also(input::readFully)
        return decrypt(key, nonce, ciphertext, aad)
    }

    private fun decrypt(key: ByteArray, nonce: ByteArray, ciphertext: ByteArray, aad: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, nonce))
        cipher.updateAAD(aad)
        return cipher.doFinal(ciphertext)
    }

    private fun dataAad(transferId: UUID, sequence: Long): ByteArray = ByteBuffer.allocate(42)
        .put("AIRBRIDGE-DATA-V1\u0000".toByteArray(StandardCharsets.UTF_8))
        .putLong(transferId.mostSignificantBits)
        .putLong(transferId.leastSignificantBits)
        .putLong(sequence)
        .array()

    private fun isSafeLeafName(value: String): Boolean {
        if (value.isEmpty() || value.length > 255 || value == "." || value == "..") return false
        if (value.endsWith('.') || value.endsWith(' ') || value.any { it.code < 32 || it in "\\/:*?\"<>|" }) return false
        return !Regex("^(CON|PRN|AUX|NUL|COM[1-9]|LPT[1-9])$").matches(value.substringBefore('.').uppercase())
    }

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
}
