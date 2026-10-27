package app.abot.android

import android.content.ContentResolver
import android.database.Cursor
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Base64
import org.json.JSONObject
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.InputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

internal object EncryptedTransferClient {
    // These v1 wire domains intentionally retain their pre-ABOT bytes for compatibility.
    private val offerAad = "AIRBRIDGE-OFFER-V1".toByteArray(StandardCharsets.UTF_8)
    private val decisionAad = "AIRBRIDGE-DECISION-V1".toByteArray(StandardCharsets.UTF_8)
    private val completeAad = "AIRBRIDGE-COMPLETE-V1".toByteArray(StandardCharsets.UTF_8)
    private const val chunkSize = 64 * 1024
    private const val maxControlFrame = 16 * 1024

    data class Result(val fileName: String, val bytesSent: Long)

    fun send(
        resolver: ContentResolver,
        document: Uri,
        pairingUri: String,
        onOfferSent: () -> Unit,
        onAccepted: () -> Unit,
        onProgress: (Long, Long) -> Unit,
    ): Result {
        val pairing = parsePairingUri(pairingUri)
        val fileName = queryDisplayName(resolver, document)
        require(isSafeLeafName(fileName)) { "The selected file name is not portable or safe" }
        val digest = MessageDigest.getInstance("SHA-256")
        var fileSize = 0L
        resolver.openInputStream(document).use { input ->
            requireNotNull(input) { "The selected file is unavailable" }
            val buffer = ByteArray(chunkSize)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                if (count == 0) continue
                digest.update(buffer, 0, count)
                fileSize = Math.addExact(fileSize, count.toLong())
            }
        }

        val transferId = UUID.randomUUID()
        val offer = JSONObject()
            .put("version", 1)
            .put("transferId", transferId.toString())
            .put("fileId", UUID.randomUUID().toString())
            .put("name", fileName)
            .put("size", fileSize)
            .put("sha256", digest.digest().toHex())
            .toString()
            .toByteArray(StandardCharsets.UTF_8)

        Socket().use { socket ->
            socket.connect(InetSocketAddress(pairing.host, pairing.port), 10_000)
            socket.soTimeout = 10 * 60 * 1000
            val output = DataOutputStream(socket.getOutputStream().buffered())
            val input = DataInputStream(socket.getInputStream().buffered())
            output.write(byteArrayOf(0x41, 0x42, 0x52, 0x31))
            writeEncryptedFrame(output, pairing.secret, offerAad, offer)
            onOfferSent()

            val decision = JSONObject(
                String(
                    readEncryptedFrame(input, pairing.secret, decisionAad, maxControlFrame),
                    StandardCharsets.UTF_8,
                ),
            )
            check(decision.optBoolean("accepted")) {
                decision.optString("reason", "The receiver declined the transfer")
            }
            onAccepted()

            resolver.openInputStream(document).use { source ->
                requireNotNull(source) { "The selected file is no longer available" }
                streamFile(source, output, pairing.secret, transferId, fileSize, onProgress)
            }
            output.writeInt(0)
            output.flush()

            val completion = JSONObject(
                String(
                    readEncryptedFrame(input, pairing.secret, completeAad, maxControlFrame),
                    StandardCharsets.UTF_8,
                ),
            )
            check(completion.optBoolean("verified")) { "The receiver could not verify the file" }
        }
        return Result(fileName, fileSize)
    }

    private fun streamFile(
        source: InputStream,
        output: DataOutputStream,
        key: ByteArray,
        transferId: UUID,
        totalBytes: Long,
        onProgress: (Long, Long) -> Unit,
    ) {
        val buffer = ByteArray(chunkSize)
        var sequence = 0L
        var sent = 0L
        while (true) {
            val count = source.read(buffer)
            if (count < 0) break
            if (count == 0) continue
            writeEncryptedFrame(output, key, dataAad(transferId, sequence), buffer.copyOf(count))
            sent += count
            sequence = Math.addExact(sequence, 1)
            onProgress(sent, totalBytes)
        }
    }

    private fun writeEncryptedFrame(
        output: DataOutputStream,
        key: ByteArray,
        associatedData: ByteArray,
        plaintext: ByteArray,
    ) {
        val nonce = ByteArray(12).also(SecureRandom()::nextBytes)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, nonce))
        cipher.updateAAD(associatedData)
        val ciphertext = cipher.doFinal(plaintext)
        output.writeInt(ciphertext.size)
        output.write(nonce)
        output.write(ciphertext)
        output.flush()
    }

    private fun readEncryptedFrame(
        input: DataInputStream,
        key: ByteArray,
        associatedData: ByteArray,
        maximumLength: Int,
    ): ByteArray {
        val length = input.readInt()
        require(length in 16..maximumLength) { "The receiver sent an invalid response" }
        val nonce = ByteArray(12).also(input::readFully)
        val ciphertext = ByteArray(length).also(input::readFully)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, nonce))
        cipher.updateAAD(associatedData)
        return cipher.doFinal(ciphertext)
    }

    private fun dataAad(transferId: UUID, sequence: Long): ByteArray =
        ByteBuffer.allocate(42)
            .put("AIRBRIDGE-DATA-V1\u0000".toByteArray(StandardCharsets.UTF_8))
            .putLong(transferId.mostSignificantBits)
            .putLong(transferId.leastSignificantBits)
            .putLong(sequence)
            .array()

    private fun parsePairingUri(value: String): Pairing {
        val uri = Uri.parse(value.trim())
        require(uri.scheme in setOf("abot", "airbridge") && uri.host == "pair") {
            "Paste a valid ABOT pairing link"
        }
        val endpoint = requireNotNull(uri.getQueryParameter("endpoint")) { "Pairing endpoint is missing" }
        val separator = endpoint.lastIndexOf(':')
        require(separator > 0) { "Pairing endpoint is invalid" }
        val host = endpoint.substring(0, separator)
        val port = endpoint.substring(separator + 1).toIntOrNull()
        require(port != null && port in 1..65535) { "Pairing port is invalid" }
        val secretText = requireNotNull(uri.getQueryParameter("secret")) { "Pairing secret is missing" }
        val secret = Base64.decode(secretText, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
        require(secret.size == 32) { "Pairing secret is invalid" }
        return Pairing(host, port, secret)
    }

    private fun queryDisplayName(resolver: ContentResolver, uri: Uri): String {
        val cursor: Cursor? = resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
        cursor.use {
            requireNotNull(it) { "The selected file metadata is unavailable" }
            require(it.moveToFirst()) { "The selected file metadata is empty" }
            return it.getString(0)
        }
    }

    private fun isSafeLeafName(value: String): Boolean {
        if (value.isEmpty() || value.length > 255 || value == "." || value == "..") return false
        if (value.endsWith('.') || value.endsWith(' ')) return false
        if (value.any { it.code < 32 || it in "\\/:*?\"<>|" }) return false
        val stem = value.substringBefore('.').uppercase()
        return !Regex("^(CON|PRN|AUX|NUL|COM[1-9]|LPT[1-9])$").matches(stem)
    }

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

    private data class Pairing(val host: String, val port: Int, val secret: ByteArray)
}
