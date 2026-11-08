package com.audixphile.player

import android.graphics.Typeface
import android.os.*
import android.text.*
import android.text.style.*
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.media3.common.Player
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.floatingactionbutton.FloatingActionButton
import java.io.File

class NowPlayingActivity : BaseActivity() {
    private val h = Handler(Looper.getMainLooper())
    private var lastId = ""
    private var lrc = listOf<Pair<Long, String>>()
    private lateinit var seek: SeekBar

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        setContentView(R.layout.activity_now_playing)
        findViewById<MaterialToolbar>(R.id.toolbar).setNavigationOnClickListener { finish() }
        seek = findViewById(R.id.seek)
        findViewById<android.view.View>(R.id.prev).setOnClickListener { Audix.ctl?.seekToPreviousMediaItem() }
        findViewById<android.view.View>(R.id.next).setOnClickListener { Audix.ctl?.seekToNextMediaItem() }
        findViewById<FloatingActionButton>(R.id.play).setOnClickListener { Audix.ctl?.run { if (isPlaying) pause() else play() } }
        findViewById<Button>(R.id.shuffle).setOnClickListener { Audix.ctl?.run { shuffleModeEnabled = !shuffleModeEnabled } }
        findViewById<Button>(R.id.repeat).setOnClickListener {
            Audix.ctl?.run { repeatMode = when (repeatMode) {
                Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                else -> Player.REPEAT_MODE_OFF } }
        }
        seek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar, v: Int, user: Boolean) { if (user) Audix.ctl?.seekTo(v.toLong()) }
            override fun onStartTrackingTouch(s: SeekBar) {}
            override fun onStopTrackingTouch(s: SeekBar) {}
        })
        Audix.connect(this) { tick() }
    }

    private fun parseLrc(path: String?): List<Pair<Long, String>> {
        if (path == null) return emptyList()
        val f = File(path.substringBeforeLast('.') + ".lrc")
        if (!f.exists()) return emptyList()
        val tag = Regex("\\[(\\d+):(\\d+)(?:[.:](\\d+))?]")
        val out = mutableListOf<Pair<Long, String>>()
        try {
            f.readLines().forEach { line ->
                val text = line.substringAfterLast(']').trim()
                tag.findAll(line).forEach { m ->
                    val frac = m.groupValues[3].padEnd(3, '0').take(3).toLong()
                    out += (m.groupValues[1].toLong() * 60000 + m.groupValues[2].toLong() * 1000 + frac) to text
                }
            }
        } catch (_: Exception) {}
        return out.sortedBy { it.first }
    }

    private fun fmt(ms: Long) = "%d:%02d".format(ms / 60000, ms / 1000 % 60)

    private fun tick() {
        val c = Audix.ctl
        if (c != null) {
            val m = c.mediaMetadata
            findViewById<TextView>(R.id.title).text = m.title
            findViewById<TextView>(R.id.artist).text = "${m.artist} • ${(m.extras?.getString("path") ?: "").substringAfterLast('.').uppercase()}"
            val id = c.currentMediaItem?.mediaId ?: ""
            if (id != lastId) {
                lastId = id
                findViewById<ImageView>(R.id.art).apply { setImageURI(null); setImageURI(m.artworkUri) }
                lrc = parseLrc(m.extras?.getString("path"))
            }
            val pos = c.currentPosition
            if (c.duration > 0) { seek.max = c.duration.toInt(); seek.progress = pos.toInt() }
            findViewById<TextView>(R.id.time).text = "${fmt(pos)} / ${fmt(maxOf(c.duration, 0))}"
            findViewById<FloatingActionButton>(R.id.play).setImageResource(
                if (c.isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play)
            findViewById<Button>(R.id.shuffle).text = if (c.shuffleModeEnabled) "Shuffle: on" else "Shuffle: off"
            findViewById<Button>(R.id.repeat).text = "Repeat: " + listOf("off", "one", "all")[c.repeatMode]
            showLyrics(pos)
        }
        h.postDelayed({ tick() }, 300)
    }

    private fun showLyrics(pos: Long) {
        val tv = findViewById<TextView>(R.id.lyrics)
        if (lrc.isEmpty()) { tv.text = "No synced lyrics found.\nPlace a .lrc file with the same name next to the song."; return }
        val cur = lrc.indexOfLast { it.first <= pos }
        val sb = SpannableStringBuilder()
        for (i in maxOf(cur - 1, 0)..minOf(cur + 3, lrc.size - 1)) {
            val s = sb.length
            sb.append(lrc[i].second).append('\n')
            if (i == cur) {
                sb.setSpan(ForegroundColorSpan(Audix.accent(this)), s, sb.length, 0)
                sb.setSpan(StyleSpan(Typeface.BOLD), s, sb.length, 0)
                sb.setSpan(RelativeSizeSpan(1.3f), s, sb.length, 0)
            }
        }
        tv.text = sb
    }

    override fun onDestroy() { h.removeCallbacksAndMessages(null); super.onDestroy() }
}
