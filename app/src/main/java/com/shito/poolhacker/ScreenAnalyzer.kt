package com.shito.poolhacker

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.PointF
import android.graphics.RectF

data class DetectionResult(val table: RectF? = null, val cue: PointF? = null, val balls: List<PointF> = emptyList())

object ScreenAnalyzer {
    fun analyze(b: Bitmap): DetectionResult {
        var l=b.width; var t=b.height; var r=0; var bot=0; var n=0
        val step=4
        for(y in 0 until b.height step step) for(x in 0 until b.width step step) {
            val c=b.getPixel(x,y); val hsv=FloatArray(3); Color.colorToHSV(c,hsv)
            if(hsv[1]>.30f && hsv[2]>.15f && (hsv[0] in 65f..175f || hsv[0] in 175f..255f || hsv[0] in 12f..48f)) {
                l=minOf(l,x); t=minOf(t,y); r=maxOf(r,x); bot=maxOf(bot,y); n++
            }
        }
        if(n<450 || r-l<b.width/4 || bot-t<b.height/5) return DetectionResult()
        val table=RectF(l.toFloat(),t.toFloat(),r.toFloat(),bot.toFloat())
        var cue:PointF?=null; var best=0
        for(y in (t+14) until (bot-14) step 8) for(x in (l+14) until (r-14) step 8) {
            var score=0
            for(dy in -12..12 step 4) for(dx in -12..12 step 4) if(dx*dx+dy*dy<=144) {
                val c=b.getPixel(x+dx,y+dy); val rr=Color.red(c); val gg=Color.green(c); val bb=Color.blue(c)
                if(rr>205 && gg>205 && bb>195 && maxOf(rr,gg,bb)-minOf(rr,gg,bb)<45) score++
            }
            if(score>best){best=score;cue=PointF(x.toFloat(),y.toFloat())}
        }
        return DetectionResult(table,if(best>=5) cue else null)
    }
}