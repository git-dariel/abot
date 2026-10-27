package app.abot.android

import android.content.Context

internal class TrustedDeviceStore(context: Context) {
    private val preferences = context.getSharedPreferences("trusted_devices_v1", Context.MODE_PRIVATE)

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
        preferences.edit().remove("$deviceId.key").remove("$deviceId.name").apply()
    }
}
