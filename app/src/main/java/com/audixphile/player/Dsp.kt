package com.audixphile.player

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer
import kotlin.math.*

/** Own biquad DSP chain: graphic/parametric EQ, shelves, preamp, soft limiter. */
@androidx.annotation.OptIn(UnstableApi::class)
class Dsp : BaseAudioProcessor() {
    class Band(val type: Int, val f: Float, val g: Float, val q: Float) // 0 peak, 1 low shelf, 2 high shelf
    @Volatile private var bands = listOf<Band>()
    @Volatile private var pre = 0f
    @Volatile private var lim = true
    @Volatile private var dirty = true
    private var rate = 0; private var ch = 0; private var n = 0
    private var c = FloatArray(0); private var z = FloatArray(0)
    private var gain = 1f; private var limiter = true
    private val ring = FloatArray(1024)
    @Volatile private var wp = 0
    fun snapshot(o: FloatArray) { val w = wp; for (i in o.indices) o[i] = ring[(w - o.size + i) and 1023] }

    fun update(b: List<Band>, preDb: Float, limiter: Boolean) { bands = b; pre = preDb; lim = limiter; dirty = true }

    override fun onConfigure(f: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        if (f.encoding != C.ENCODING_PCM_16BIT) throw AudioProcessor.UnhandledAudioFormatException(f)
        rate = f.sampleRate; ch = f.channelCount; dirty = true
        return f
    }

    private fun build() {
        val b = bands; n = b.size; gain = 10f.pow(pre / 20f); limiter = lim
        c = FloatArray(n * 5); z = FloatArray(n * 2 * ch)
        b.forEachIndexed { i, x ->
            var b0 = 1f; var b1 = 0f; var b2 = 0f; var a0 = 1f; var a1 = 0f; var a2 = 0f
            if (x.f < rate / 2f) {
                val a = 10f.pow(x.g / 40f); val w = 2 * PI.toFloat() * x.f / rate
                val cs = cos(w); val sn = sin(w); val al = sn / (2 * x.q); val sa = 2 * sqrt(a) * (sn / 2 * sqrt(2f))
                when (x.type) {
                    0 -> { b0 = 1 + al * a; b1 = -2 * cs; b2 = 1 - al * a; a0 = 1 + al / a; a1 = -2 * cs; a2 = 1 - al / a }
                    1 -> { b0 = a * ((a + 1) - (a - 1) * cs + sa); b1 = 2 * a * ((a - 1) - (a + 1) * cs); b2 = a * ((a + 1) - (a - 1) * cs - sa)
                           a0 = (a + 1) + (a - 1) * cs + sa; a1 = -2 * ((a - 1) + (a + 1) * cs); a2 = (a + 1) + (a - 1) * cs - sa }
                    else -> { b0 = a * ((a + 1) + (a - 1) * cs + sa); b1 = -2 * a * ((a - 1) + (a + 1) * cs); b2 = a * ((a + 1) + (a - 1) * cs - sa)
                              a0 = (a + 1) - (a - 1) * cs + sa; a1 = 2 * ((a - 1) - (a + 1) * cs); a2 = (a + 1) - (a - 1) * cs - sa }
                }
            }
            c[i * 5] = b0 / a0; c[i * 5 + 1] = b1 / a0; c[i * 5 + 2] = b2 / a0; c[i * 5 + 3] = a1 / a0; c[i * 5 + 4] = a2 / a0
        }
    }

    override fun queueInput(input: ByteBuffer) {
        if (dirty) { dirty = false; build() }
        val out = replaceOutputBuffer(input.remaining())
        var k = 0
        while (input.remaining() >= 2) {
            var x = input.short / 32768f * gain
            val zo = k * n * 2
            for (i in 0 until n) {
                val o = i * 5; val s = zo + i * 2
                val y = c[o] * x + z[s]
                z[s] = c[o + 1] * x - c[o + 3] * y + z[s + 1]
                z[s + 1] = c[o + 2] * x - c[o + 4] * y
                x = y
            }
            val a = abs(x)
            if (limiter && a > 0.9f) x = sign(x) * (0.9f + 0.1f * tanh((a - 0.9f) / 0.1f))
            if (k == 0) { ring[wp] = x; wp = (wp + 1) and 1023 }
            out.putShort((x.coerceIn(-1f, 0.99997f) * 32768f).toInt().toShort())
            if (++k == ch) k = 0
        }
        out.flip()
    }
}
