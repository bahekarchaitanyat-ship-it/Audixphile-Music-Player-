package com.audixphile.player

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import kotlin.math.*

/** Real-time spectrum bars, fed from the DSP chain (no microphone permission needed). */
class VisualizerView(c: Context, a: AttributeSet? = null) : View(c, a) {
    private val buf = FloatArray(512)
    private val re = FloatArray(512)
    private val im = FloatArray(512)
    private val bars = FloatArray(32)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    private fun fft() {
        val n = re.size; var j = 0
        for (i in 1 until n) {
            var bit = n shr 1
            while ((j and bit) != 0) { j = j xor bit; bit = bit shr 1 }
            j = j xor bit
            if (i < j) { val t = re[i]; re[i] = re[j]; re[j] = t }
        }
        var len = 2
        while (len <= n) {
            val ang = -2 * PI / len; val wr = cos(ang).toFloat(); val wi = sin(ang).toFloat()
            var i = 0
            while (i < n) {
                var cr = 1f; var ci = 0f
                for (k in 0 until len / 2) {
                    val u = i + k; val v = u + len / 2
                    val tr = re[v] * cr - im[v] * ci; val ti = re[v] * ci + im[v] * cr
                    re[v] = re[u] - tr; im[v] = im[u] - ti; re[u] += tr; im[u] += ti
                    val nr = cr * wr - ci * wi; ci = cr * wi + ci * wr; cr = nr
                }
                i += len
            }
            len = len shl 1
        }
    }

    override fun onDraw(cv: Canvas) {
        if (Audix.ctl?.isPlaying == true) Audix.dsp.snapshot(buf) else buf.fill(0f)
        for (i in 0 until 512) { re[i] = buf[i] * (0.5f - 0.5f * cos(2 * PI.toFloat() * i / 511)); im[i] = 0f }
        fft()
        paint.color = Audix.accent(context)
        val w = width / 32f
        for (b in 0 until 32) {
            val lo = 1 + (255 * (b / 32f).pow(2)).toInt()
            val hi = max(lo + 1, 1 + (255 * ((b + 1) / 32f).pow(2)).toInt())
            var s = 0f
            for (k in lo until hi) s += sqrt(re[k] * re[k] + im[k] * im[k])
            val m = s / (hi - lo) / 128f
            val lvl = ((20 * log10(m + 1e-5f) + 60) / 60f).coerceIn(0f, 1f)
            bars[b] = max(lvl, bars[b] * 0.85f)
            cv.drawRoundRect(b * w + 2, height * (1 - bars[b]), (b + 1) * w - 2, height.toFloat(), 6f, 6f, paint)
        }
        postInvalidateOnAnimation()
    }
}
