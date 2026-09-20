@file:androidx.annotation.OptIn(markerClass = [ExperimentalGetImage::class])

package com.sandip.plugins.scanner

import android.Manifest
import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.nativephp.mobile.bridge.BridgeFunction
import com.nativephp.mobile.bridge.BridgeResponse
import com.nativephp.mobile.lifecycle.NativePHPLifecycle
import com.nativephp.mobile.utils.NativeActionCoordinator
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import org.json.JSONObject

object ScannerFunctions {

    private const val TAG = "ScannerFunctions"
    const val CAMERA_PERMISSION_REQUEST_CODE = 4272
    private const val CODE_SCANNED_EVENT = "Sandip\\Scanner\\Native\\Events\\Scanner\\CodeScanned"
    private const val CANCELLED_EVENT = "Sandip\\Scanner\\Native\\Events\\Scanner\\Cancelled"

    private const val REPEAT_DEBOUNCE_MS = 2000L
    private const val SUCCESS_PULSE_MS = 1000L
    private const val ACCENT_GREEN = 0xFF34D399.toInt()

    @Volatile private var activeOverlay: ScannerOverlay? = null

    private data class PendingScan(val id: String?, val activity: FragmentActivity)

    @Volatile private var pendingScan: PendingScan? = null

    init {
        // Runtime permission results only reach plugins via this lifecycle bus - the
        // OS-level Activity.onRequestPermissionsResult callback is not forwarded per-plugin.
        NativePHPLifecycle.on(NativePHPLifecycle.Events.ON_PERMISSION_RESULT) { data ->
            if (data["permission"] as? String == Manifest.permission.CAMERA) {
                handleCameraPermissionResult(data["granted"] as? Boolean ?: false)
            }
        }
    }

    private val FORMAT_MAP: Map<String, Int> =
            mapOf(
                    "qr" to Barcode.FORMAT_QR_CODE,
                    "ean13" to Barcode.FORMAT_EAN_13,
                    "ean8" to Barcode.FORMAT_EAN_8,
                    "code128" to Barcode.FORMAT_CODE_128,
                    "code39" to Barcode.FORMAT_CODE_39,
                    "upca" to Barcode.FORMAT_UPC_A,
                    "upce" to Barcode.FORMAT_UPC_E,
            )

    private val REVERSE_FORMAT_MAP: Map<Int, String> =
            mapOf(
                    Barcode.FORMAT_QR_CODE to "qr",
                    Barcode.FORMAT_EAN_13 to "ean13",
                    Barcode.FORMAT_EAN_8 to "ean8",
                    Barcode.FORMAT_CODE_128 to "code128",
                    Barcode.FORMAT_CODE_39 to "code39",
                    Barcode.FORMAT_UPC_A to "upca",
                    Barcode.FORMAT_UPC_E to "upce",
                    Barcode.FORMAT_CODE_93 to "code93",
                    Barcode.FORMAT_CODABAR to "codabar",
                    Barcode.FORMAT_ITF to "itf",
                    Barcode.FORMAT_DATA_MATRIX to "data_matrix",
                    Barcode.FORMAT_PDF417 to "pdf417",
                    Barcode.FORMAT_AZTEC to "aztec",
            )

    private fun barcodeFormatOptions(names: List<String>): BarcodeScannerOptions {
        if (names.contains("all")) {
            return BarcodeScannerOptions.Builder()
                    .setBarcodeFormats(Barcode.FORMAT_ALL_FORMATS)
                    .build()
        }

        val formats = names.mapNotNull { FORMAT_MAP[it] }.distinct()
        val first = formats.firstOrNull() ?: Barcode.FORMAT_QR_CODE
        val rest = formats.drop(1).toIntArray()

        return BarcodeScannerOptions.Builder().setBarcodeFormats(first, *rest).build()
    }

    private object PermissionPrefs {
        private const val PREFS_NAME = "scanner_permission_prefs"
        private const val KEY_ASKED = "camera_permission_asked"

        fun hasAskedBefore(context: Context): Boolean =
                context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                        .getBoolean(KEY_ASKED, false)

        fun markAsked(context: Context) {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    .edit()
                    .putBoolean(KEY_ASKED, true)
                    .apply()
        }
    }

    class GalleryPickerHost : Fragment() {
        private var callback: ((Uri?) -> Unit)? = null

        private val launcher =
                registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
                    val cb = callback
                    callback = null
                    cb?.invoke(uri)
                }

