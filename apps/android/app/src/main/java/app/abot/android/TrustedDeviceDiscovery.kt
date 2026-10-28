package app.abot.android

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import android.os.Handler
import android.os.Looper
import java.nio.charset.StandardCharsets
import java.util.concurrent.atomic.AtomicBoolean

internal data class DiscoveredTrustedDevice(
    val pairing: VerifiedPairing,
    val restorablePairingLink: String,
)

internal class TrustedDeviceDiscovery(
    context: Context,
    private val trustedDevices: TrustedDeviceStore,
) {
    private val nsdManager = context.getSystemService(NsdManager::class.java)
    private val wifiManager = context.applicationContext.getSystemService(WifiManager::class.java)
    private val mainHandler = Handler(Looper.getMainLooper())
    private val active = AtomicBoolean(false)
    private var multicastLock: WifiManager.MulticastLock? = null
    private var listener: NsdManager.DiscoveryListener? = null
    private var timeoutRunnable: Runnable? = null

    fun discover(
        onFound: (DiscoveredTrustedDevice) -> Unit,
        onFinished: (String) -> Unit,
    ) {
        stop()
        active.set(true)
        multicastLock = wifiManager.createMulticastLock("abot-trusted-discovery").apply {
            setReferenceCounted(false)
            acquire()
        }
        val discoveryListener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(serviceType: String) = Unit

            override fun onServiceFound(service: NsdServiceInfo) {
                if (
                    !active.get() ||
                    service.serviceType.trimEnd('.') != SERVICE_TYPE.trimEnd('.')
                ) return
                @Suppress("DEPRECATION")
                nsdManager.resolveService(service, object : NsdManager.ResolveListener {
                    override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) = Unit

                    override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                        if (!active.get()) return
                        val discovered = runCatching { verifiedCandidate(serviceInfo) }.getOrNull() ?: return
                        mainHandler.post {
                            if (active.compareAndSet(true, false)) {
                                stopInternal()
                                onFound(discovered)
                            }
                        }
                    }
                })
            }

            override fun onServiceLost(service: NsdServiceInfo) = Unit

            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                finish("Trusted-device discovery could not start.", onFinished)
            }

            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) = Unit

            override fun onDiscoveryStopped(serviceType: String) = Unit
        }
        listener = discoveryListener
        nsdManager.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, discoveryListener)
        timeoutRunnable = Runnable {
            finish("No trusted Windows receiver was found. Start the receiver or scan its QR code.", onFinished)
        }.also { mainHandler.postDelayed(it, DISCOVERY_TIMEOUT_MS) }
    }

    fun stop() {
        active.set(false)
        stopInternal()
    }

    private fun finish(message: String, callback: (String) -> Unit) {
        if (!active.compareAndSet(true, false)) return
        stopInternal()
        mainHandler.post { callback(message) }
    }

    private fun stopInternal() {
        timeoutRunnable?.let(mainHandler::removeCallbacks)
        timeoutRunnable = null
        listener?.let {
            runCatching { nsdManager.stopServiceDiscovery(it) }
        }
        listener = null
        multicastLock?.let { lock ->
            if (lock.isHeld) lock.release()
        }
        multicastLock = null
    }

    @Suppress("DEPRECATION")
    private fun verifiedCandidate(service: NsdServiceInfo): DiscoveredTrustedDevice {
        val attributes = service.attributes
        val partCount = attributes["pairing_parts"]
            ?.toString(StandardCharsets.UTF_8)
            ?.toIntOrNull()
            ?: throw IllegalArgumentException("Discovery invitation is incomplete")
        require(partCount in 1..MAX_PAIRING_PARTS) { "Discovery invitation is too large" }
        val pairingData = buildString {
            repeat(partCount) { index ->
                val part = attributes["pairing_$index"]
                    ?: throw IllegalArgumentException("Discovery invitation is incomplete")
                append(part.toString(StandardCharsets.UTF_8))
            }
        }
        val pairingUri = "abot://pair?data=$pairingData"
        val pairing = PairingInvitationVerifier.verify(pairingUri)
        require(trustedDevices.isTrusted(pairing)) { "Discovered identity is not trusted" }
        require(service.port == pairing.port) { "Discovery endpoint does not match its signature" }
        require(service.host.hostAddress == pairing.host) { "Discovery address does not match its signature" }
        val credential = trustedDevices.getReconnectCredential(pairing)
            ?: throw IllegalArgumentException("Trusted reconnect has not been provisioned")
        return DiscoveredTrustedDevice(
            pairing = TrustedReconnectProtocol.create(pairing, credential),
            restorablePairingLink = TrustedReconnectProtocol.mark(pairingUri),
        )
    }

    private companion object {
        const val SERVICE_TYPE = "_airbridge._tcp."
        const val DISCOVERY_TIMEOUT_MS = 8_000L
        const val MAX_PAIRING_PARTS = 8
    }
}
