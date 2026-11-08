package com.audixphile.player

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

open class BaseActivity : AppCompatActivity() {
    override fun onCreate(b: Bundle?) {
        val skins = listOf(R.style.Skin_Teal, R.style.Skin_Violet, R.style.Skin_Amber, R.style.Skin_Rose, R.style.Skin_Amoled)
        setTheme(skins[getSharedPreferences("audix", 0).getInt("skin", 0).coerceIn(0, skins.size - 1)])
        super.onCreate(b)
    }
}
