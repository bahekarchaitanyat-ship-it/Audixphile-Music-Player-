package com.audixphile.player

import android.os.Bundle
import org.json.JSONObject
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.slider.Slider
import com.google.android.material.switchmaterial.SwitchMaterial

class EqualizerActivity : BaseActivity() {
    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        val p = getSharedPreferences("audix", 0)
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(32, 32, 32, 32) }
        setContentView(ScrollView(this).apply { addView(box) })
        fun sw(label: String, key: String) = box.addView(SwitchMaterial(this).apply {
            text = label; setTextColor(-1); isChecked = p.getBoolean(key, true)
            setOnCheckedChangeListener { _, v -> p.edit().putBoolean(key, v).apply() } })
        fun sl(label: String, key: String) {
            box.addView(TextView(this).apply { text = label; setTextColor(-1) })
            box.addView(Slider(this).apply {
                valueFrom = -12f; valueTo = 12f; stepSize = 0.5f; value = p.getFloat(key, 0f).coerceIn(-12f, 12f)
                addOnChangeListener { _, v, _ -> p.edit().putFloat(key, v).apply() } })
        }
        sw("Enable DSP", "dsp"); sw("Limiter (anti-clipping)", "lim")
        sl("Preamp (dB)", "pre"); sl("Bass (dB)", "bass"); sl("Treble (dB)", "treble")
        Audix.FREQ.forEachIndexed { i, f -> sl("${f.toInt()} Hz", "g$i") }
        box.addView(TextView(this).apply { text = "Parametric / AutoEQ profile"; setTextColor(-1) })
        val et = EditText(this).apply {
            setText(p.getString("peq", "")); setTextColor(-1); setHintTextColor(0xFF9AA3B5.toInt()); minLines = 5
            hint = "Preamp: -6.0 dB\nFilter 1: ON PK Fc 105 Hz Gain 3.5 dB Q 0.70" }
        box.addView(et)
        box.addView(Button(this).apply { text = "Apply profile"; setOnClickListener { p.edit().putString("peq", et.text.toString()).apply() } })
        val keys = listOf("pre", "bass", "treble") + (0..9).map { "g$it" }
        box.addView(Button(this).apply { text = "Save preset"; setOnClickListener {
            val n = EditText(this@EqualizerActivity)
            MaterialAlertDialogBuilder(this@EqualizerActivity).setTitle("Preset name").setView(n).setPositiveButton("Save") { _, _ ->
                val name = n.text.toString().trim()
                if (name.isNotEmpty()) {
                    val j = JSONObject()
                    keys.forEach { j.put(it, p.getFloat(it, 0f).toDouble()) }
                    j.put("peq", p.getString("peq", ""))
                    p.edit().putString("preset_$name", j.toString())
                        .putStringSet("presets", (p.getStringSet("presets", setOf()) ?: setOf()) + name).apply()
                }
            }.show() } })
        box.addView(Button(this).apply { text = "Load preset"; setOnClickListener {
            val names = (p.getStringSet("presets", setOf()) ?: setOf()).sorted()
            MaterialAlertDialogBuilder(this@EqualizerActivity).setTitle("Load preset").setItems(names.toTypedArray()) { _, i ->
                val j = JSONObject(p.getString("preset_${names[i]}", "{}")!!)
                val e = p.edit()
                j.keys().forEach { k -> if (k == "peq") e.putString(k, j.getString(k)) else e.putFloat(k, j.getDouble(k).toFloat()) }
                e.apply(); recreate() }.show() } })
        box.addView(Button(this).apply { text = "Reset all"; setOnClickListener {
            p.edit().apply { listOf("pre", "bass", "treble").forEach { remove(it) }; for (i in 0..9) remove("g$i"); remove("peq") }.apply()
            recreate() } })
    }
}
