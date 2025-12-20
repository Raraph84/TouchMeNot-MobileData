package com.djay.touchmenot_mm

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.djay.touchmenot_mm.ui.theme.TouchMeNot_MMTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        PrefsBridge.makeWorldReadable(this)
        setContent {
            TouchMeNot_MMTheme {
                SettingsScreen(this)
            }
        }
    }
}