        fun pickImage(onPicked: (Uri?) -> Unit) {
            callback = onPicked
            launcher.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
            )
        }

        companion object {
            private const val TAG = "ScannerGalleryPicker"

            fun install(activity: FragmentActivity): GalleryPickerHost =
                    activity.supportFragmentManager.findFragmentByTag(TAG) as? GalleryPickerHost
                            ?: GalleryPickerHost().also {
                                activity.supportFragmentManager
                                        .beginTransaction()
                                        .add(it, TAG)
                                        .commitNow()
                            }
        }
    }

    private fun startScan(
            activity: FragmentActivity,
            prompt: String,
            continuous: Boolean,
            allowGallery: Boolean,
            formats: List<String>,
            haptics: Boolean,
            zoom: Float,
            maxZoom: Float,
            zoomControl: Boolean,
            focusOnTap: Boolean,
            timeoutSeconds: Int,
            id: String?,
    ) {
        activity.runOnUiThread {
            activeOverlay?.finish(cancelled = true)
            val overlay =
                    ScannerOverlay(
                            activity,
                            prompt,
                            continuous,
                            allowGallery,
                            formats,
                            haptics,
                            zoom,
                            maxZoom,
                            zoomControl,
                            focusOnTap,
                            timeoutSeconds,
                            id
                    )
            activeOverlay = overlay
            overlay.show()
        }
    }

    private fun handleCameraPermissionResult(granted: Boolean) {
        val pending = pendingScan ?: return
        pendingScan = null

        pending.activity.runOnUiThread {
            val payload = JSONObject()
            payload.put("reason", if (granted) "permission_required" else "permission_denied")
            if (pending.id != null) payload.put("id", pending.id)
            NativeActionCoordinator.dispatchEvent(
                    pending.activity,
                    CANCELLED_EVENT,
                    payload.toString()
            )
        }
    }

    class Scan(private val activity: FragmentActivity) : BridgeFunction {
        override fun execute(parameters: Map<String, Any>): Map<String, Any> {
            val prompt = parameters["prompt"] as? String ?: ""
            val continuous = parameters["continuous"] as? Boolean ?: false
            val allowGallery = parameters["allowGallery"] as? Boolean ?: true
            val id = parameters["id"] as? String

            @Suppress("UNCHECKED_CAST")
            val requestedFormats =
                    (parameters["formats"] as? List<String>)?.filter { it.isNotBlank() }?.takeIf {
                        it.isNotEmpty()
                    }
                            ?: listOf("qr")

            val haptics = parameters["haptics"] as? Boolean ?: true
            val zoom = (parameters["zoom"] as? Number)?.toFloat() ?: 1.0f
            val maxZoom = (parameters["maxZoom"] as? Number)?.toFloat() ?: 3.0f
            val zoomControl = parameters["zoomControl"] as? Boolean ?: true
            val focusOnTap = parameters["focusOnTap"] as? Boolean ?: true
            val timeoutSeconds = (parameters["timeout"] as? Number)?.toInt() ?: 0

            val unknown = requestedFormats.filter { it != "all" && !FORMAT_MAP.containsKey(it) }
            if (unknown.isNotEmpty()) {
                return BridgeResponse.error(
                        "INVALID_FORMAT",
                        "Unknown barcode format(s): ${unknown.joinToString(", ")}. Valid formats are: ${(FORMAT_MAP.keys + "all").joinToString(", ")}."
                )
            }

            if (ContextCompat.checkSelfPermission(activity, Manifest.permission.CAMERA) !=
                            PackageManager.PERMISSION_GRANTED
            ) {
                val askedBefore = PermissionPrefs.hasAskedBefore(activity)
                val canShowRationale =
                        ActivityCompat.shouldShowRequestPermissionRationale(
                                activity,
                                Manifest.permission.CAMERA
                        )

                if (askedBefore && !canShowRationale) {
                    return BridgeResponse.error(
                            "PERMISSION_DENIED",
                            "Camera access is denied. Enable it in Settings to use the scanner."
                    )
                }

                PermissionPrefs.markAsked(activity)
                pendingScan = PendingScan(id, activity)

                ActivityCompat.requestPermissions(
                        activity,
                        arrayOf(Manifest.permission.CAMERA),
                        CAMERA_PERMISSION_REQUEST_CODE
                )

                return BridgeResponse.success(mapOf("permissionRequested" to true))
            }

            startScan(
                    activity,
                    prompt,
                    continuous,
                    allowGallery,
                    requestedFormats,
                    haptics,
                    zoom,
                    maxZoom,
                    zoomControl,
                    focusOnTap,
                    timeoutSeconds,
                    id
            )

            return BridgeResponse.success(mapOf("started" to true))
        }
    }

    class Stop(private val activity: FragmentActivity) : BridgeFunction {
        override fun execute(parameters: Map<String, Any>): Map<String, Any> {
            val id = parameters["id"] as? String
            val overlay = activeOverlay

            if (overlay == null || (id != null && overlay.id != id)) {
                return BridgeResponse.success(mapOf("stopped" to false))
            }

            activity.runOnUiThread { overlay.finish(cancelled = true, reason = "stopped_by_app") }

            return BridgeResponse.success(mapOf("stopped" to true))
        }
    }

    private class IconButtonView(
            context: Context,
            initialGlyph: (Canvas, Float, Float, Float) -> Unit,
    ) : View(context) {
        var glyph: (Canvas, Float, Float, Float) -> Unit = initialGlyph
            set(value) {
                field = value
                invalidate()
            }

        private val circlePaint =
                Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.WHITE
                    style = Paint.Style.FILL
                }

        init {
            isClickable = true
            isFocusable = true
        }

        override fun onDraw(canvas: Canvas) {
            val cx = width / 2f
            val cy = height / 2f
            val radius = minOf(width, height) / 2f
            canvas.drawCircle(cx, cy, radius, circlePaint)
            glyph(canvas, cx, cy, radius * 0.5f)
        }
    }

    private fun drawCloseIcon(canvas: Canvas, cx: Float, cy: Float, r: Float) {
        val paint =
                Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.BLACK
                    style = Paint.Style.STROKE
                    strokeWidth = r * 0.24f
                    strokeCap = Paint.Cap.ROUND
                }
        val d = r * 0.72f
        canvas.drawLine(cx - d, cy - d, cx + d, cy + d, paint)
        canvas.drawLine(cx - d, cy + d, cx + d, cy - d, paint)
    }

    private fun drawBoltIcon(canvas: Canvas, cx: Float, cy: Float, r: Float) {
        val paint =
                Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.BLACK
                    style = Paint.Style.FILL
                }
        val s = (r * 2.1f) / 24f
        val ox = cx - 12f * s
        val oy = cy - 12f * s
        val path =
                Path().apply {
                    moveTo(ox + 7f * s, oy + 2f * s)
                    lineTo(ox + 7f * s, oy + 13f * s)
                    lineTo(ox + 10f * s, oy + 13f * s)
                    lineTo(ox + 10f * s, oy + 22f * s)
                    lineTo(ox + 17f * s, oy + 10f * s)
                    lineTo(ox + 13f * s, oy + 10f * s)
                    lineTo(ox + 17f * s, oy + 2f * s)
                    close()
                }
        canvas.drawPath(path, paint)
    }

    private fun drawBoltSlashIcon(canvas: Canvas, cx: Float, cy: Float, r: Float) {
        val paint =
                Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.BLACK
                    style = Paint.Style.FILL
                }
        val s = (r * 2.1f) / 24f
        val ox = cx - 12f * s
        val oy = cy - 12f * s

        val body =
                Path().apply {
                    moveTo(ox + 3.27f * s, oy + 3f * s)
                    lineTo(ox + 2f * s, oy + 4.27f * s)
                    lineTo(ox + 7.18f * s, oy + 9.45f * s)
                    lineTo(ox + 7f * s, oy + 10f * s)
                    lineTo(ox + 10f * s, oy + 10f * s)
                    lineTo(ox + 10f * s, oy + 20f * s)
                    lineTo(ox + 13.58f * s, oy + 13.86f * s)
                    lineTo(ox + 17.73f * s, oy + 18f * s)
                    lineTo(ox + 19f * s, oy + 16.73f * s)
                    close()
                }
        canvas.drawPath(body, paint)

        val tip =
                Path().apply {
                    moveTo(ox + 17f * s, oy + 10f * s)
                    lineTo(ox + 13f * s, oy + 10f * s)
                    lineTo(ox + 17f * s, oy + 2f * s)
                    lineTo(ox + 7f * s, oy + 2f * s)
                    lineTo(ox + 7f * s, oy + 4.18f * s)
                    lineTo(ox + 14.46f * s, oy + 11.64f * s)
                    close()
                }
        canvas.drawPath(tip, paint)
    }

    private class ViewfinderOverlayView(
            context: Context,
            private val windowRect: RectF,
    ) : View(context) {
        private val borderPaint =
                Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.WHITE
                    style = Paint.Style.STROKE
                    strokeWidth = 6f
                }
        private val pulsePaint =
                Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = ACCENT_GREEN
                    style = Paint.Style.STROKE
                }

        private val cornerRadius = 24f
        private var pulseProgress = 0f
        private var pulseActive = false
        private var pulseTarget: RectF? = null
        private var pulseAnimator: ValueAnimator? = null

        fun playSuccessPulse(codeRect: RectF?) {
            pulseTarget = codeRect
            pulseAnimator?.cancel()
            pulseAnimator =
                    ValueAnimator.ofFloat(0f, 1f).apply {
                        duration = SUCCESS_PULSE_MS
                        interpolator = DecelerateInterpolator()
                        addUpdateListener {
                            pulseProgress = it.animatedValue as Float
                            invalidate()
                        }
                        addListener(
                                object : AnimatorListenerAdapter() {
                                    override fun onAnimationEnd(animation: Animator) {
                                        pulseActive = false
                                        invalidate()
                                    }
                                }
                        )
                        pulseActive = true
                        start()
                    }
        }

        override fun onDraw(canvas: Canvas) {
            if (!pulseActive) {
                canvas.drawRoundRect(windowRect, cornerRadius, cornerRadius, borderPaint)
                return
            }

            val target = pulseTarget ?: windowRect
            val radius = minOf(target.width(), target.height()) * 0.12f

            val growT = (pulseProgress / 0.25f).coerceIn(0f, 1f)
            val scale = 1.3f + (1f - 1.3f) * growT
            val hw = target.width() / 2f * scale
            val hh = target.height() / 2f * scale
            val cx = target.centerX()
            val cy = target.centerY()

            val fadeT = ((pulseProgress - 0.7f) / 0.3f).coerceIn(0f, 1f)
            pulsePaint.alpha = ((1f - fadeT) * 255).toInt()
            pulsePaint.strokeWidth = 6f

            canvas.drawRoundRect(
                    RectF(cx - hw, cy - hh, cx + hw, cy + hh),
                    radius,
                    radius,
                    pulsePaint
            )
        }
    }

    private class ZoomSliderView(context: Context) : View(context) {
        var minValue = 1f
        var maxValue = 3f
        var onValueChanged: ((Float) -> Unit)? = null

        var value = 1f
            set(newValue) {
                field = newValue.coerceIn(minValue, maxValue)
                invalidate()
            }

        private val density = context.resources.displayMetrics.density
        private val thumbRadius = density * 9f

        private val trackPaint =
                Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.parseColor("#4DFFFFFF")
                    strokeWidth = density * 2f
                    strokeCap = Paint.Cap.ROUND
                }
        private val progressPaint =
                Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.WHITE
                    strokeWidth = density * 2f
                    strokeCap = Paint.Cap.ROUND
                }
        private val thumbPaint =
                Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.WHITE
                    style = Paint.Style.FILL
                }
        private val thumbShadowPaint =
                Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.parseColor("#40000000")
                    style = Paint.Style.FILL
                }

        private fun trackLeft() = paddingLeft + thumbRadius
        private fun trackRight() = width - paddingRight - thumbRadius

        override fun onDraw(canvas: Canvas) {
            val left = trackLeft()
            val right = trackRight()
            if (right <= left) return

            val cy = height / 2f
            val ratio = ((value - minValue) / (maxValue - minValue)).coerceIn(0f, 1f)
            val thumbX = left + (right - left) * ratio

            canvas.drawLine(left, cy, right, cy, trackPaint)
            canvas.drawLine(left, cy, thumbX, cy, progressPaint)
            canvas.drawCircle(thumbX, cy + density, thumbRadius, thumbShadowPaint)
            canvas.drawCircle(thumbX, cy, thumbRadius, thumbPaint)
        }

        override fun onTouchEvent(event: MotionEvent): Boolean {
            when (event.action) {
                MotionEvent.ACTION_DOWN -> parent?.requestDisallowInterceptTouchEvent(true)
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    updateFromTouch(event.x)
                    performClick()
                    parent?.requestDisallowInterceptTouchEvent(false)
                    return true
                }
            }
            updateFromTouch(event.x)
            return true
        }

        override fun performClick(): Boolean {
            super.performClick()
            return true
        }

        private fun updateFromTouch(x: Float) {
            val left = trackLeft()
            val right = trackRight()
            if (right <= left) return
            val ratio = ((x - left) / (right - left)).coerceIn(0f, 1f)
            val newValue = minValue + (maxValue - minValue) * ratio
            value = newValue
            onValueChanged?.invoke(newValue)
        }
    }

    private class ScannerOverlay(
            private val activity: FragmentActivity,
            private val prompt: String,
            private val continuous: Boolean,
            private val allowGallery: Boolean,
            private val formatNames: List<String>,
            private val haptics: Boolean,
            private val initialZoom: Float,
            private val maxZoomConfigured: Float,
            private val zoomControl: Boolean,
            private val focusOnTap: Boolean,
            private val timeoutSeconds: Int,
            val id: String?,
    ) {
        private val root = activity.findViewById<ViewGroup>(android.R.id.content)
        private val executor: ExecutorService = Executors.newSingleThreadExecutor()
        private val finished = AtomicBoolean(false)
        private val matched = AtomicBoolean(false)
        private var overlayView: FrameLayout? = null
        private var viewfinderView: ViewfinderOverlayView? = null
        private var previewView: PreviewView? = null
        private var cameraProvider: ProcessCameraProvider? = null
        private var camera: Camera? = null
        private var torchOn = false
        private var scanner: BarcodeScanner? = null

        private val timeoutHandler = Handler(Looper.getMainLooper())
        private var timeoutRunnable: Runnable? = null

        private var zoomLowerBound = 1f
        private var zoomUpperBound = maxZoomConfigured.coerceAtLeast(1f)
        private var zoomSliderView: ZoomSliderView? = null
        private var zoomLabel: TextView? = null

        private var lastValue: String? = null
        private var lastFiredAt: Long = 0L

        private fun dp(value: Int): Int =
                (value * activity.resources.displayMetrics.density).toInt()

        fun show() {
            val previewView =
                    PreviewView(activity).apply {
                        layoutParams =
                                FrameLayout.LayoutParams(
                                        FrameLayout.LayoutParams.MATCH_PARENT,
                                        FrameLayout.LayoutParams.MATCH_PARENT
                                )
                    }

            val dm = activity.resources.displayMetrics
            val squareSide = (minOf(dm.widthPixels, dm.heightPixels) * 0.68f)
            val squareLeft = (dm.widthPixels - squareSide) / 2f
            val squareTop = dm.heightPixels * 0.26f
            val squareRect =
                    RectF(squareLeft, squareTop, squareLeft + squareSide, squareTop + squareSide)

            val viewfinder =
                    ViewfinderOverlayView(activity, squareRect).apply {
                        layoutParams =
                                FrameLayout.LayoutParams(
                                        FrameLayout.LayoutParams.MATCH_PARENT,
                                        FrameLayout.LayoutParams.MATCH_PARENT
                                )
                    }
            viewfinderView = viewfinder
            this.previewView = previewView

            val titleLabel =
                    TextView(activity).apply {
                        text = "Scan Code"
                        setTextColor(Color.WHITE)
                        textSize = 17f
                        setTypeface(typeface, android.graphics.Typeface.BOLD)
                        gravity = Gravity.CENTER
                        layoutParams =
                                FrameLayout.LayoutParams(
                                                FrameLayout.LayoutParams.WRAP_CONTENT,
                                                FrameLayout.LayoutParams.WRAP_CONTENT,
                                                Gravity.TOP or Gravity.CENTER_HORIZONTAL
                                        )
                                        .apply { topMargin = dp(56) }
                    }

            val promptBottomMargin = dp(40)
            val promptLabel =
                    if (prompt.isBlank()) null
                    else
                            TextView(activity).apply {
                                text = prompt
                                setTextColor(Color.WHITE)
                                textSize = 14f
                                gravity = Gravity.CENTER
                                layoutParams =
                                        FrameLayout.LayoutParams(
                                                        FrameLayout.LayoutParams.MATCH_PARENT,
                                                        FrameLayout.LayoutParams.WRAP_CONTENT,
                                                        Gravity.BOTTOM
                                                )
                                                .apply {
                                                    bottomMargin = promptBottomMargin
                                                    leftMargin = dp(32)
                                                    rightMargin = dp(32)
                                                }
                            }

            val closeButton =
                    IconButtonView(activity) { canvas, cx, cy, r ->
                        drawCloseIcon(canvas, cx, cy, r)
                    }
                            .apply {
                                layoutParams =
                                        FrameLayout.LayoutParams(
                                                        dp(44),
                                                        dp(44),
                                                        Gravity.TOP or Gravity.START
                                                )
                                                .apply {
                                                    topMargin = dp(44)
                                                    leftMargin = dp(20)
                                                }
                                setOnClickListener {
                                    finish(cancelled = true, reason = "user_cancelled")
                                }
                            }

            val torchButton =
                    IconButtonView(activity) { canvas, cx, cy, r ->
                        drawBoltSlashIcon(canvas, cx, cy, r)
                    }
                            .apply {
                                visibility = View.INVISIBLE
                                layoutParams =
                                        FrameLayout.LayoutParams(
                                                        dp(44),
                                                        dp(44),
                                                        Gravity.TOP or Gravity.END
                                                )
                                                .apply {
                                                    topMargin = dp(44)
                                                    rightMargin = dp(20)
                                                }
                                setOnClickListener {
                                    val cam = camera ?: return@setOnClickListener
                                    torchOn = !torchOn
                                    cam.cameraControl.enableTorch(torchOn)
                                    glyph = if (torchOn) ::drawBoltIcon else ::drawBoltSlashIcon
                                }
                            }

            val galleryButton =
                    if (!allowGallery) null
                    else
                            TextView(activity).apply {
                                text = "Choose from Gallery"
                                setTextColor(Color.WHITE)
                                textSize = 14f
                                setTypeface(typeface, android.graphics.Typeface.BOLD)
                                gravity = Gravity.CENTER
                                includeFontPadding = false
                                setPadding(dp(24), dp(12), dp(24), dp(12))
                                background =
                                        GradientDrawable().apply {
                                            shape = GradientDrawable.RECTANGLE
                                            cornerRadius = dp(24).toFloat()
                                            setColor(Color.parseColor("#33FFFFFF"))
                                            setStroke(dp(2), Color.WHITE)
                                        }
                                isClickable = true
                                layoutParams =
                                        FrameLayout.LayoutParams(
                                                        FrameLayout.LayoutParams.WRAP_CONTENT,
                                                        FrameLayout.LayoutParams.WRAP_CONTENT,
                                                        Gravity.TOP or Gravity.CENTER_HORIZONTAL
                                                )
                                                .apply {
                                                    topMargin = squareRect.bottom.toInt() + dp(28)
                                                }
                                setOnClickListener { pickFromGallery() }
                            }

            val zoomLabel =
                    if (!zoomControl) null
                    else
                            TextView(activity).apply {
                                text = formatZoomLabel(initialZoom.coerceIn(zoomLowerBound, zoomUpperBound))
                                setTextColor(Color.WHITE)
                                textSize = 13f
                                setTypeface(typeface, android.graphics.Typeface.BOLD)
                                gravity = Gravity.CENTER
                                layoutParams =
                                        FrameLayout.LayoutParams(
                                                        FrameLayout.LayoutParams.WRAP_CONTENT,
                                                        FrameLayout.LayoutParams.WRAP_CONTENT,
                                                        Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
                                                )
                                                .apply { bottomMargin = promptBottomMargin + dp(90) }
                            }
            this.zoomLabel = zoomLabel

            val zoomSlider =
                    if (!zoomControl) null
                    else
                            ZoomSliderView(activity).apply {
                                minValue = zoomLowerBound
                                maxValue = zoomUpperBound
                                value = initialZoom.coerceIn(zoomLowerBound, zoomUpperBound)
                                layoutParams =
                                        FrameLayout.LayoutParams(
                                                        dp(200),
                                                        dp(32),
                                                        Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
                                                )
                                                .apply { bottomMargin = promptBottomMargin + dp(54) }
                                onValueChanged = { newValue ->
                                    camera?.cameraControl?.setZoomRatio(newValue)
                                    this@ScannerOverlay.zoomLabel?.text = formatZoomLabel(newValue)
                                }
                            }
            this.zoomSliderView = zoomSlider

            val overlay =
                    FrameLayout(activity).apply {
                        setBackgroundColor(Color.BLACK)
                        addView(previewView)
                        addView(viewfinder)
                        addView(titleLabel)
                        promptLabel?.let { addView(it) }
                        galleryButton?.let { addView(it) }
                        zoomLabel?.let { addView(it) }
                        zoomSlider?.let { addView(it) }
                        addView(closeButton)
                        addView(torchButton)
                        layoutParams =
                                FrameLayout.LayoutParams(
                                        FrameLayout.LayoutParams.MATCH_PARENT,
                                        FrameLayout.LayoutParams.MATCH_PARENT
                                )
                    }

            overlayView = overlay
            root.addView(overlay)

            if (zoomControl && (zoomSlider != null || zoomLabel != null)) {
                overlay.viewTreeObserver.addOnGlobalLayoutListener(
                        object : android.view.ViewTreeObserver.OnGlobalLayoutListener {
                            override fun onGlobalLayout() {
                                overlay.viewTreeObserver.removeOnGlobalLayoutListener(this)
                                val slider = zoomSlider ?: return
                                val bottomLimit =
                                        if (promptLabel != null) {
                                            promptLabel.top - dp(14)
                                        } else {
                                            overlay.height - dp(28)
                                        }
                                slider.y = (bottomLimit - slider.height).toFloat()
                                zoomLabel?.let { it.y = slider.y - dp(4) - it.height }
                            }
                        }
                )
            }

            if (focusOnTap) {
                previewView.setOnTouchListener { view, event ->
                    if (event.action == MotionEvent.ACTION_UP) {
                        val cam = camera
                        if (cam != null) {
                            val point =
                                    previewView.meteringPointFactory.createPoint(
                                            event.x,
                                            event.y
                                    )
                            val action =
                                    FocusMeteringAction.Builder(
                                                    point,
                                                    FocusMeteringAction.FLAG_AF or
                                                            FocusMeteringAction.FLAG_AE
                                            )
                                            .setAutoCancelDuration(3, TimeUnit.SECONDS)
                                            .build()
                            cam.cameraControl.startFocusAndMetering(action)
                            showFocusIndicator(event.x, event.y)
                        }
                        view.performClick()
                    }
                    true
                }
            }

            if (timeoutSeconds > 0) {
                val runnable = Runnable { finish(cancelled = true, reason = "timeout") }
                timeoutRunnable = runnable
                timeoutHandler.postDelayed(runnable, timeoutSeconds * 1000L)
            }

            val scanner = BarcodeScanning.getClient(barcodeFormatOptions(formatNames))
            this.scanner = scanner

            val cameraProviderFuture = ProcessCameraProvider.getInstance(activity)
            cameraProviderFuture.addListener(
                    {
                        val provider = cameraProviderFuture.get()
                        cameraProvider = provider

                        val preview =
                                Preview.Builder().build().also {
                                    it.setSurfaceProvider(previewView.surfaceProvider)
                                }

                        val analysis =
                                ImageAnalysis.Builder()
                                        .setBackpressureStrategy(
                                                ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST
                                        )
                                        .build()
                                        .also {
                                            it.setAnalyzer(executor) { imageProxy ->
                                                processFrame(imageProxy, scanner)
                                            }
                                        }

                        try {
                            provider.unbindAll()
                            val boundCamera =
                                    provider.bindToLifecycle(
                                            activity,
                                            CameraSelector.DEFAULT_BACK_CAMERA,
                                            preview,
                                            analysis
                                    )
                            camera = boundCamera
                            if (boundCamera.cameraInfo.hasFlashUnit()) {
                                torchButton.visibility = View.VISIBLE
                            }
                            applyZoom(boundCamera)
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed to bind camera", e)
                            finish(cancelled = true, reason = "camera_error")
                        }
                    },
                    ContextCompat.getMainExecutor(activity)
            )
        }

        private fun processFrame(imageProxy: ImageProxy, scanner: BarcodeScanner) {
            val mediaImage = imageProxy.image
            if (mediaImage == null) {
                imageProxy.close()
                return
            }

            val rotationDegrees = imageProxy.imageInfo.rotationDegrees
            val image = InputImage.fromMediaImage(mediaImage, rotationDegrees)
            val imageWidth = imageProxy.width
            val imageHeight = imageProxy.height

            scanner.process(image)
                    .addOnSuccessListener { barcodes ->
                        val barcode = barcodes.firstOrNull { !it.rawValue.isNullOrEmpty() }
                        val value = barcode?.rawValue

                        if (value != null) {
                            val mappedBox =
                                    mapBoundingBoxToView(
                                            barcode.boundingBox,
                                            imageWidth,
                                            imageHeight,
                                            rotationDegrees
                                    )
                            handleMatch(
                                    value,
                                    REVERSE_FORMAT_MAP[barcode.format] ?: "unknown",
                                    mappedBox
                            )
                        }
                    }
                    .addOnFailureListener { Log.e(TAG, "Barcode scan failed", it) }
                    .addOnCompleteListener { imageProxy.close() }
        }

        private fun mapBoundingBoxToView(
                box: Rect?,
                imageWidth: Int,
                imageHeight: Int,
                rotationDegrees: Int,
        ): RectF? {
            box ?: return null
            val pv = previewView ?: return null
            if (pv.width == 0 || pv.height == 0) return null

            val rotatedWidth =
                    if (rotationDegrees == 90 || rotationDegrees == 270) imageHeight else imageWidth
            val rotatedHeight =
                    if (rotationDegrees == 90 || rotationDegrees == 270) imageWidth else imageHeight
            if (rotatedWidth <= 0 || rotatedHeight <= 0) return null

            val scale =
                    maxOf(pv.width.toFloat() / rotatedWidth, pv.height.toFloat() / rotatedHeight)
            val offsetX = (pv.width - rotatedWidth * scale) / 2f
            val offsetY = (pv.height - rotatedHeight * scale) / 2f

            return RectF(
                    box.left * scale + offsetX,
                    box.top * scale + offsetY,
                    box.right * scale + offsetX,
                    box.bottom * scale + offsetY
            )
        }

        private fun formatZoomLabel(ratio: Float): String = String.format("%.1fx", ratio)

        private fun applyZoom(boundCamera: Camera) {
            val zoomState = boundCamera.cameraInfo.zoomState.value
            val deviceMin = zoomState?.minZoomRatio ?: 1f
            val deviceMax = zoomState?.maxZoomRatio ?: maxZoomConfigured
            zoomLowerBound = deviceMin.coerceAtLeast(1f)
            zoomUpperBound = maxZoomConfigured.coerceIn(zoomLowerBound, deviceMax)

            val clampedInitial = initialZoom.coerceIn(zoomLowerBound, zoomUpperBound)
            boundCamera.cameraControl.setZoomRatio(clampedInitial)

            zoomSliderView?.minValue = zoomLowerBound
            zoomSliderView?.maxValue = zoomUpperBound
            zoomSliderView?.value = clampedInitial
            zoomLabel?.text = formatZoomLabel(clampedInitial)
        }

        private fun showFocusIndicator(x: Float, y: Float) {
            val overlay = overlayView ?: return
            val size = dp(64)
            val ring =
                    View(activity).apply {
                        background =
                                GradientDrawable().apply {
                                    shape = GradientDrawable.OVAL
                                    setStroke(dp(2), Color.WHITE)
                                    setColor(Color.TRANSPARENT)
                                }
                        layoutParams =
                                FrameLayout.LayoutParams(size, size).apply {
                                    leftMargin = (x - size / 2f).toInt()
                                    topMargin = (y - size / 2f).toInt()
                                }
                        alpha = 0f
                        scaleX = 1.3f
                        scaleY = 1.3f
                    }
            overlay.addView(ring)
            ring.animate()
                    .alpha(1f)
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(180)
                    .withEndAction {
                        ring.animate()
                                .alpha(0f)
                                .setStartDelay(400)
                                .setDuration(300)
                                .withEndAction { overlay.removeView(ring) }
                                .start()
                    }
                    .start()
        }

        private fun vibrate() {
            if (!haptics) return
            try {
                val vibrator =
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            val manager =
                                    activity.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as?
                                            VibratorManager
                            manager?.defaultVibrator
                        } else {
                            @Suppress("DEPRECATION")
                            activity.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                        }

                if (vibrator == null || !vibrator.hasVibrator()) return

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(
                            VibrationEffect.createOneShot(40, VibrationEffect.DEFAULT_AMPLITUDE)
                    )
                } else {
                    @Suppress("DEPRECATION") vibrator.vibrate(40)
                }
            } catch (e: SecurityException) {
                Log.w(TAG, "Vibrate skipped: android.permission.VIBRATE not granted", e)
            }
        }

        private fun pickFromGallery() {
            if (!allowGallery) return
            GalleryPickerHost.install(activity).pickImage { uri ->
                if (uri == null) return@pickImage
                decodeGalleryImage(uri)
            }
        }

        private fun decodeGalleryImage(uri: Uri) {
            if (finished.get()) return
            val scanner = this.scanner ?: return

            val image =
                    try {
                        InputImage.fromFilePath(activity, uri)
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to load picked image", e)
                        showGalleryToast("Couldn't read that image.")
                        return
                    }

            scanner.process(image)
                    .addOnSuccessListener { barcodes ->
                        val barcode = barcodes.firstOrNull { !it.rawValue.isNullOrEmpty() }
                        val value = barcode?.rawValue

                        if (value != null) {
                            vibrate()
                            finish(
                                    cancelled = false,
                                    data = value,
                                    format = REVERSE_FORMAT_MAP[barcode.format] ?: "unknown"
                            )
                        } else {
                            showGalleryToast("No code found in that image.")
                        }
                    }
                    .addOnFailureListener {
                        Log.e(TAG, "Gallery barcode scan failed", it)
                        showGalleryToast("No code found in that image.")
                    }
        }

        private fun showGalleryToast(message: String) {
            activity.runOnUiThread { Toast.makeText(activity, message, Toast.LENGTH_SHORT).show() }
        }

        private fun handleMatch(value: String, format: String, boundingBox: RectF?) {
            if (!continuous) {
                if (!matched.compareAndSet(false, true)) return

                activity.runOnUiThread {
                    vibrate()
                    viewfinderView?.playSuccessPulse(boundingBox)
                    overlayView?.postDelayed(
                            { finish(cancelled = false, data = value, format = format) },
                            SUCCESS_PULSE_MS
                    )
                }
                return
            }

            val now = System.currentTimeMillis()
            if (value == lastValue && now - lastFiredAt < REPEAT_DEBOUNCE_MS) {
                return
            }
            lastValue = value
            lastFiredAt = now

            activity.runOnUiThread {
                vibrate()
                viewfinderView?.playSuccessPulse(boundingBox)

                val payload = JSONObject()
                payload.put("data", value)
                payload.put("format", format)
                if (id != null) payload.put("id", id)
                NativeActionCoordinator.dispatchEvent(
                        activity,
                        CODE_SCANNED_EVENT,
                        payload.toString()
                )
            }
        }

        fun finish(
                cancelled: Boolean,
                data: String? = null,
                format: String? = null,
                reason: String? = null
        ) {
            if (!finished.compareAndSet(false, true)) {
                return
            }

            if (activeOverlay === this) {
                activeOverlay = null
            }

            timeoutRunnable?.let { timeoutHandler.removeCallbacks(it) }

            activity.runOnUiThread {
                cameraProvider?.unbindAll()
                overlayView?.let { root.removeView(it) }
                executor.shutdown()

                val payload = JSONObject()
                if (cancelled) {
                    if (reason == null) return@runOnUiThread
                    payload.put("reason", reason)
                    if (id != null) payload.put("id", id)
                    NativeActionCoordinator.dispatchEvent(
                            activity,
                            CANCELLED_EVENT,
                            payload.toString()
                    )
                } else {
                    payload.put("data", data)
                    payload.put("format", format ?: "unknown")
                    if (id != null) payload.put("id", id)
                    NativeActionCoordinator.dispatchEvent(
                            activity,
                            CODE_SCANNED_EVENT,
                            payload.toString()
                    )
                }
            }
        }
    }
}