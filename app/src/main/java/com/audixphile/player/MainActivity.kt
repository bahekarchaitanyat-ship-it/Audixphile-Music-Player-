package com.audixphile.player

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.content.ContentUris
import android.content.ContentValues
import android.net.Uri
import android.provider.MediaStore
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import android.os.*
import android.view.*
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
import androidx.media3.common.MediaItem
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.tabs.TabLayout

class MainActivity : BaseActivity() {
    class Row(val t: String, val s: String, val art: Uri?, val grp: List<MediaItem>?)
    private val h = Handler(Looper.getMainLooper())
    private var lastId = ""
    private var tab = 0
    private var query = ""
    private var open: List<MediaItem>? = null
    private var queue = listOf<MediaItem>()
    private var rows = listOf<Row>()
    private val adapter = A()
    private lateinit var mTitle: TextView
    private lateinit var mPlay: ImageButton
    private lateinit var mArt: ImageView

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        setContentView(R.layout.activity_main)
        val tb = findViewById<MaterialToolbar>(R.id.toolbar)
        val sv = SearchView(this)
        sv.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(s: String?) = false
            override fun onQueryTextChange(s: String?): Boolean { query = s ?: ""; refresh(); return true }
        })
        tb.menu.add("Search").setActionView(sv).setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS or MenuItem.SHOW_AS_ACTION_COLLAPSE_ACTION_VIEW)
        tb.menu.add("Equalizer").setOnMenuItemClickListener { startActivity(Intent(this, EqualizerActivity::class.java)); true }
        tb.menu.add("Settings").setOnMenuItemClickListener { startActivity(Intent(this, SettingsActivity::class.java)); true }
        val tabs = findViewById<TabLayout>(R.id.tabs)
        listOf("Songs", "Albums", "Artists", "Folders").forEach { tabs.addTab(tabs.newTab().setText(it)) }
        tabs.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(t: TabLayout.Tab) { tab = t.position; open = null; refresh() }
            override fun onTabUnselected(t: TabLayout.Tab) {}
            override fun onTabReselected(t: TabLayout.Tab) { open = null; refresh() }
        })
        mTitle = findViewById(R.id.mTitle); mPlay = findViewById(R.id.mPlay); mArt = findViewById(R.id.mArt)
        findViewById<View>(R.id.mini).setOnClickListener { startActivity(Intent(this, NowPlayingActivity::class.java)) }
        mPlay.setOnClickListener { Audix.ctl?.run { if (isPlaying) pause() else play() } }
        findViewById<RecyclerView>(R.id.list).apply { layoutManager = LinearLayoutManager(context); adapter = this@MainActivity.adapter }
        val perms = if (Build.VERSION.SDK_INT >= 33)
            arrayOf(Manifest.permission.READ_MEDIA_AUDIO, Manifest.permission.POST_NOTIFICATIONS)
        else arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE)
        if (perms.all { checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED }) start() else requestPermissions(perms, 1)
    }

    override fun onRequestPermissionsResult(c: Int, p: Array<out String>, r: IntArray) {
        super.onRequestPermissionsResult(c, p, r); start()
    }

    private fun start() { Audix.load(this); refresh(); Audix.connect(this) { tick() } }

    private fun refresh() {
        val base = open ?: Audix.songs
        val q = query.lowercase()
        val f = if (q.isEmpty()) base else base.filter {
            "${it.mediaMetadata.title} ${it.mediaMetadata.artist} ${it.mediaMetadata.albumTitle}".lowercase().contains(q) }
        queue = f
        rows = if (open != null || tab == 0)
            f.map { Row(it.mediaMetadata.title.toString(), it.mediaMetadata.artist.toString(), it.mediaMetadata.artworkUri, null) }
        else f.groupBy {
            val m = it.mediaMetadata
            when (tab) { 1 -> m.albumTitle.toString(); 2 -> m.artist.toString(); else -> (m.extras?.getString("path") ?: "").substringBeforeLast('/') }
        }.map { (k, v) -> Row(if (tab == 3) k.substringAfterLast('/') else k, "${v.size} songs", v[0].mediaMetadata.artworkUri, v) }
            .sortedBy { it.t.lowercase() }
        adapter.notifyDataSetChanged()
    }

    @Deprecated("back handling")
    override fun onBackPressed() { if (open != null) { open = null; refresh() } else super.onBackPressed() }

    private fun tick() {
        Audix.ctl?.let {
            val m = it.mediaMetadata
            mTitle.text = m.title ?: "Audixphile"
            mPlay.setImageResource(if (it.isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play)
            val id = it.currentMediaItem?.mediaId ?: ""
            if (id != lastId) { lastId = id; mArt.setImageURI(null); mArt.setImageURI(m.artworkUri) }
        }
        h.postDelayed({ tick() }, 500)
    }

    private fun editTags(it: MediaItem) {
        val m = it.mediaMetadata
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(48, 16, 48, 0) }
        val f = listOf("Title" to m.title, "Artist" to m.artist, "Album" to m.albumTitle).map { (h, v) ->
            EditText(this).apply { hint = h; setText(v?.toString() ?: ""); box.addView(this) } }
        MaterialAlertDialogBuilder(this).setTitle("Edit song info").setView(box).setPositiveButton("Save") { _, _ ->
            try {
                val uri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, it.mediaId.toLong())
                contentResolver.update(uri, ContentValues().apply {
                    put("title", f[0].text.toString()); put("artist", f[1].text.toString()); put("album", f[2].text.toString()) }, null, null)
                Audix.songs.clear(); Audix.load(this); refresh()
            } catch (e: Exception) { Toast.makeText(this, "Could not save: " + e.message, Toast.LENGTH_LONG).show() }
        }.setNegativeButton("Cancel", null).show()
    }

    inner class A : RecyclerView.Adapter<A.V>() {
        inner class V(v: View) : RecyclerView.ViewHolder(v) {
            val art: ImageView = v.findViewById(R.id.art)
            val t: TextView = v.findViewById(R.id.t)
            val s: TextView = v.findViewById(R.id.s)
        }
        override fun onCreateViewHolder(p: ViewGroup, t: Int) = V(layoutInflater.inflate(R.layout.item_song, p, false))
        override fun getItemCount() = rows.size
        override fun onBindViewHolder(v: V, i: Int) {
            val r = rows[i]
            v.t.text = r.t; v.s.text = r.s
            v.art.setImageURI(null); v.art.setImageURI(r.art)
            v.itemView.setOnLongClickListener { if (r.grp == null) editTags(queue[i]); true }
            v.itemView.setOnClickListener {
                if (r.grp != null) { open = r.grp; refresh() }
                else Audix.ctl?.run { setMediaItems(queue, i, 0); prepare(); play() }
            }
        }
    }

    override fun onDestroy() { h.removeCallbacksAndMessages(null); super.onDestroy() }
}
