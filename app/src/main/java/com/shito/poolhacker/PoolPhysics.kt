package com.shito.poolhacker

import android.graphics.PointF
import android.graphics.RectF
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/**
 * Independent 2-D pool geometry engine.
 * Coordinates are screen pixels; no game memory or automated input is used.
 */
object PoolPhysics {
    data class AimSolution(
        val ghostBall: PointF,
        val cuePath: List<PointF>,
        val objectPath: List<PointF>,
        val pocket: PointF,
        val bankShot: Boolean,
        val score: Float
    )

    private data class Route(val points: List<PointF>, val pocket: PointF, val bank: Boolean, val length: Float)

    fun solve(
        cue: PointF,
        objectBalls: List<PointF>,
        table: RectF,
        ballRadius: Float
    ): AimSolution? {
        if (table.width() <= 0f || table.height() <= 0f) return null
        val radius = ballRadius.coerceAtLeast(2f)
        val pockets = listOf(
            PointF(table.left, table.top),
            PointF(table.centerX(), table.top),
            PointF(table.right, table.top),
            PointF(table.left, table.bottom),
            PointF(table.centerX(), table.bottom),
            PointF(table.right, table.bottom)
        )

        var best: AimSolution? = null
        for (target in objectBalls) {
            val routes = buildRoutes(target, pockets, table)
            for (route in routes) {
                val first = route.points.getOrNull(1) ?: continue
                val direction = unit(first.x - target.x, first.y - target.y) ?: continue
                val ghost = PointF(
                    target.x - direction.x * radius * 2f,
                    target.y - direction.y * radius * 2f
                )
                if (!inside(ghost, table, radius * 0.7f)) continue

                val cueBlocked = objectBalls.any { ball ->
                    ball !== target && distanceToSegment(ball, cue, ghost) < radius * 2.15f
                }
                val objectBlocked = route.points.zipWithNext().sumOf { (a, b) ->
                    objectBalls.count { ball ->
                        ball !== target && distanceToSegment(ball, a, b) < radius * 2.15f
                    }.toFloat()
                }
                val cueDistance = distance(cue, ghost)
                val routeDistance = route.length
                val score = cueDistance + routeDistance * 0.82f +
                    (if (cueBlocked) radius * 18f else 0f) +
                    objectBlocked * radius * 16f +
                    if (route.bank) radius * 1.8f else 0f
                val solution = AimSolution(
                    ghost,
                    listOf(PointF(cue.x, cue.y), ghost),
                    route.points,
                    route.pocket,
                    route.bank,
                    score
                )
                if (best == null || score < best!!.score) best = solution
            }
        }
        return best
    }

    private fun buildRoutes(target: PointF, pockets: List<PointF>, table: RectF): List<Route> {
        val result = ArrayList<Route>()
        for (pocket in pockets) {
            result.add(Route(listOf(PointF(target.x, target.y), pocket), pocket, false, distance(target, pocket)))
            val mirrors = listOf(
                PointF(pocket.x, 2f * table.top - pocket.y),
                PointF(pocket.x, 2f * table.bottom - pocket.y),
                PointF(2f * table.left - pocket.x, pocket.y),
                PointF(2f * table.right - pocket.x, pocket.y)
            )
            for ((index, mirror) in mirrors.withIndex()) {
                val dx = mirror.x - target.x
                val dy = mirror.y - target.y
                val t = when (index) {
                    0 -> if (dy == 0f) continue else (table.top - target.y) / dy
                    1 -> if (dy == 0f) continue else (table.bottom - target.y) / dy
                    2 -> if (dx == 0f) continue else (table.left - target.x) / dx
                    else -> if (dx == 0f) continue else (table.right - target.x) / dx
                }
                if (t <= 0f || t >= 1f) continue
                val bank = PointF(target.x + dx * t, target.y + dy * t)
                val valid = when (index) {
                    0, 1 -> bank.x in table.left..table.right
                    else -> bank.y in table.top..table.bottom
                }
                if (!valid) continue
                result.add(
                    Route(
                        listOf(PointF(target.x, target.y), bank, pocket),
                        pocket,
                        true,
                        distance(target, bank) + distance(bank, pocket)
                    )
                )
            }
        }
        return result
    }

    private fun unit(x: Float, y: Float): PointF? {
        val length = hypot(x, y)
        if (length < 0.001f) return null
        return PointF(x / length, y / length)
    }

    private fun distance(a: PointF, b: PointF) = hypot(a.x - b.x, a.y - b.y)

    private fun inside(p: PointF, r: RectF, margin: Float) =
        p.x >= r.left + margin && p.x <= r.right - margin &&
            p.y >= r.top + margin && p.y <= r.bottom - margin

    private fun distanceToSegment(p: PointF, a: PointF, b: PointF): Float {
        val dx = b.x - a.x
        val dy = b.y - a.y
        val denominator = dx * dx + dy * dy
        if (denominator < 0.001f) return distance(p, a)
        val t = (((p.x - a.x) * dx + (p.y - a.y) * dy) / denominator).coerceIn(0f, 1f)
        return hypot(p.x - (a.x + t * dx), p.y - (a.y + t * dy))
    }
}
