package app.abot.android

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import android.view.HapticFeedbackConstants
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.ImageProcessingOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.gesturerecognizer.GestureRecognizer
import java.util.concurrent.Executors

class GestureCaptureActivity : ComponentActivity() {
    companion object {
        const val EXTRA_MEDIA_URI = "app.abot.android.extra.MEDIA_URI"
        private const val TAG = "ABOT-Gesture"
    }

    private val worker = Executors.newSingleThreadExecutor()
    private val sequence = GestureSequence()
    private var recognizer: GestureRecognizer? = null
    private var provider: ProcessCameraProvider? = null
    private var analysis: ImageAnalysis? = null
    private lateinit var previewView: PreviewView
    @Volatile private var running = false
    @Volatile private var generation = 0
    private var lastDiagnosticLog = 0L
    private var message by mutableStateOf("Camera starting...")
    private val permission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) startCamera() else message = "Camera permission denied. Go back and use Offer file to Windows."
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        previewView = PreviewView(this)
        val mediaUri = intent.getStringExtra(EXTRA_MEDIA_URI)?.let(Uri::parse)
        setContent {
            AbotTheme {
                Surface(Modifier.fillMaxSize(), color = AbotPaper) {
                    Column(Modifier.systemBarsPadding().padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        AbotSectionLabel("Send gesture")
                        Text("Grab this photo.", style = MaterialTheme.typography.headlineLarge)
                        Text("Open palm → closed fist", color = AbotBlue, style = MaterialTheme.typography.titleLarge)
                        Text("The gesture prepares an offer. Windows still confirms before any bytes move.", color = AbotMuted, style = MaterialTheme.typography.bodySmall)
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .background(AbotInk),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (mediaUri != null) {
                                MediaStoreImage(
                                    uri = mediaUri,
                                    requestedSize = 1600,
                                    contentScale = ContentScale.Fit,
                                    modifier = Modifier.fillMaxSize(),
                                    contentDescription = "Photo ready to offer",
                                )
                            }
                            Surface(
                                modifier = Modifier.align(Alignment.BottomEnd).padding(10.dp).size(width = 150.dp, height = 200.dp),
                                color = AbotInk,
                                border = androidx.compose.foundation.BorderStroke(2.dp, AbotBlue),
                            ) {
                                AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())
                            }
                        }
                        Surface(color = AbotSoft, modifier = Modifier.fillMaxWidth()) {
                            Text(message, Modifier.padding(14.dp), fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                        }
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text("Camera stays on this phone", style = MaterialTheme.typography.bodySmall, color = AbotMuted, modifier = Modifier.weight(1f))
                            OutlinedButton(
                                onClick = { finish() },
                                border = androidx.compose.foundation.BorderStroke(1.dp, AbotInk),
                                shape = androidx.compose.foundation.shape.RoundedCornerShape(2.dp),
                            ) { Text("Cancel") }
                        }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            startCamera()
        } else permission.launch(Manifest.permission.CAMERA)
    }

    private fun startCamera() {
        if (running || isFinishing) return
        running = true
        val session = ++generation
        val future = ProcessCameraProvider.getInstance(this)
        future.addListener({
            if (!running || session != generation || isDestroyed || isFinishing) return@addListener
            try {
                provider = future.get()
                val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
                val frames = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                analysis = frames
                worker.execute {
                    sequence.reset()
                    try {
                        if (recognizer == null) recognizer = GestureRecognizer.createFromOptions(
                            this,
                            GestureRecognizer.GestureRecognizerOptions.builder()
                                .setBaseOptions(BaseOptions.builder().setModelAssetPath("gesture_recognizer.task").build())
                                .setRunningMode(RunningMode.VIDEO)
                                .setNumHands(2)
                                .build(),
                        )
                        Log.i(TAG, "MediaPipe recognizer ready")
                    } catch (_: Exception) {
                        runOnUiThread { if (session == generation) stopWithError("Gesture model could not start. Use the send button instead.") }
                    }
                }
                var lastFrame = 0L
                frames.setAnalyzer(worker) { frame ->
                    try {
                        val now = SystemClock.uptimeMillis()
                        if (running && session == generation && recognizer != null && now - lastFrame >= 100) {
                            lastFrame = now
                            val bitmap = frame.toBitmap()
                            val image = BitmapImageBuilder(bitmap).build()
                            try {
                                val result = recognizer!!.recognizeForVideo(
                                    image,
                                    ImageProcessingOptions.builder().setRotationDegrees(frame.imageInfo.rotationDegrees).build(),
                                    now,
                                )
                                val gesture = result.gestures().firstOrNull()?.firstOrNull()
                                val wasArmed = sequence.armed
                                val triggered = sequence.observe(gesture?.categoryName(), gesture?.score() ?: 0f, result.landmarks().size, now)
                                if (now - lastDiagnosticLog >= 1_000) {
                                    lastDiagnosticLog = now
                                    Log.d(TAG, "hands=${result.landmarks().size} pose=${gesture?.categoryName()} confidence=${gesture?.score()} armed=${sequence.armed}")
                                }
                                val confidence = ((gesture?.score() ?: 0f) * 100).toInt()
                                val status = when {
                                    triggered -> "PHOTO GRABBED — sending the offer to Windows..."
                                    sequence.armed && gesture?.categoryName() == "Closed_Fist" -> "Fist found ($confidence%). Keep it closed."
                                    sequence.armed -> "PALM LOCKED — now close your fist."
                                    gesture?.categoryName() == "Open_Palm" && gesture.score() >= GestureSequence.MIN_CONFIDENCE -> "Palm found ($confidence%). Hold it steady."
                                    gesture?.categoryName() == "Closed_Fist" -> "Fist found ($confidence%). Open your palm first."
                                    result.landmarks().isNotEmpty() -> "Hand found, but no clear palm yet ($confidence%). Move it fully inside the preview."
                                    else -> "No hand detected. Show one open palm inside the camera preview."
                                }
                                runOnUiThread {
                                    if (running && session == generation && !isFinishing) {
                                        message = status
                                        if (!wasArmed && sequence.armed) {
                                            previewView.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                                        }
                                        if (triggered) {
                                            running = false
                                            analysis?.clearAnalyzer()
                                            provider?.unbindAll()
                                            previewView.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                                            Log.i(TAG, "Palm-to-fist sequence triggered")
                                            setResult(RESULT_OK)
                                            previewView.postDelayed({ if (!isFinishing) finish() }, 500)
                                        }
                                    }
                                }
                            } finally {
                                image.close()
                                bitmap.recycle()
                            }
                        }
                    } catch (_: Exception) {
                        runOnUiThread { if (session == generation) stopWithError("Camera recognition failed. Use the send button instead.") }
                    } finally { frame.close() }
                }
                provider!!.bindToLifecycle(this, CameraSelector.DEFAULT_FRONT_CAMERA, preview, frames)
            } catch (_: Exception) {
                stopWithError("Front camera unavailable. Close other camera apps or use the send button.")
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun stopWithError(text: String) {
        running = false
        analysis?.clearAnalyzer()
        provider?.unbindAll()
        message = text
    }

    override fun onPause() {
        running = false
        generation++
        analysis?.clearAnalyzer()
        provider?.unbindAll()
        super.onPause()
    }

    override fun onDestroy() {
        worker.execute { recognizer?.close(); recognizer = null }
        worker.shutdown()
        super.onDestroy()
    }
}
