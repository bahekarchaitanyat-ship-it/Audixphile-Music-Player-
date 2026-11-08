package com.audixphile.player

import android.content.Context
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import androidx.media3.common.AudioAttributes
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService

@androidx.annotation.OptIn(UnstableApi::class)
class PlaybackService : MediaSessionService(), SharedPreferences.OnSharedPreferenceChangeListener {
    private var session: MediaSession? = null
    private lateinit var player: ExoPlayer
    private lateinit var prefs: SharedPreferences
    private val dsp = Audix.dsp
    private val h = Handler(Looper.getMainLooper())

    override fun onCreate() {
        super.onCreate()
        prefs = getSharedPreferences("audix", 0)
        val rf = object : DefaultRenderersFactory(this) {
            override fun buildAudioSink(ctx: Context, enableFloat: Boolean, enableParams: Boolean): AudioSink =
                DefaultAudioSink.Builder(ctx).setAudioProcessors(arrayOf(dsp)).build()
        }
        player = ExoPlayer.Builder(this, rf).setHandleAudioBecomingNoisy(true)
            .setAudioAttributes(AudioAttributes.DEFAULT, true).build()
        session = MediaSession.Builder(this, player).build()
        prefs.registerOnSharedPreferenceChangeListener(this)
        apply(); fade.run()
    }

    private val fade = object : Runnable {
        override fun run() {
            val cf = prefs.getFloat("cf", 0f) * 1000f
            var v = 1f
            if (cf > 0 && player.isPlaying && player.duration > 0 && player.repeatMode != Player.REPEAT_MODE_ONE)
                v = minOf(1f, (player.duration - player.currentPosition) / cf, player.currentPosition / cf).coerceIn(0.02f, 1f)
            if (player.volume != v) player.volume = v
            h.postDelayed(this, 100)
        }
    }

    private fun apply() {
        player.setPlaybackSpeed(prefs.getFloat("speed", 1f))
        player.skipSilenceEnabled = prefs.getBoolean("skip", false)
        val b = mutableListOf<Dsp.Band>()
        var pre = prefs.getFloat("pre", 0f)
        if (prefs.getBoolean("dsp", true)) {
            Audix.FREQ.forEachIndexed { i, f -> prefs.getFloat("g$i", 0f).let { if (it != 0f) b += Dsp.Band(0, f, it, 1.41f) } }
            prefs.getFloat("bass", 0f).let { if (it != 0f) b += Dsp.Band(1, 100f, it, 0.7f) }
            prefs.getFloat("treble", 0f).let { if (it != 0f) b += Dsp.Band(2, 8000f, it, 0.7f) }
            val rx = Regex("(PK|LSC|HSC|LS|HS)\\s+Fc\\s+([\\d.]+)\\s*Hz\\s+Gain\\s+(-?[\\d.]+)\\s*dB(?:\\s+Q\\s+([\\d.]+))?", RegexOption.IGNORE_CASE)
            prefs.getString("peq", "")!!.lines().forEach { l ->
                Regex("Preamp:\\s*(-?[\\d.]+)", RegexOption.IGNORE_CASE).find(l)?.let { pre += it.groupValues[1].toFloat() }
                rx.find(l)?.let { m ->
                    val t = when (m.groupValues[1].uppercase()) { "PK" -> 0; "LSC", "LS" -> 1; else -> 2 }
                    b += Dsp.Band(t, m.groupValues[2].toFloat(), m.groupValues[3].toFloat(), m.groupValues[4].toFloatOrNull() ?: 0.7f)
                }
            }
        }
        dsp.update(b, pre, prefs.getBoolean("lim", true))
    }

    override fun onSharedPreferenceChanged(s: SharedPreferences?, k: String?) = apply()
    override fun onGetSession(c: MediaSession.ControllerInfo) = session

    override fun onDestroy() {
        h.removeCallbacksAndMessages(null)
        prefs.unregisterOnSharedPreferenceChangeListener(this)
        session?.run { player.release(); release() }
        session = null
        super.onDestroy()
    }
}
