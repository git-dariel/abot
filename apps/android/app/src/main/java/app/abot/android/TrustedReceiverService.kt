package app.abot.android

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import java.io.IOException
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors
import java.util.concurrent.TimeoutException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

internal data class TrustedConnectionSnapshot(
    val pairingLink: String? = null,
    val message: String = "Windows receiver discovery is off.",
    val isSearching: Boolean = false,
    val discoveryEnabled: Boolean = false,
)

class TrustedReceiverService : Service() {
    companion object {
        const val ACTION_OFFER_AVAILABLE = "app.abot.android.action.OFFER_AVAILABLE"
        const val ACTION_TRANSFER_COMPLETED = "app.abot.android.action.TRANSFER_COMPLETED"
        const val ACTION_CONNECTION_CHANGED = "app.abot.android.action.CONNECTION_CHANGED"
        const val EXTRA_TRANSFER_ID = "transferId"
        const val EXTRA_FILE_NAME = "fileName"
        const val EXTRA_FILE_SIZE = "fileSize"
        const val EXTRA_PAIRING_LINK = "pairingLink"
        const val EXTRA_CONNECTION_MESSAGE = "connectionMessage"
        const val EXTRA_IS_SEARCHING = "isSearching"
        private const val ACTION_START = "app.abot.android.action.START_RECEIVER"
        private const val ACTION_STOP = "app.abot.android.action.STOP_RECEIVER"
        private const val ACTION_RECONNECT = "app.abot.android.action.RECONNECT_RECEIVER"
        private const val ACTION_ACCEPT = "app.abot.android.action.ACCEPT_TRANSFER"
        private const val ACTION_DECLINE = "app.abot.android.action.DECLINE_TRANSFER"
        private const val READY_CHANNEL = "abot_receiver"
        private const val TRANSFER_CHANNEL = "abot_transfers"
        private const val FOREGROUND_ID = 100
        private const val OFFER_ID = 101

        @Volatile private var pendingSnapshot: ReverseOffer? = null
        @Volatile private var lastCompletedFile: String? = null
        @Volatile private var trustedConnectionSnapshot = TrustedConnectionSnapshot()
        @Volatile private var discoveryAllowed = false
        @Volatile private var serviceRunning = false

        fun enableDiscovery(context: Context) {
            discoveryAllowed = true
            ContextCompat.startForegroundService(
                context,
                Intent(context, TrustedReceiverService::class.java).setAction(ACTION_START),
            )
        }

        fun disableDiscovery(context: Context) {
            discoveryAllowed = false
            trustedConnectionSnapshot = TrustedConnectionSnapshot()
            if (serviceRunning) {
                context.startService(
                    Intent(context, TrustedReceiverService::class.java).setAction(ACTION_STOP),
                )
            }
        }

        internal fun pendingOffer(transferId: String? = null): ReverseOffer? = pendingSnapshot?.takeIf {
            transferId == null || it.transferId.toString() == transferId
        }

        fun consumeCompletedFile(): String? = lastCompletedFile.also { lastCompletedFile = null }

        internal fun connectionSnapshot(): TrustedConnectionSnapshot = trustedConnectionSnapshot

        internal fun isDiscoveryEnabled(): Boolean = discoveryAllowed

        fun reconnect(context: Context) {
            if (!discoveryAllowed) return
            ContextCompat.startForegroundService(
                context,
                Intent(context, TrustedReceiverService::class.java).setAction(ACTION_RECONNECT),
            )
        }

        fun accept(context: Context, transferId: String) = sendDecision(context, ACTION_ACCEPT, transferId)

        fun decline(context: Context, transferId: String) = sendDecision(context, ACTION_DECLINE, transferId)

        private fun sendDecision(context: Context, action: String, transferId: String) {
            context.startService(
                Intent(context, TrustedReceiverService::class.java)
                    .setAction(action)
                    .putExtra(EXTRA_TRANSFER_ID, transferId),
            )
        }
    }

    private data class PendingDecision(
        val offer: ReverseOffer,
        val result: CompletableFuture<Boolean>,
    )

    private val worker = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val polling = AtomicBoolean(false)
    private val discovering = AtomicBoolean(false)
    private val decisionLock = Any()
    private val reconnectBackoff = ReconnectBackoff()
    private lateinit var trustedDevices: TrustedDeviceStore
    private lateinit var discovery: TrustedDeviceDiscovery
    private var pairing: VerifiedPairing? = null
    private var pairingLink: String? = null
    private var pending: PendingDecision? = null
    private var scheduledWork: Runnable? = null
    private var destroyed = false

