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
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val found = detection
            val table = found.table ?: return
            val cue = found.cue ?: return

            canvas.save()
            canvas.clipRect(table)

            val ballRadius = (table.width() / 68f).coerceIn(8f, 19f)
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 3.5f
            paint.color = Color.WHITE
            canvas.drawCircle(cue.x, cue.y, ballRadius, paint)

            val solution = PoolPhysics.solve(cue, found.balls, table, ballRadius)
            if (solution == null) {
                // Keep the cue marker visible so detection can be distinguished
                // from a missing cue-ball result while the object balls are being found.
                paint.color = Color.rgb(255, 205, 70)
                paint.strokeWidth = 2.5f
                canvas.drawCircle(cue.x, cue.y, ballRadius + 5f, paint)
                canvas.restore()
                return
            }

            // Cue-ball line ends at the calculated ghost-ball contact position.
            paint.color = Color.WHITE
            paint.strokeWidth = 4.2f
            canvas.drawLine(
                solution.cuePath[0].x, solution.cuePath[0].y,
                solution.ghostBall.x, solution.ghostBall.y, paint
            )

            // Predicted object-ball path: direct shot or one-cushion bank.
            paint.color = Color.rgb(65, 255, 155)
            paint.strokeWidth = 4f
            for (segment in solution.objectPath.zipWithNext()) {
                canvas.drawLine(segment.first.x, segment.first.y, segment.second.x, segment.second.y, paint)
            }

            // Ghost-ball contact marker and target-ball ring.
            paint.color = Color.rgb(255, 215, 70)
            paint.strokeWidth = 2.5f
            canvas.drawCircle(solution.ghostBall.x, solution.ghostBall.y, ballRadius * 0.82f, paint)
            paint.color = Color.rgb(65, 255, 155)
            canvas.drawCircle(solution.objectPath.first().x, solution.objectPath.first().y, ballRadius, paint)

            // Mark the cushion contact on bank shots and the selected pocket.
            if (solution.bankShot && solution.objectPath.size >= 3) {
                val bank = solution.objectPath[1]
                paint.color = Color.rgb(255, 165, 55)
                paint.strokeWidth = 3f
                canvas.drawCircle(bank.x, bank.y, ballRadius * 0.55f, paint)
            }
            paint.style = Paint.Style.FILL
            paint.color = Color.rgb(65, 255, 155)
            canvas.drawCircle(solution.pocket.x, solution.pocket.y, 6f, paint)

            canvas.restore()
        }
    }
}
