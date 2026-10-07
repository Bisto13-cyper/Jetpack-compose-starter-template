package com.superapp.app.features.keyboard

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme

/**
 * Hosts the keyboard customization screen.
 * Launched from the keyboard toolbar (⚙) and from the More panel.
 */
class KeyboardSettingsActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        KeyboardSettingsStore.init(this)
        KeyboardClipboard.init(this)

        setContent {
            MaterialTheme {
                KeyboardSettingsScreen(onClose = { finish() })
            }
        }
    }

    companion object {
        fun start(context: Context) {
            val intent = Intent(context, KeyboardSettingsActivity::class.java)
            if (context !is Activity) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }
    }
}
