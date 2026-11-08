package com.audixphile.player

import android.content.ComponentName
import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken

object Audix {
    var ctl: MediaController? = null
    val dsp = Dsp()
    val SKINS = listOf("Teal", "Violet", "Amber", "Rose", "AMOLED black")
    fun accent(c: Context): Int {
        val tv = android.util.TypedValue()
        c.theme.resolveAttribute(com.google.android.material.R.attr.colorSecondary, tv, true)
        return tv.data
    }
    val songs = mutableListOf<MediaItem>()
    val FREQ = floatArrayOf(31f, 62f, 125f, 250f, 500f, 1000f, 2000f, 4000f, 8000f, 16000f)
    private val h = Handler(Looper.getMainLooper())

    fun connect(c: Context, ready: () -> Unit) {
        if (ctl != null) { ready(); return }
        val t = SessionToken(c, ComponentName(c, PlaybackService::class.java))
        val f = MediaController.Builder(c.applicationContext, t).buildAsync()
        f.addListener({ ctl = f.get(); ready() }, { it.run() })
    }

    fun load(c: Context) {
        if (songs.isNotEmpty()) return
        val uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val m = MediaStore.Audio.Media::class.java
        c.contentResolver.query(uri, arrayOf("_id", "title", "artist", "album_id", "_data", "album"),
            "is_music!=0", null, "title COLLATE NOCASE")?.use { q ->
            while (q.moveToNext()) {
                val id = q.getLong(0)
                val art = ContentUris.withAppendedId(Uri.parse("content://media/external/audio/albumart"), q.getLong(3))
                songs += MediaItem.Builder().setMediaId(id.toString())
                    .setUri(ContentUris.withAppendedId(uri, id))
                    .setMediaMetadata(MediaMetadata.Builder().setTitle(q.getString(1)).setArtist(q.getString(2))
                        .setAlbumTitle(q.getString(5)).setArtworkUri(art).setExtras(Bundle().apply { putString("path", q.getString(4)) }).build())
                    .build()
            }
        }
    }

    fun sleep(min: Int) {
        h.removeCallbacksAndMessages(null)
        if (min > 0) h.postDelayed({ ctl?.pause() }, min * 60000L)
    }
}
