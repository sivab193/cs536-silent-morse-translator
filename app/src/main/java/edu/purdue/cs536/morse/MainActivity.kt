package edu.purdue.cs536.morse

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.Matrix
import android.os.Bundle
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AlertDialog
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import java.nio.ByteBuffer
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.math.hypot

class MainActivity : AppCompatActivity() {
    private enum class Mode { TAP, LIGHT, REMOTE }
    private enum class TapMode { PINCH, DIP }

    private lateinit var previewView: PreviewView
    private lateinit var overlay: MorseOverlayView
    private lateinit var controls: LinearLayout
    private lateinit var retryButton: ActionControl
    private val modeControls = mutableMapOf<Mode, ActionControl>()
    private lateinit var cameraExecutor: ExecutorService
    private lateinit var logger: SessionLogger
    private lateinit var decoder: MorseDecoder
    private lateinit var remoteVision: RemoteVisionClient
    private var handTracker: HandTracker? = null
    private val debouncer = SignalDebouncer(40)
    private val lightDetector = LightSignalDetector()
    private val mainHandler = Handler(Looper.getMainLooper())

    @Volatile private var mode = Mode.TAP
    @Volatile private var tapMode = TapMode.PINCH
    @Volatile private var signalOn = false
    @Volatile private var latestHandAtMs = 0L
    @Volatile private var detectorFailed = false
    @Volatile private var remoteBusy = false
    @Volatile private var remoteText = ""
    @Volatile private var fps = 0.0
    private var inputFrames = 0L
    private var analyzeStride = 2
    private var fpsWindowStarted = SystemClock.elapsedRealtime()
    private var fpsFrames = 0

