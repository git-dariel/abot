package app.abot.android

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.SystemClock
import android.view.HapticFeedbackConstants
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.ImageProcessingOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.gesturerecognizer.GestureRecognizer
import java.util.concurrent.Executors

class ReceiveGestureActivity : ComponentActivity() {
    companion object {
        const val EXTRA_TRANSFER_ID = "app.abot.android.extra.TRANSFER_ID"
    }

    private val worker = Executors.newSingleThreadExecutor()
    private val gate = StablePalmGate()
    private lateinit var previewView: PreviewView
    private var recognizer: GestureRecognizer? = null
    private var provider: ProcessCameraProvider? = null
    private var analysis: ImageAnalysis? = null
    private var running = false
    private var message by mutableStateOf("Starting camera...")
    private val permission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) startCamera() else message = "Camera permission denied. Use Receive without gesture or go back."
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (intent.getStringExtra(EXTRA_TRANSFER_ID).isNullOrBlank()) {
            finish()
            return
        }
        previewView = PreviewView(this)
        setContent {
            MaterialTheme(colorScheme = lightColorScheme(primary = Color.Black, onPrimary = Color.White)) {
                Surface(Modifier.fillMaxSize()) {
                    Column(
                        Modifier.systemBarsPadding().padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        Text("Receive this file", style = MaterialTheme.typography.headlineMedium)
                        Text("Show one open palm and hold it steady. File bytes start only after this phone confirms.")
                        AndroidView(
                            factory = { previewView },
                            modifier = Modifier.fillMaxWidth().weight(1f).background(Color.Black),
                        )
                        Text(message)
                        Text("Camera frames stay on this phone.", style = MaterialTheme.typography.bodySmall)
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedButton(onClick = { finish() }, modifier = Modifier.weight(1f)) { Text("Not now") }
                            Button(onClick = ::accept, modifier = Modifier.weight(1f)) { Text("Receive without gesture") }
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
        } else {
            permission.launch(Manifest.permission.CAMERA)
        }
    }

    private fun startCamera() {
        if (running || isFinishing) return
        running = true
        gate.reset()
        val future = ProcessCameraProvider.getInstance(this)
        future.addListener({
            try {
                provider = future.get()
                val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
                val frames = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                analysis = frames
                worker.execute {
                    if (recognizer == null) {
                        recognizer = GestureRecognizer.createFromOptions(
                            this,
                            GestureRecognizer.GestureRecognizerOptions.builder()
                                .setBaseOptions(BaseOptions.builder().setModelAssetPath("gesture_recognizer.task").build())
                                .setRunningMode(RunningMode.VIDEO)
                                .setNumHands(2)
                                .build(),
                        )
                    }
                }
                var lastFrame = 0L
                frames.setAnalyzer(worker) { frame ->
                    try {
                        val now = SystemClock.uptimeMillis()
                        if (running && recognizer != null && now - lastFrame >= 100) {
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
                                val confidence = ((gesture?.score() ?: 0f) * 100).toInt()
                                val accepted = gate.observe(
                                    gesture?.categoryName(),
                                    gesture?.score() ?: 0f,
                                    result.landmarks().size,
                                    now,
                                )
                                runOnUiThread {
                                    message = when {
                                        accepted -> "PALM CONFIRMED - receiving securely..."
                                        gesture?.categoryName() == "Open_Palm" -> "Palm found ($confidence%). Hold it steady."
                                        result.landmarks().isNotEmpty() -> "Hand found. Open your palm fully ($confidence%)."
                                        else -> "Show one open palm inside the camera."
                                    }
                                    if (accepted) {
                                        previewView.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                                        accept()
                                    }
                                }
                            } finally {
                                image.close()
                                bitmap.recycle()
                            }
                        }
                    } catch (_: Exception) {
                        runOnUiThread { message = "Gesture recognition failed. Use Receive without gesture." }
                    } finally {
                        frame.close()
                    }
                }
                provider!!.bindToLifecycle(this, CameraSelector.DEFAULT_FRONT_CAMERA, preview, frames)
            } catch (_: Exception) {
                running = false
                message = "Front camera unavailable. Use Receive without gesture."
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun accept() {
        if (isFinishing) return
        running = false
        analysis?.clearAnalyzer()
        provider?.unbindAll()
        setResult(
            RESULT_OK,
            Intent().putExtra(EXTRA_TRANSFER_ID, intent.getStringExtra(EXTRA_TRANSFER_ID)),
        )
        finish()
    }

    override fun onPause() {
        running = false
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