    override fun onCreate() {
        super.onCreate()
        serviceRunning = true
        trustedDevices = TrustedDeviceStore(this)
        discovery = TrustedDeviceDiscovery(this, trustedDevices)
        createChannels()
        val notification = readyNotification("Looking for your trusted PC...")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(FOREGROUND_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)
        } else {
            startForeground(FOREGROUND_ID, notification)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_ACCEPT -> resolveDecision(intent.getStringExtra(EXTRA_TRANSFER_ID), true)
            ACTION_DECLINE -> resolveDecision(intent.getStringExtra(EXTRA_TRANSFER_ID), false)
            ACTION_RECONNECT -> if (discoveryAllowed) restartConnection()
            ACTION_STOP -> stopDiscovery()
            ACTION_START -> {
                discoveryAllowed = true
                if (trustedDevices.hasProvisionedReconnectCredential()) scheduleWork(0) else stopDiscovery()
            }
            else -> stopDiscovery()
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun scheduleWork(delayMs: Long) {
        if (destroyed || !discoveryAllowed) return
        mainHandler.post {
            if (destroyed || !discoveryAllowed) return@post
            scheduledWork?.let(mainHandler::removeCallbacks)
            val work = Runnable {
                scheduledWork = null
                if (destroyed || !discoveryAllowed || polling.get() || discovering.get()) return@Runnable
                pairing?.let(::poll) ?: discover()
            }
            scheduledWork = work
            mainHandler.postDelayed(work, delayMs)
        }
    }

    private fun discover() {
        if (!discoveryAllowed) return
        if (!discovering.compareAndSet(false, true)) return
        publishConnection(null, "Finding your trusted PC...", isSearching = true)
        discovery.discover(
            onFound = { found ->
                discovering.set(false)
                pairing = found.pairing
                pairingLink = found.restorablePairingLink
                reconnectBackoff.reset()
                publishConnection(found.restorablePairingLink, "Connected to your trusted PC", isSearching = false)
                poll(found.pairing)
            },
            onFinished = { message ->
                discovering.set(false)
                publishConnection(null, message, isSearching = false)
                scheduleWork(reconnectBackoff.nextDelay())
            },
        )
    }

    private fun restartConnection() {
        if (!discoveryAllowed) return
        pairing = null
        pairingLink = null
        reconnectBackoff.reset()
        publishConnection(null, "Reconnecting to your trusted PC...", isSearching = true)
        if (!polling.get()) {
            discovery.stop()
            discovering.set(false)
            scheduleWork(0)
        }
    }

    private fun poll(currentPairing: VerifiedPairing) {
        if (!polling.compareAndSet(false, true)) return
        worker.execute {
            try {
                val received = ReverseTransferClient.poll(
                    resolver = contentResolver,
                    cacheDirectory = cacheDir,
                    pairing = currentPairing,
                    decide = ::awaitDecision,
                    onProgress = { receivedBytes, total ->
                        val percent = if (total == 0L) 100 else ((receivedBytes * 100) / total).toInt()
                        updateForeground("Receiving file... $percent%")
                    },
                )
                if (received != null) {
                    lastCompletedFile = received
                    notifyCompleted(received)
                    sendInternalBroadcast(
                        ACTION_TRANSFER_COMPLETED,
                        fileName = received,
                    )
                }
            } catch (error: Exception) {
                val detail = error.message ?: "Trusted receiver connection failed"
                if (shouldRefreshEndpoint(error)) {
                    pairing = null
                    pairingLink = null
                }
                publishConnection(pairingLink, connectionFailureMessage(error, detail), isSearching = false)
            } finally {
                synchronized(decisionLock) {
                    pending = null
                    pendingSnapshot = null
                }
                getSystemService(NotificationManager::class.java).cancel(OFFER_ID)
                polling.set(false)
                if (discoveryAllowed) {
                    scheduleWork(if (pairing == null) reconnectBackoff.nextDelay() else 3_000)
                } else {
                    stopWhenIdle()
                }
            }
        }
    }

    private fun stopDiscovery() {
        discoveryAllowed = false
        scheduledWork?.let(mainHandler::removeCallbacks)
        scheduledWork = null
        discovery.stop()
        discovering.set(false)
        pairing = null
        pairingLink = null
        publishConnection(null, "Windows receiver discovery is off.", isSearching = false)
        stopWhenIdle()
    }

    private fun stopWhenIdle() {
        val activeTransfer = synchronized(decisionLock) { pending != null }
        if (activeTransfer) {
            updateForeground("Finishing the active transfer...")
            return
        }
        stopForeground(STOP_FOREGROUND_REMOVE)
        getSystemService(NotificationManager::class.java).cancel(FOREGROUND_ID)
        stopSelf()
    }

    private fun shouldRefreshEndpoint(error: Exception): Boolean =
        error is IOException || error is SecurityException || error is IllegalArgumentException

    private fun connectionFailureMessage(error: Exception, detail: String): String = when {
        error is TimeoutException -> "The incoming transfer expired. ABOT is ready to try again."
        shouldRefreshEndpoint(error) -> "Connection interrupted. Finding your trusted PC again..."
        else -> detail
    }

    private fun awaitDecision(offer: ReverseOffer): Boolean {
        val future = CompletableFuture<Boolean>()
        synchronized(decisionLock) {
            pending = PendingDecision(offer, future)
            pendingSnapshot = offer
        }
        notifyOffer(offer)
        sendInternalBroadcast(
            ACTION_OFFER_AVAILABLE,
            transferId = offer.transferId.toString(),
            fileName = offer.name,
            fileSize = offer.size,
        )
        return future.get(10, TimeUnit.MINUTES)
    }

    private fun resolveDecision(transferId: String?, accepted: Boolean) {
        val current = synchronized(decisionLock) {
            pending?.takeIf { it.offer.transferId.toString() == transferId }
        } ?: return
        if (current.result.complete(accepted)) {
            updateForeground(if (accepted) "Receiving ${current.offer.name}..." else "Transfer declined")
        }
    }

    private fun createChannels() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(READY_CHANNEL, "ABOT background receiver", NotificationManager.IMPORTANCE_LOW),
        )
        manager.createNotificationChannel(
            NotificationChannel(TRANSFER_CHANNEL, "Incoming ABOT transfers", NotificationManager.IMPORTANCE_HIGH),
        )
    }

    private fun readyNotification(text: String) = NotificationCompat.Builder(this, READY_CHANNEL)
        .setSmallIcon(R.drawable.ic_stat_abot)
        .setContentTitle("ABOT is ready")
        .setContentText(text)
        .setOngoing(true)
        .setOnlyAlertOnce(true)
        .setContentIntent(mainPendingIntent())
        .build()

    private fun updateForeground(text: String) {
        getSystemService(NotificationManager::class.java).notify(FOREGROUND_ID, readyNotification(text))
    }

    private fun publishConnection(link: String?, message: String, isSearching: Boolean) {
        trustedConnectionSnapshot = TrustedConnectionSnapshot(link, message, isSearching, discoveryAllowed)
        if (discoveryAllowed || synchronized(decisionLock) { pending != null }) updateForeground(message)
        sendBroadcast(
            Intent(ACTION_CONNECTION_CHANGED)
                .setPackage(packageName)
                .putExtra(EXTRA_PAIRING_LINK, link)
                .putExtra(EXTRA_CONNECTION_MESSAGE, message)
                .putExtra(EXTRA_IS_SEARCHING, isSearching),
        )
    }

    private fun notifyOffer(offer: ReverseOffer) {
        val transferId = offer.transferId.toString()
        val open = PendingIntent.getActivity(
            this,
            transferId.hashCode(),
            Intent(this, MainActivity::class.java)
                .setAction(MainActivity.ACTION_SHOW_RECEIVE)
                .putExtra(EXTRA_TRANSFER_ID, transferId)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val decline = PendingIntent.getService(
            this,
            transferId.hashCode() xor 0x4a2b,
            Intent(this, TrustedReceiverService::class.java)
                .setAction(ACTION_DECLINE)
                .putExtra(EXTRA_TRANSFER_ID, transferId),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(this, TRANSFER_CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_abot)
            .setContentTitle("File from Windows")
            .setContentText("${offer.name} - tap to receive with your palm")
            .setAutoCancel(false)
            .setContentIntent(open)
            .addAction(0, "Decline", decline)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        getSystemService(NotificationManager::class.java).notify(OFFER_ID, notification)
    }

    private fun notifyCompleted(fileName: String) {
        val notification = NotificationCompat.Builder(this, TRANSFER_CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_abot)
            .setContentTitle("File received")
            .setContentText("$fileName was verified and saved to Downloads/ABOT")
            .setAutoCancel(true)
            .setContentIntent(mainPendingIntent())
            .build()
        getSystemService(NotificationManager::class.java).notify(OFFER_ID, notification)
    }

    private fun mainPendingIntent(): PendingIntent = PendingIntent.getActivity(
        this,
        0,
        Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun sendInternalBroadcast(
        action: String,
        transferId: String? = null,
        fileName: String? = null,
        fileSize: Long? = null,
    ) {
        sendBroadcast(Intent(action).setPackage(packageName).apply {
            transferId?.let { putExtra(EXTRA_TRANSFER_ID, it) }
            fileName?.let { putExtra(EXTRA_FILE_NAME, it) }
            fileSize?.let { putExtra(EXTRA_FILE_SIZE, it) }
        })
    }

    override fun onDestroy() {
        destroyed = true
        scheduledWork?.let(mainHandler::removeCallbacks)
        scheduledWork = null
        discovery.stop()
        discovering.set(false)
        synchronized(decisionLock) { pending?.result?.complete(false) }
        worker.shutdownNow()
        pendingSnapshot = null
        getSystemService(NotificationManager::class.java).cancel(FOREGROUND_ID)
        serviceRunning = false
        discoveryAllowed = false
        trustedConnectionSnapshot = TrustedConnectionSnapshot()
        super.onDestroy()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        stopDiscovery()
        super.onTaskRemoved(rootIntent)
    }
}
