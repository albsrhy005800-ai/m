package com.shito.poolhacker

import android.app.Service
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
    private lateinit var wm: WindowManager
    private var overlay: View? = null
    override fun onCreate() {
        super.onCreate()
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        overlay = AimView()
        val params = WindowManager.LayoutParams(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN, PixelFormat.TRANSLUCENT).apply { gravity = Gravity.TOP or Gravity.START }
        wm.addView(overlay, params)
    }
    override fun onDestroy() { overlay?.let { wm.removeView(it) }; overlay = null; super.onDestroy() }
    override fun onBind(intent: Intent?): IBinder? = null

    private class AimView : ViewHolder() {
        private val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeWidth = 4f; style = Paint.Style.STROKE }
        override fun onDraw(c: Canvas) {
            super.onDraw(c)
            val cx = width * .5f; val cy = height * .72f
            p.color = Color.GREEN; c.drawLine(cx, cy, cx, height * .12f, p)
            p.color = Color.YELLOW; c.drawLine(cx, cy, width * .18f, height * .18f, p)
            p.color = Color.RED; c.drawLine(cx, cy, width * .82f, height * .18f, p)
            p.color = Color.WHITE; p.strokeWidth = 2f; c.drawCircle(cx, cy, 18f, p)
        }
    }
    private abstract class ViewHolder : View(this@AimOverlayService) { }
}
