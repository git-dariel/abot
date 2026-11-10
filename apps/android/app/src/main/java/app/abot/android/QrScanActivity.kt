package app.abot.android

import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import androidx.activity.ComponentActivity
import com.journeyapps.barcodescanner.CaptureManager
import com.journeyapps.barcodescanner.DecoratedBarcodeView

class QrScanActivity : ComponentActivity() {
    private lateinit var capture: CaptureManager
    private lateinit var scanner: DecoratedBarcodeView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_qr_scan)
        scanner = findViewById(R.id.zxing_barcode_scanner)
        capture = CaptureManager(this, scanner).also {
            it.initializeFromIntent(intent, savedInstanceState)
            it.decode()
        }
        findViewById<View>(R.id.qr_scan_close).setOnClickListener {
            setResult(RESULT_CANCELED)
            finish()
        }
    }

    override fun onResume() {
        super.onResume()
        capture.onResume()
    }

    override fun onPause() {
        capture.onPause()
        super.onPause()
    }

    override fun onDestroy() {
        capture.onDestroy()
        super.onDestroy()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        capture.onSaveInstanceState(outState)
        super.onSaveInstanceState(outState)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean =
        scanner.onKeyDown(keyCode, event) || super.onKeyDown(keyCode, event)
}
