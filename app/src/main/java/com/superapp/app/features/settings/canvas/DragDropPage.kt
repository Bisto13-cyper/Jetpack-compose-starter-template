package com.superapp.app.features.settings.canvas

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.superapp.app.core.canvas.CanvasStore
import com.superapp.app.core.settings.SettingsEnv

/**
 * CANVAS FILE 5 - Settings > Canvas > Drag & Drop.
 *
 * Lets you place the home circles anywhere you like.
 *
 * How it works:
 *  1. Tap "Start moving circles" here.
 *  2. Open Home with the top button (the menu), or the phone's back button.
 *  3. Drag any circle to a new spot. The lines follow it.
 *  4. Tap "Done" in the bar at the bottom of Home.
 *
 * Moved circles keep their spot, even after closing the app. Circles you
 * never moved stay in the automatic ring. "Reset all positions" sends
 * every circle back to the automatic layout.
 *
 * Positions are saved as fractions of the screen area, so they still fit
 * on other screen sizes.
 *
 * HOW TO CHANGE:
 *  - The text shown here : edit the strings below
 *  - How far a circle may go : see coerceIn(-1.1f, 1.1f) in HomeScreen.kt
 */
@Composable
fun DragDropPage(env: SettingsEnv) {
    val context = LocalContext.current
    val colors = MaterialTheme.colorScheme

    val store = remember { CanvasStore.get(context) }
    val canvas by store.state.collectAsState()
    val moved = canvas.positions.size

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(
            text = "Drag the circles anywhere on the Home screen. " +
                "The lines follow them and every spot is remembered.",
            color = colors.onBackground.copy(alpha = 0.7f),
            fontSize = 13.sp
        )

        Text(
            text = if (canvas.editing) {
                "Edit mode is ON. Open Home and drag the circles."
            } else {
                "Edit mode is off."
            },
            color = colors.primary,
            fontSize = 15.sp
        )

        if (canvas.editing) {
            OutlinedButton(
                onClick = { store.update { it.copy(editing = false) } },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Stop moving circles")
            }
        } else {
            Button(
                onClick = { store.update { it.copy(editing = true) } },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Start moving circles")
            }
        }

        Text(
            text = if (moved == 0) {
                "No circle has a custom spot yet."
            } else {
                "$moved circle(s) have a custom spot."
            },
            color = colors.onBackground.copy(alpha = 0.7f),
            fontSize = 13.sp
        )

        OutlinedButton(
            onClick = { store.update { it.copy(positions = emptyMap()) } },
            enabled = moved > 0,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Reset all positions")
        }
    }
}
