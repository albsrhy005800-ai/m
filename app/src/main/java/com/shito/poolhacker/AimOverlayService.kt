package com.shito.poolhacker

import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.os.IBinder
import android.view.Gravity
import android.view.View
import android.view.WindowManager

class AimOverlayService : Service() {
    private lateinit var windowManager: WindowManager
    private var aimView: View? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        aimView = AimView(this)
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
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
            // Prototype guides only. These are not detected or calculated from game objects yet.
            val startX = width * 0.5f
            val startY = height * 0.72f
            paint.color = Color.GREEN
            canvas.drawLine(startX, startY, startX, height * 0.12f, paint)
            paint.color = Color.YELLOW
            canvas.drawLine(startX, startY, width * 0.18f, height * 0.18f, paint)
            paint.color = Color.RED
            canvas.drawLine(startX, startY, width * 0.82f, height * 0.18f, paint)
            paint.color = Color.WHITE
            paint.strokeWidth = 2f
            canvas.drawCircle(startX, startY, 18f, paint)
        }
    }
}
