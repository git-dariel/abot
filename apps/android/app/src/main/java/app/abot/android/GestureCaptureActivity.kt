package app.abot.android

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
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
import androidx.compose.ui.graphics.Color
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
    }

    private val worker = Executors.newSingleThreadExecutor()
    private val sequence = GestureSequence()
    private var recognizer: GestureRecognizer? = null
    private var provider: ProcessCameraProvider? = null
    private var analysis: ImageAnalysis? = null
    private lateinit var previewView: PreviewView
    @Volatile private var running = false
    @Volatile private var generation = 0
    private var message by mutableStateOf("Camera starting...")
    private val permission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) startCamera() else message = "Camera permission denied. Go back and use Offer file to Windows."
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        previewView = PreviewView(this)
        val mediaUri = intent.getStringExtra(EXTRA_MEDIA_URI)?.let(Uri::parse)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(primary = Color.White, onPrimary = Color.Black)) {
                Surface(Modifier.fillMaxSize()) {
                    Column(Modifier.systemBarsPadding().padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Text("Grab this photo", style = MaterialTheme.typography.headlineMedium)
                        Text("Show one open palm, then close it into a fist. Windows must independently confirm with its own fist-to-palm gesture before any file bytes move.")
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .background(Color(0xFF111111)),
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
                                modifier = Modifier.align(Alignment.BottomEnd).size(width = 132.dp, height = 176.dp),
                                color = Color.Black,
                            ) {
                                AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())
                            }
                        }
                        Text(message)
                        Text("Camera frames stay on this phone.", style = MaterialTheme.typography.bodySmall)
                        Button(onClick = { finish() }, modifier = Modifier.fillMaxWidth()) { Text("Cancel gesture") }
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
                                val triggered = sequence.observe(gesture?.categoryName(), gesture?.score() ?: 0f, result.landmarks().size, now)
                                val status = when {
                                    sequence.armed && gesture?.categoryName() == "Closed_Fist" -> "Fist found. Hold it steady to offer."
                                    sequence.armed -> "Palm locked. Close your fist."
                                    gesture?.categoryName() == "Open_Palm" -> "Palm found. Hold it steady."
                                    gesture?.categoryName() == "Closed_Fist" -> "Open your palm first."
                                    else -> "Hold one open palm inside the camera preview."
                                }
                                runOnUiThread {
                                    if (running && session == generation && !isFinishing) {
                                        message = status
                                        if (triggered) {
                                            running = false
                                            setResult(RESULT_OK)
                                            finish()
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
