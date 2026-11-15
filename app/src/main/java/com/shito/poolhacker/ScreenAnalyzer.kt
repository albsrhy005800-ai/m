package com.shito.poolhacker

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.PointF
import android.graphics.RectF
import kotlin.math.abs
import kotlin.math.hypot

data class DetectionResult(
    val table: RectF? = null,
    val cue: PointF? = null,
    val balls: List<PointF> = emptyList()
)

object ScreenAnalyzer {
    fun analyze(bitmap: Bitmap): DetectionResult {
        val w = bitmap.width
        val h = bitmap.height
        val stride = 4
        var left = w
        var top = h
        var right = 0
        var bottom = 0
        var feltCount = 0
        val hsv = FloatArray(3)

        // Estimate the playing surface from felt-colored pixels, not a fixed table template.
        for (y in 0 until h step stride) {
            for (x in 0 until w step stride) {
                Color.colorToHSV(bitmap.getPixel(x, y), hsv)
                if (isFelt(hsv)) {
                    left = minOf(left, x); top = minOf(top, y)
                    right = maxOf(right, x); bottom = maxOf(bottom, y)
                    feltCount++
                }
            }
        }
        val tableWidth = right - left
        val tableHeight = bottom - top
        val tableRatio = if (tableHeight > 0) tableWidth.toFloat() / tableHeight else 0f

        // A pool table is a wide landscape surface. This rejects portrait launchers
        // and wallpaper backgrounds that were previously mistaken for blue felt.
        if (feltCount < 450 || tableWidth < w / 4 || tableHeight < h / 5 ||
            tableRatio !in 1.20f..2.20f) {
            return DetectionResult()
        }

        val table = RectF(left.toFloat(), top.toFloat(), right.toFloat(), bottom.toFloat())
        val cue = findWhiteBall(bitmap, left, top, right, bottom)
        val balls = findColoredBallCenters(bitmap, left, top, right, bottom)
        return DetectionResult(table, cue, balls)
    }

    private fun isFelt(hsv: FloatArray): Boolean =
        hsv[1] > 0.30f && hsv[2] > 0.15f &&
            (hsv[0] in 65f..175f || hsv[0] in 175f..255f || hsv[0] in 12f..48f)

    private fun findWhiteBall(b: Bitmap, l: Int, t: Int, r: Int, bot: Int): PointF? {
        var best: PointF? = null
        var bestScore = 0
        for (y in (t + 14).coerceAtLeast(14) until (bot - 14).coerceAtMost(b.height - 14) step 6) {
            for (x in (l + 14).coerceAtLeast(14) until (r - 14).coerceAtMost(b.width - 14) step 6) {
                var score = 0
                for (dy in -10..10 step 5) for (dx in -10..10 step 5) {
                    if (dx * dx + dy * dy > 100) continue
                    val c = b.getPixel(x + dx, y + dy)
                    val red = Color.red(c); val green = Color.green(c); val blue = Color.blue(c)
                    if (red > 205 && green > 205 && blue > 195 &&
                        maxOf(red, green, blue) - minOf(red, green, blue) < 45) score++
                }
                if (score > bestScore) { bestScore = score; best = PointF(x.toFloat(), y.toFloat()) }
            }
        }
        return if (bestScore >= 5) best else null
    }

    private fun findColoredBallCenters(b: Bitmap, l: Int, t: Int, r: Int, bot: Int): List<PointF> {
        val candidates = ArrayList<PointF>()
        val hsv = FloatArray(3)
        for (y in (t + 16).coerceAtLeast(16) until (bot - 16).coerceAtMost(b.height - 16) step 10) {
            for (x in (l + 16).coerceAtLeast(16) until (r - 16).coerceAtMost(b.width - 16) step 10) {
                Color.colorToHSV(b.getPixel(x, y), hsv)
                if (hsv[1] < 0.48f || hsv[2] < 0.30f) continue
                var sameColor = 0
                val centerHue = hsv[0]
                for (dy in -10..10 step 5) for (dx in -10..10 step 5) {
                    Color.colorToHSV(b.getPixel(x + dx, y + dy), hsv)
                    val hueDistance = minOf(abs(hsv[0] - centerHue), 360f - abs(hsv[0] - centerHue))
                    if (hsv[1] > 0.30f && hueDistance < 24f) sameColor++
                }
                if (sameColor >= 7 && candidates.none { hypot(it.x - x.toFloat(), it.y - y.toFloat()) < 25f }) {
                    candidates.add(PointF(x.toFloat(), y.toFloat()))
                }
            }
        }
        return candidates.take(16)
    }
}