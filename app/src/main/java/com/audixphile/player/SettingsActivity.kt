package com.audixphile.player

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.slider.Slider
import com.google.android.material.switchmaterial.SwitchMaterial

class SettingsActivity : BaseActivity() {
    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        setContentView(R.layout.activity_settings)
        val p = getSharedPreferences("audix", 0)
        findViewById<MaterialToolbar>(R.id.toolbar).setNavigationOnClickListener { finish() }
        findViewById<Slider>(R.id.speed).apply {
            value = p.getFloat("speed", 1f); addOnChangeListener { _, v, _ -> p.edit().putFloat("speed", v).apply() } }
        findViewById<Slider>(R.id.cf).apply {
            value = p.getFloat("cf", 0f).coerceIn(0f, 12f); addOnChangeListener { _, v, _ -> p.edit().putFloat("cf", v).apply() } }
        findViewById<SwitchMaterial>(R.id.skip).apply {
            isChecked = p.getBoolean("skip", false); setOnCheckedChangeListener { _, on -> p.edit().putBoolean("skip", on).apply() } }
        findViewById<Button>(R.id.eq).setOnClickListener { startActivity(Intent(this, EqualizerActivity::class.java)) }
        findViewById<Button>(R.id.skin).setOnClickListener {
            MaterialAlertDialogBuilder(this).setTitle("Skin").setItems(Audix.SKINS.toTypedArray()) { _, i ->
                p.edit().putInt("skin", i).apply(); recreate() }.show()
        }
        findViewById<Button>(R.id.sleep).setOnClickListener {
            val mins = intArrayOf(0, 15, 30, 60)
            MaterialAlertDialogBuilder(this).setTitle("Sleep timer")
                .setItems(arrayOf("Off", "15 minutes", "30 minutes", "60 minutes")) { _, i -> Audix.sleep(mins[i]) }.show()
        }
    }
}
