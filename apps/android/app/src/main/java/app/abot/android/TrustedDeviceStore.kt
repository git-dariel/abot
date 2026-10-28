package app.abot.android

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import java.security.SecureRandom
import java.util.Base64
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

internal data class ReconnectCredential(
    val senderDeviceId: String,
    val token: ByteArray,
)

internal class TrustedDeviceStore(context: Context) {
    private val preferences = context.getSharedPreferences("trusted_devices_v1", Context.MODE_PRIVATE)
    private val senderDeviceId: String by lazy {
        preferences.getString("sender_device_id", null) ?: UUID.randomUUID().toString().also {
            check(preferences.edit().putString("sender_device_id", it).commit()) {
                "The Android reconnect identity could not be saved"
            }
        }
    }

    fun trust(pairing: VerifiedPairing) {
        require(!pairing.isLegacy) { "Legacy pairing links cannot be saved as trusted devices" }
        val existing = preferences.getString("${pairing.deviceId}.key", null)
        require(existing == null || existing == pairing.identityKeyHex) {
            "This device ID was previously trusted with a different identity key"
        }
        preferences.edit()
            .putString("${pairing.deviceId}.key", pairing.identityKeyHex)
            .putString("${pairing.deviceId}.name", pairing.displayName)
            .apply()
    }

    fun isTrusted(pairing: VerifiedPairing): Boolean =
        !pairing.isLegacy && preferences.getString("${pairing.deviceId}.key", null) == pairing.identityKeyHex

    fun revoke(deviceId: String) {
        preferences.edit()
            .remove("$deviceId.key")
            .remove("$deviceId.name")
            .remove("$deviceId.reconnect")
            .remove("$deviceId.reconnect_provisioned")
            .apply()
    }

    fun hasProvisionedReconnectCredential(): Boolean = preferences.all.any { (key, value) ->
        key.endsWith(".reconnect_provisioned") && value == true
    }

    @Synchronized
    fun getOrCreateReconnectCredential(pairing: VerifiedPairing): ReconnectCredential {
        require(isTrusted(pairing)) { "The Windows identity is not trusted" }
        val existing = readReconnectCredential(pairing)
        if (existing != null) return existing
        val token = ByteArray(32).also(SecureRandom()::nextBytes)
        check(preferences.edit()
            .putString("${pairing.deviceId}.reconnect", encrypt(token))
            .commit()) { "The reconnect credential could not be saved" }
        return ReconnectCredential(senderDeviceId, token)
    }

    fun getReconnectCredential(pairing: VerifiedPairing): ReconnectCredential? {
        if (!isTrusted(pairing)) return null
        if (!preferences.getBoolean("${pairing.deviceId}.reconnect_provisioned", false)) return null
        return readReconnectCredential(pairing)
    }

    fun markReconnectProvisioned(pairing: VerifiedPairing) {
        require(readReconnectCredential(pairing) != null) { "The reconnect credential is missing" }
        check(preferences.edit()
            .putBoolean("${pairing.deviceId}.reconnect_provisioned", true)
            .commit()) { "The reconnect setup could not be confirmed" }
    }

    private fun readReconnectCredential(pairing: VerifiedPairing): ReconnectCredential? {
        if (!isTrusted(pairing)) return null
        val encrypted = preferences.getString("${pairing.deviceId}.reconnect", null) ?: return null
        return ReconnectCredential(senderDeviceId, decrypt(encrypted))
    }

    private fun encrypt(plaintext: ByteArray): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, wrappingKey())
        val ciphertext = cipher.doFinal(plaintext)
        val combined = cipher.iv + ciphertext
        return Base64.getUrlEncoder().withoutPadding().encodeToString(combined)
    }

    private fun decrypt(encoded: String): ByteArray {
        val combined = Base64.getUrlDecoder().decode(encoded)
        require(combined.size > 12) { "The stored reconnect credential is invalid" }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(
            Cipher.DECRYPT_MODE,
            wrappingKey(),
            GCMParameterSpec(128, combined.copyOfRange(0, 12)),
        )
        return cipher.doFinal(combined.copyOfRange(12, combined.size)).also {
            require(it.size == 32) { "The stored reconnect credential is invalid" }
        }
    }

    private fun wrappingKey(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").run {
            init(
                KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build(),
            )
            generateKey()
        }
    }

    private companion object {
        const val KEY_ALIAS = "abot-reconnect-wrap-v1"
    }
}
