package com.shito.poolhacker

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.PointF
import android.graphics.RectF
import java.util.ArrayDeque
import kotlin.math.abs

data class DetectionResult(
    val table: RectF? = null,
    val cue: PointF? = null,
    val balls: List<PointF> = emptyList()
)

/**
 * Adaptive image analysis:
 * 1) find the largest connected felt region (not a fixed screen rectangle),
 * 2) find compact circular, non-felt color components inside that region,
 * 3) identify the cue ball by its high neutral-white pixel ratio.
 */
object ScreenAnalyzer {
    private data class Component(
        val x: Float,
        val y: Float,
        val width: Int,
        val height: Int,
        val count: Int,
        val whiteRatio: Float
    )

    fun analyze(bitmap: Bitmap): DetectionResult {
        val w = bitmap.width
        val h = bitmap.height
        if (w < 200 || h < 150) return DetectionResult()

        val step = 4
        val gw = (w + step - 1) / step
        val gh = (h + step - 1) / step
        val felt = BooleanArray(gw * gh)
        val hsv = FloatArray(3)

        for (gy in 0 until gh) {
            val y = (gy * step + step / 2).coerceAtMost(h - 1)
            for (gx in 0 until gw) {
                val x = (gx * step + step / 2).coerceAtMost(w - 1)
                Color.colorToHSV(bitmap.getPixel(x, y), hsv)
                felt[gy * gw + gx] = isTableFelt(hsv)
            }
        }

        val tableComponent = largestComponent(felt, gw, gh) ?: return DetectionResult()
        val tableWidth = (tableComponent[2] - tableComponent[0] + 1) * step
        val tableHeight = (tableComponent[3] - tableComponent[1] + 1) * step
        val ratio = tableWidth.toFloat() / tableHeight.coerceAtLeast(1)
        val feltCells = tableComponent[4]
        if (feltCells < 500 || tableWidth < w * 0.28f ||
            tableHeight < h * 0.22f || ratio !in 1.25f..2.55f) {
            return DetectionResult()
        }

        val left = (tableComponent[0] * step).toFloat()
        val top = (tableComponent[1] * step).toFloat()
        val right = minOf(w, (tableComponent[2] + 1) * step).toFloat()
        val bottom = minOf(h, (tableComponent[3] + 1) * step).toFloat()
        val table = RectF(left, top, right, bottom)

        val balls = findBallComponents(bitmap, table, step)
        val cueComponent = balls.filter { it.whiteRatio >= 0.66f }
            .maxByOrNull { it.count * it.whiteRatio }
        val cue = cueComponent?.let { PointF(it.x, it.y) }
        val objects = balls.filterNot { c ->
            cueComponent != null && abs(c.x - cueComponent.x) < step * 2f &&
                abs(c.y - cueComponent.y) < step * 2f
        }.map { PointF(it.x, it.y) }

        return DetectionResult(table, cue, objects)
    }

    private fun isTableFelt(hsv: FloatArray): Boolean {
        // The blue/cyan cloth in 8 Ball Pool; avoids blue UI elements by
        // selecting only the largest connected region in the whole frame.
        return hsv[0] in 165f..215f && hsv[1] > 0.24f && hsv[2] > 0.22f
    }

    private fun largestComponent(mask: BooleanArray, width: Int, height: Int): IntArray? {
        val visited = BooleanArray(mask.size)
        val queue = ArrayDeque<Int>()
        var best: IntArray? = null
        var bestCount = 0

        for (start in mask.indices) {
            if (!mask[start] || visited[start]) continue
            visited[start] = true
            queue.add(start)
            var minX = start % width
            var maxX = minX
            var minY = start / width
            var maxY = minY
            var count = 0

            while (queue.isNotEmpty()) {
                val p = queue.removeFirst()
                val x = p % width
                val y = p / width
                count++
                minX = minOf(minX, x); maxX = maxOf(maxX, x)
                minY = minOf(minY, y); maxY = maxOf(maxY, y)

                if (x > 0) visit(p - 1, mask, visited, queue)
                if (x + 1 < width) visit(p + 1, mask, visited, queue)
                if (y > 0) visit(p - width, mask, visited, queue)
                if (y + 1 < height) visit(p + width, mask, visited, queue)
            }
            if (count > bestCount) {
                bestCount = count
                best = intArrayOf(minX, minY, maxX, maxY, count)
            }
        }
        return best
    }

    private fun visit(p: Int, mask: BooleanArray, visited: BooleanArray, queue: ArrayDeque<Int>) {
        if (mask[p] && !visited[p]) {
            visited[p] = true
            queue.add(p)
        }
    }

    private fun findBallComponents(bitmap: Bitmap, table: RectF, step: Int): List<Component> {
        val left = table.left.toInt().coerceAtLeast(0)
        val top = table.top.toInt().coerceAtLeast(0)
        val right = table.right.toInt().coerceAtMost(bitmap.width)
        val bottom = table.bottom.toInt().coerceAtMost(bitmap.height)
        val gw = ((right - left + step - 1) / step).coerceAtLeast(1)
        val gh = ((bottom - top + step - 1) / step).coerceAtLeast(1)
        val mask = BooleanArray(gw * gh)
        val white = BooleanArray(gw * gh)
        val hsv = FloatArray(3)

        for (gy in 0 until gh) {
            val y = (top + gy * step + step / 2).coerceAtMost(bottom - 1)
            for (gx in 0 until gw) {
                val x = (left + gx * step + step / 2).coerceAtMost(right - 1)
                Color.colorToHSV(bitmap.getPixel(x, y), hsv)
                val index = gy * gw + gx
                val neutralWhite = hsv[1] < 0.23f && hsv[2] > 0.72f
                val vividBall = hsv[1] > 0.48f && hsv[2] > 0.30f &&
                    !(hsv[0] in 165f..215f)
                white[index] = neutralWhite
                mask[index] = neutralWhite || vividBall
            }
        }

        val visited = BooleanArray(mask.size)
        val queue = ArrayDeque<Int>()
        val result = ArrayList<Component>()

        for (start in mask.indices) {
            if (!mask[start] || visited[start]) continue
            visited[start] = true
            queue.add(start)
            var minX = start % gw; var maxX = minX
            var minY = start / gw; var maxY = minY
            var count = 0
            var whiteCount = 0

            while (queue.isNotEmpty()) {
                val p = queue.removeFirst()
                val x = p % gw
                val y = p / gw
                count++
                if (white[p]) whiteCount++
                minX = minOf(minX, x); maxX = maxOf(maxX, x)
                minY = minOf(minY, y); maxY = maxOf(maxY, y)
                if (x > 0) visit(p - 1, mask, visited, queue)
                if (x + 1 < gw) visit(p + 1, mask, visited, queue)
                if (y > 0) visit(p - gw, mask, visited, queue)
                if (y + 1 < gh) visit(p + gw, mask, visited, queue)
            }

            val bw = maxX - minX + 1
            val bh = maxY - minY + 1
            val shape = bw.toFloat() / bh.coerceAtLeast(1)
            // Reject tiny highlights and long table decorations; pool balls
            // remain compact and approximately circular at every resolution.
            if (count in 5..260 && bw in 3..17 && bh in 3..17 && shape in 0.55f..1.8f) {
                result.add(
                    Component(
                        left + (minX + maxX + 1) * step / 2f,
                        top + (minY + maxY + 1) * step / 2f,
                        bw, bh, count, whiteCount.toFloat() / count
                    )
                )
            }
        }
        return result
    }
}
