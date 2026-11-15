package com.shito.poolhacker

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.*
import android.util.DisplayMetrics
import android.util.Log
import android.view.WindowManager

class ScreenCaptureService : Service() {
    companion object {
        const val EXTRA_RESULT_CODE = "result_code"
        const val EXTRA_RESULT_DATA = "result_data"
        private const val CHANNEL = "shito_capture"
        private const val TAG = "ShitoCapture"
    }

    private var projection: MediaProjection? = null
    private var reader: ImageReader? = null
    private var display: VirtualDisplay? = null
    private var thread: HandlerThread? = null
    private var handler: Handler? = null
    private var lastFrameAt = 0L

    override fun onCreate() {
        super.onCreate()
        if (Build.VERSION.SDK_INT >= 26) {
            getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(CHANNEL, "تحليل شاشة اللعبة", NotificationManager.IMPORTANCE_LOW)
            )
        }
        val notification = if (Build.VERSION.SDK_INT >= 26) {
            Notification.Builder(this, CHANNEL)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }.setContentTitle("Shito Pool")
            .setContentText("التقاط إطارات اللعبة وتحليل الطاولة")
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .build()

        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(7, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
        } else {
            startForeground(7, notification)
        }

        thread = HandlerThread("shito-frame-reader").also { it.start() }
        handler = Handler(thread!!.looper)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (projection != null) return START_NOT_STICKY

        val resultCode = intent?.getIntExtra(EXTRA_RESULT_CODE, 0) ?: 0
        @Suppress("DEPRECATION")
        val resultData = intent?.getParcelableExtra<Intent>(EXTRA_RESULT_DATA)
        if (resultData == null || resultCode == 0) {
            Log.e(TAG, "Missing screen-capture permission result")
            stopSelf()
            return START_NOT_STICKY
        }

        try {
            val manager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            projection = manager.getMediaProjection(resultCode, resultData)

            val metrics = DisplayMetrics()
            @Suppress("DEPRECATION")
            (getSystemService(WINDOW_SERVICE) as WindowManager).defaultDisplay.getRealMetrics(metrics)

            // ImageReader remains the compatible Android 7.1+ frame source.
            // Use three buffers and honor row padding/cropRect when making each bitmap.
            reader = ImageReader.newInstance(
                metrics.widthPixels,
                metrics.heightPixels,
                PixelFormat.RGBA_8888,
                3
            )
            reader!!.setOnImageAvailableListener({ source ->
                val image = try {
                    source.acquireLatestImage()
                } catch (e: Exception) {
                    Log.e(TAG, "Unable to acquire screen frame", e)
                    null
                } ?: return@setOnImageAvailableListener

                try {
                    val now = System.currentTimeMillis()
                    if (now - lastFrameAt < 250L) return@setOnImageAvailableListener
                    lastFrameAt = now

                    val plane = image.planes[0]
                    val paddedWidth = (plane.rowStride / plane.pixelStride)
                        .coerceAtLeast(image.width)
                    val padded = Bitmap.createBitmap(
                        paddedWidth,
                        image.height,
                        Bitmap.Config.ARGB_8888
                    )
                    val pixelBuffer = plane.buffer.duplicate()
                    pixelBuffer.rewind()
                    padded.copyPixelsFromBuffer(pixelBuffer)

                    val crop = image.cropRect
                    val frame = Bitmap.createBitmap(
                        padded,
                        crop.left,
                        crop.top,
                        crop.width(),
                        crop.height()
                    )
                    if (frame !== padded) padded.recycle()

                    try {
                        val result = ScreenAnalyzer.analyze(frame)
                        AimOverlayService.updateDetection(result)
                    } finally {
                        frame.recycle()
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Frame conversion/analysis failed", e)
                } finally {
                    image.close()
                }
            }, handler)

            projection!!.registerCallback(object : MediaProjection.Callback() {
                override fun onStop() {
                    Log.w(TAG, "Screen capture stopped by system")
                    stopSelf()
                }
            }, handler)

            display = projection!!.createVirtualDisplay(
                "ShitoPoolCapture",
                metrics.widthPixels,
                metrics.heightPixels,
                metrics.densityDpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                reader!!.surface,
                null,
                handler
            )
            Log.i(TAG, "Capture started at ${metrics.widthPixels}x${metrics.heightPixels}")
        } catch (e: Exception) {
            Log.e(TAG, "Unable to start screen capture", e)
            stopSelf()
        }

        return START_NOT_STICKY
    }

    override fun onDestroy() {
        display?.release()
        display = null
        reader?.setOnImageAvailableListener(null, null)
        reader?.close()
        reader = null
        projection?.stop()
        projection = null
        thread?.quitSafely()
        thread = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?) = null
}