    private val permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) startCamera() else showCameraError("Camera permission denied")
    }

    private val ticker = object : Runnable {
        override fun run() {
            decoder.tick(SystemClock.elapsedRealtime())
            if (mode == Mode.TAP && latestHandAtMs > 0 && SystemClock.elapsedRealtime() - latestHandAtMs > 5_000L) {
                applyRawSignal(false, "NO HAND — show your hand to the camera", null)
            }
            refreshDecoderUi()
            mainHandler.postDelayed(this, 50L)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.decorView.windowInsetsController?.hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
        }
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            )

        cameraExecutor = Executors.newSingleThreadExecutor()
        logger = SessionLogger(this, "tap")
        decoder = MorseDecoder { event -> logger.add(event) }
        val endpoint = intent.getStringExtra("endpoint")
            ?: getSharedPreferences("remote", MODE_PRIVATE).getString("endpoint", null)
            ?: "http://127.0.0.1:8765"
        remoteVision = RemoteVisionClient(endpoint)
        buildUi()
        initializeHandTracker()
        mainHandler.post(ticker)

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            startCamera()
        } else permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    private fun buildUi() {
        val root = FrameLayout(this).apply { setBackgroundColor(android.graphics.Color.BLACK) }
        previewView = PreviewView(this).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
        root.addView(previewView, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))

        overlay = MorseOverlayView(this)
        root.addView(overlay, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))

        controls = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(dp(4), dp(4), dp(4), dp(4))
            background = GradientDrawable().apply {
                cornerRadius = dp(16).toFloat()
                setColor(Color.argb(238, 28, 28, 30))
            }
            elevation = dp(8).toFloat()
        }
        val controlsHeight = dp(68)
        val controlsMargin = dp(8)
        overlay.controlsHeightPx = controlsHeight + controlsMargin * 2
        root.addView(controls, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, controlsHeight, Gravity.BOTTOM).apply {
            setMargins(controlsMargin, 0, controlsMargin, controlsMargin)
        })
        modeControls[Mode.TAP] = addControl("Hand", R.drawable.ic_hand, "Use hand gestures") { switchMode(Mode.TAP) }.apply {
            setOnLongClickListener {
            tapMode = if (tapMode == TapMode.PINCH) TapMode.DIP else TapMode.PINCH
            toast("Tap detector: ${tapMode.name.lowercase()}")
            logger.note("Tap detector changed to ${tapMode.name}")
            true
            }
        }
        modeControls[Mode.LIGHT] = addControl("Light", R.drawable.ic_light, "Read a light signal") { switchMode(Mode.LIGHT) }
        modeControls[Mode.REMOTE] = addControl("Phone", R.drawable.ic_phone, "Use phone OCR") { switchMode(Mode.REMOTE) }.apply {
            setOnLongClickListener {
                showEndpointDialog()
                true
            }
        }
        addControl("Clear", R.drawable.ic_clear, "Clear decoded message") {
            decoder.reset()
            debouncer.reset()
            signalOn = false
            logger.note("Text cleared")
            refreshDecoderUi()
        }
        addControl("Save", R.drawable.ic_save, "Save session log") {
            val file = logger.save(decoder.unitMs)
            toast("Saved ${file.name}")
        }
        addControl("Share", R.drawable.ic_share, "Share session log") { shareLatestLog() }
        updateModeControls()

        retryButton = ActionControl(this).apply {
            configure("Retry", R.drawable.ic_retry, "Retry camera connection")
            visibility = View.GONE
            setOnClickListener {
                visibility = View.GONE
                this@MainActivity.overlay.update { it.copy(cameraError = null) }
                if (ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                    startCamera()
                } else {
                    startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = android.net.Uri.parse("package:$packageName")
                    })
                }
            }
        }
        root.addView(retryButton, FrameLayout.LayoutParams(dp(112), dp(64), Gravity.CENTER).apply { topMargin = dp(112) })
        setContentView(root)
    }

    private fun addControl(label: String, icon: Int, description: String, action: () -> Unit): ActionControl {
        val button = ActionControl(this).apply {
            configure(label, icon, description)
            setOnClickListener { action() }
        }
        controls.addView(button, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f).apply {
            marginStart = dp(2)
            marginEnd = dp(2)
        })
        return button
    }

    private fun updateModeControls() {
        modeControls.forEach { (controlMode, control) -> control.isSelected = controlMode == mode }
    }

    private fun initializeHandTracker() {
        cameraExecutor.execute {
            try {
                handTracker = HandTracker(this, ::onHandResult) { message -> handleHandFailure(message) }
                logger.note("MediaPipe HandLandmarker loaded")
            } catch (error: Throwable) {
                handleHandFailure(error.message ?: error.toString())
            }
        }
    }

    private fun handleHandFailure(message: String) {
        if (detectorFailed) return
        detectorFailed = true
        Log.e(TAG, "MediaPipe HandLandmarker failed: $message")
        logger.note("MediaPipe failure: $message; falling back to LIGHT")
        runOnUiThread {
            toast("Hand model failed — using Light mode")
            switchMode(Mode.LIGHT, "Hand model error: ${message.take(45)}")
        }
    }

    private fun startCamera() {
        val future = ProcessCameraProvider.getInstance(this)
        future.addListener({
            try {
                val provider = future.get()
                provider.unbindAll()
                val preview = Preview.Builder().build().also { it.surfaceProvider = previewView.surfaceProvider }
                val analysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                    .setTargetResolution(android.util.Size(640, 480))
                    .build()
                    .also { it.setAnalyzer(cameraExecutor, ::analyzeFrame) }
                val selector = when {
                    provider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA) -> CameraSelector.DEFAULT_BACK_CAMERA
                    provider.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA) -> CameraSelector.DEFAULT_FRONT_CAMERA
                    else -> throw IllegalStateException("No usable camera")
                }
                provider.bindToLifecycle(this, selector, preview, analysis)
                overlay.update { it.copy(cameraError = null, detail = "Camera ready") }
                logger.note("Camera opened; selector=${if (selector == CameraSelector.DEFAULT_BACK_CAMERA) "BACK" else "FRONT"}")
            } catch (error: Throwable) {
                Log.e(TAG, "Camera start failed", error)
                logger.note("Camera error: ${error.message}")
                showCameraError("Camera not available: ${error.message ?: "unknown error"}")
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun analyzeFrame(image: ImageProxy) {
        val now = SystemClock.elapsedRealtime()
        updateFps(now)
        inputFrames++
        try {
            when (mode) {
                Mode.LIGHT -> {
                    val brightness = centerBrightness(image)
                    val raw = lightDetector.update(brightness, now)
                    val detail = "Light: ${brightness.toInt()} / threshold ${lightDetector.threshold.toInt()}"
                    applyRawSignal(raw, detail, null, lightDetector.prompt())
                }
                Mode.TAP -> {
                    if (detectorFailed) return
                    if (inputFrames % analyzeStride != 0L) return
                    val tracker = handTracker ?: return
                    tracker.detect(image.toRotatedBitmap(), now)
                }
                Mode.REMOTE -> {
                    if (remoteBusy || inputFrames % 15L != 0L) return
                    runRemoteOcr(image.toRotatedBitmap(), now)
                }
            }
        } catch (error: Throwable) {
            Log.e(TAG, "Frame analysis failed", error)
            logger.note("Frame analysis error: ${error.message}")
        } finally {
            image.close()
        }
    }

    private fun onHandResult(result: HandTracker.HandResult) {
        if (mode != Mode.TAP) return
        val points = result.points
        if (points == null || points.size < 21) {
            applyRawSignal(false, "Hand: NO HAND", null)
            return
        }
        latestHandAtMs = SystemClock.elapsedRealtime()
        val raw: Boolean
        val detail: String
        if (tapMode == TapMode.PINCH) {
            val p4 = points[4]
            val p8 = points[8]
            val p0 = points[0]
            val p9 = points[9]
            val pinch = hypot((p4.x - p8.x).toDouble(), (p4.y - p8.y).toDouble())
            val handSize = hypot((p0.x - p9.x).toDouble(), (p0.y - p9.y).toDouble()).coerceAtLeast(0.001)
            val ratio = pinch / handSize
            raw = ratio < if (signalOn) 0.37 else 0.33
            detail = "Hand: FOUND | pinch ${"%.2f".format(ratio)}"
        } else {
            raw = points[8].y > points[6].y
            detail = "Hand: FOUND | dip"
        }
        applyRawSignal(raw, detail, points)
    }

    private fun runRemoteOcr(bitmap: Bitmap, startedMs: Long) {
        remoteBusy = true
        remoteVision.ocr(bitmap) { result ->
            bitmap.recycle()
            remoteBusy = false
            result.onSuccess { response ->
                remoteText = response.text
                overlay.update {
                    it.copy(
                        mode = Mode.REMOTE.name,
                        detail = "Remote: ${SystemClock.elapsedRealtime() - startedMs}ms (OCR ${response.serverLatencyMs}ms)",
                        decoded = remoteText.ifBlank { "No text found" },
                        symbols = "",
                        prompt = "",
                        handPoints = null
                    )
                }
                logger.note("Remote OCR ${response.serverLatencyMs}ms: ${remoteText.take(120)}")
            }.onFailure { error ->
                overlay.update { it.copy(detail = "Remote unavailable", prompt = error.message?.take(70).orEmpty()) }
            }
        }
    }

    private fun applyRawSignal(
        raw: Boolean,
        detail: String,
        points: List<HandTracker.Point>?,
        prompt: String = ""
    ) {
        val now = SystemClock.elapsedRealtime()
        val stable = debouncer.update(raw, now)
        if (stable != signalOn) {
            signalOn = stable
            decoder.setState(stable, now)
        } else decoder.tick(now)
        overlay.update {
            it.copy(
                mode = mode.name,
                signalOn = stable,
                unitMs = decoder.unitMs.toInt(),
                fps = fps.toInt(),
                detail = detail,
                decoded = decoder.text,
                symbols = decoder.symbols,
                prompt = prompt,
                handPoints = points
            )
        }
    }

    private fun switchMode(newMode: Mode, prompt: String = "") {
        if (mode == newMode && prompt.isBlank()) return
        val now = SystemClock.elapsedRealtime()
        if (signalOn) decoder.setState(false, now)
        signalOn = false
        debouncer.reset()
        mode = newMode
        updateModeControls()
        logger.modeChanged(newMode.name.lowercase())
        if (newMode == Mode.LIGHT) lightDetector.begin(now)
        overlay.update {
            it.copy(
                mode = newMode.name,
                signalOn = false,
                prompt = if (newMode == Mode.LIGHT) lightDetector.prompt() else prompt,
                handPoints = null,
                decoded = if (newMode == Mode.REMOTE) remoteText else decoder.text,
                symbols = if (newMode == Mode.REMOTE) "" else decoder.symbols,
                detail = when (newMode) {
                    Mode.TAP -> "Show your hand to the camera"
                    Mode.LIGHT -> "Light calibration"
                    Mode.REMOTE -> "Phone OCR: ${remoteVision.endpoint}"
                }
            )
        }
    }

    private fun refreshDecoderUi() {
        overlay.update {
            it.copy(
                unitMs = decoder.unitMs.toInt(),
                decoded = if (mode == Mode.REMOTE) remoteText else decoder.text,
                symbols = if (mode == Mode.REMOTE) "" else decoder.symbols,
                fps = fps.toInt()
            )
        }
    }

    private fun updateFps(now: Long) {
        fpsFrames++
        val elapsed = now - fpsWindowStarted
        if (elapsed >= 1_000L) {
            val current = fpsFrames * 1000.0 / elapsed
            fps = if (fps == 0.0) current else fps * 0.7 + current * 0.3
            fpsFrames = 0
            fpsWindowStarted = now
            analyzeStride = if (fps < 10.0) 3 else 2
        }
    }

    private fun centerBrightness(image: ImageProxy): Double {
        val plane = image.planes[0]
        val buffer = plane.buffer
        val pixelStride = plane.pixelStride
        val rowStride = plane.rowStride
        val xStart = (image.width * 0.3).toInt()
        val xEnd = (image.width * 0.7).toInt()
        val yStart = (image.height * 0.3).toInt()
        val yEnd = (image.height * 0.7).toInt()
        var total = 0L
        var count = 0
        var y = yStart
        while (y < yEnd) {
            var x = xStart
            while (x < xEnd) {
                val offset = y * rowStride + x * pixelStride
                if (offset + 2 < buffer.limit()) {
                    val r = buffer.get(offset).toInt() and 0xff
                    val g = buffer.get(offset + 1).toInt() and 0xff
                    val b = buffer.get(offset + 2).toInt() and 0xff
                    total += (r * 299L + g * 587L + b * 114L) / 1000L
                    count++
                }
                x += 4
            }
            y += 4
        }
        return if (count == 0) 0.0 else total.toDouble() / count
    }

    private fun ImageProxy.toRotatedBitmap(): Bitmap {
        val plane = planes[0]
        val paddedWidth = plane.rowStride / plane.pixelStride
        val padded = Bitmap.createBitmap(paddedWidth, height, Bitmap.Config.ARGB_8888)
        val copy: ByteBuffer = plane.buffer.duplicate().apply { rewind() }
        padded.copyPixelsFromBuffer(copy)
        val cropped = if (paddedWidth == width) padded else Bitmap.createBitmap(padded, 0, 0, width, height).also { padded.recycle() }
        val rotation = imageInfo.rotationDegrees
        if (rotation == 0) return cropped
        return Bitmap.createBitmap(cropped, 0, 0, cropped.width, cropped.height, Matrix().apply { postRotate(rotation.toFloat()) }, true)
            .also { if (it !== cropped) cropped.recycle() }
    }

    private fun shareLatestLog() {
        val file = logger.save(decoder.unitMs)
        val uri = FileProvider.getUriForFile(this, "$packageName.files", file)
        startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }, "Share Morse log"))
    }

    private fun showEndpointDialog() {
        val field = EditText(this).apply {
            setText(remoteVision.endpoint)
            hint = "http://phone-ip:8765"
            setSingleLine(true)
        }
        AlertDialog.Builder(this)
            .setTitle("Phone companion endpoint")
            .setView(field)
            .setPositiveButton("SAVE") { _, _ ->
                val value = field.text.toString().trim().trimEnd('/')
                if (value.startsWith("http://") || value.startsWith("https://")) {
                    remoteVision.endpoint = value
                    getSharedPreferences("remote", MODE_PRIVATE).edit().putString("endpoint", value).apply()
                    toast("Remote endpoint saved")
                } else toast("Endpoint must start with http:// or https://")
            }
            .setNegativeButton("CANCEL", null)
            .show()
    }

    private fun showCameraError(message: String) {
        overlay.update { it.copy(cameraError = message) }
        retryButton.visibility = View.VISIBLE
    }

    private fun toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    override fun onDestroy() {
        mainHandler.removeCallbacks(ticker)
        try { logger.save(decoder.unitMs) } catch (_: Throwable) { }
        handTracker?.close()
        cameraExecutor.shutdown()
        super.onDestroy()
    }

    companion object { private const val TAG = "SilentMorse" }
}
