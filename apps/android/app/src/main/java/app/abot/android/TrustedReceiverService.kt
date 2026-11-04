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
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class TrustedReceiverService : Service() {
    companion object {
        const val ACTION_OFFER_AVAILABLE = "app.abot.android.action.OFFER_AVAILABLE"
        const val ACTION_TRANSFER_COMPLETED = "app.abot.android.action.TRANSFER_COMPLETED"
        const val EXTRA_TRANSFER_ID = "transferId"
        const val EXTRA_FILE_NAME = "fileName"
        const val EXTRA_FILE_SIZE = "fileSize"
        private const val ACTION_START = "app.abot.android.action.START_RECEIVER"
        private const val ACTION_ACCEPT = "app.abot.android.action.ACCEPT_TRANSFER"
        private const val ACTION_DECLINE = "app.abot.android.action.DECLINE_TRANSFER"
        private const val READY_CHANNEL = "abot_receiver"
        private const val TRANSFER_CHANNEL = "abot_transfers"
        private const val FOREGROUND_ID = 100
        private const val OFFER_ID = 101

        @Volatile private var pendingSnapshot: ReverseOffer? = null
        @Volatile private var lastCompletedFile: String? = null

        fun start(context: Context) {
            ContextCompat.startForegroundService(
                context,
                Intent(context, TrustedReceiverService::class.java).setAction(ACTION_START),
            )
        }

        internal fun pendingOffer(transferId: String? = null): ReverseOffer? = pendingSnapshot?.takeIf {
            transferId == null || it.transferId.toString() == transferId
        }

        fun consumeCompletedFile(): String? = lastCompletedFile.also { lastCompletedFile = null }

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
    private val decisionLock = Any()
    private lateinit var trustedDevices: TrustedDeviceStore
    private lateinit var discovery: TrustedDeviceDiscovery
    private var pairing: VerifiedPairing? = null
    private var pending: PendingDecision? = null
    private var destroyed = false

    override fun onCreate() {
        super.onCreate()
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
            else -> if (trustedDevices.hasProvisionedReconnectCredential()) scheduleWork(0) else stopSelf()
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun scheduleWork(delayMs: Long) {
        if (destroyed) return
        mainHandler.postDelayed({
            if (destroyed || polling.get()) return@postDelayed
            pairing?.let(::poll) ?: discover()
        }, delayMs)
    }

    private fun discover() {
        updateForeground("Finding your trusted PC...")
        discovery.discover(
            onFound = { found ->
                pairing = found.pairing
                updateForeground("Ready for files from Windows")
                poll(found.pairing)
            },
            onFinished = { message ->
                updateForeground(message)
                scheduleWork(5_000)
            },
        )
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
                if (
                    detail.contains("Connection refused", ignoreCase = true) ||
                    detail.contains("timed out", ignoreCase = true) ||
                    detail.contains("reset", ignoreCase = true)
                ) {
                    pairing = null
                }
                updateForeground(detail)
            } finally {
                synchronized(decisionLock) {
                    pending = null
                    pendingSnapshot = null
                }
                getSystemService(NotificationManager::class.java).cancel(OFFER_ID)
                polling.set(false)
                scheduleWork(3_000)
            }
        }
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
        discovery.stop()
        synchronized(decisionLock) { pending?.result?.complete(false) }
        worker.shutdownNow()
        pendingSnapshot = null
        super.onDestroy()
    }
}
