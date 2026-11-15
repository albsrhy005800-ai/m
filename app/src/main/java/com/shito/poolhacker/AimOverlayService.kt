package com.shito.poolhacker

import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.PointF
import android.graphics.RectF
import android.os.IBinder
import android.view.Gravity
import android.view.View
import android.view.WindowManager

class AimOverlayService : Service() {
    companion object {
        @Volatile private var detection = DetectionResult()
        private var currentView: View? = null
        fun updateDetection(value: DetectionResult) {
            detection = value
            currentView?.postInvalidate()
        }
    }
    private lateinit var windowManager: WindowManager
    private var aimView: View? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        aimView = AimView(this)
        currentView = aimView
        // TYPE_APPLICATION_OVERLAY exists only on Android 8.0+.
        // Android 7.1 and older require a legacy overlay window type.
        val overlayType = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            overlayType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP or Gravity.START }
        windowManager.addView(aimView, params)
    }

    override fun onDestroy() {
        aimView?.let { windowManager.removeView(it) }
        aimView = null
        currentView = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private class AimView(context: Context) : View(context) {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 4f
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val found = detection
            val cue = found.cue ?: return
            val table = found.table ?: return
            val target = found.balls.minByOrNull {
                kotlin.math.hypot(it.x - cue.x, it.y - cue.y)
            }

            // Draw the cue-ball marker whenever it is detected. A missed object ball
            // must not suppress all feedback.
            canvas.save()
            canvas.clipRect(table)
            paint.style = Paint.Style.STROKE
            paint.color = Color.WHITE
            paint.strokeWidth = 3f
            canvas.drawCircle(cue.x, cue.y, 11f, paint)
            if (target != null) {
                paint.color = Color.rgb(80, 255, 150)
                paint.strokeWidth = 3f
                canvas.drawLine(cue.x, cue.y, target.x, target.y, paint)
            }
            canvas.restore()
        }
    }
}
