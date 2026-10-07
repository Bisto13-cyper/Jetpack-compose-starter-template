package com.superapp.app.features.clock

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

/** Full-screen "ringing" UI shown over the lock screen with Stop / Snooze. */
class AlarmRingActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val request = RingRequest.fromIntent(intent)
        if (request == null) {
            finish()
            return
        }
        setContent {
            MaterialTheme {
                RingContent(
                    request = request,
                    onStop = { send(AlarmRingService.ACTION_STOP) },
                    onSnooze = { send(AlarmRingService.ACTION_SNOOZE) }
                )
            }
        }
    }

    private fun send(action: String) {
        startService(Intent(this, AlarmRingService::class.java).setAction(action))
        finish()
    }

    @Composable
    private fun RingContent(request: RingRequest, onStop: () -> Unit, onSnooze: () -> Unit) {
        // Close automatically when the service stops (notification Stop, auto-stop, etc.).
        LaunchedEffect(Unit) {
            delay(2_000)
            while (AlarmRingService.ringing) delay(500)
            finish()
        }
        Surface(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier.fillMaxSize().padding(32.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterVertically),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(request.title, fontSize = 28.sp, color = MaterialTheme.colorScheme.primary)
                Text(request.message, fontSize = 56.sp)
                Button(onClick = onStop, modifier = Modifier.fillMaxWidth()) { Text("Stop") }
                if (request.snoozeMinutes > 0) {
                    OutlinedButton(onClick = onSnooze, modifier = Modifier.fillMaxWidth()) {
                        Text("Snooze ${request.snoozeMinutes} min")
                    }
                }
            }
        }
    }
}
