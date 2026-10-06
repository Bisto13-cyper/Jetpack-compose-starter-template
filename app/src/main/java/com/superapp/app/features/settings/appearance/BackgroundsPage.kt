package com.superapp.app.features.settings.appearance

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.superapp.app.core.images.ImageStore
import com.superapp.app.core.settings.SettingsEnv
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * SETTINGS FILE 4 - Settings > Appearance > Backgrounds.
 *
 * The user taps "Choose photo", picks any photo from the phone, and it
 * becomes the app background immediately. No permission is needed:
 * the system photo picker is used.
 *
 * What it does with the photo:
 *  - copies it into the app's private storage (via ImageStore)
 *  - saves its path in the theme (backgroundImagePath)
 *  - MainActivity already draws that path behind every screen
 *  - deletes the previous background file so storage doesn't pile up
 *
 * HOW TO CHANGE:
 *  - Preview height        : change 200.dp
 *  - How faint the preview : change PREVIEW_ALPHA. It must match the
 *    alpha = 0.35f used in MainActivity (BackgroundImage), or the
 *    preview won't look like the real result.
 */
private const val PREVIEW_ALPHA = 0.35f

@Composable
fun BackgroundsPage(env: SettingsEnv) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val colors = MaterialTheme.colorScheme

    val theme by env.repository.theme.collectAsState()
    val currentPath = theme.backgroundImagePath

    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    // Load the preview off the main thread.
    val preview by produceState<ImageBitmap?>(initialValue = null, key1 = currentPath) {
        value = if (currentPath == null) null
        else withContext(Dispatchers.IO) { ImageStore.loadScaled(currentPath, 800) }
    }

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            busy = true
            message = null
            scope.launch {
                val saved = withContext(Dispatchers.IO) {
                    ImageStore.importImage(context, uri)
                }
                if (saved != null) {
                    val old = env.repository.theme.value.backgroundImagePath
                    env.repository.updateTheme { it.copy(backgroundImagePath = saved) }
                    withContext(Dispatchers.IO) { ImageStore.delete(context, old) }
                    message = "Background updated"
                } else {
                    message = "Could not read that photo. Try another one."
                }
                busy = false
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 24.dp)
    ) {
        Text(
            text = "Preview",
            color = colors.onBackground.copy(alpha = 0.7f),
            fontSize = 13.sp
        )
        Spacer(Modifier.height(8.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(colors.background)
                .border(1.dp, colors.primary.copy(alpha = 0.4f), RoundedCornerShape(16.dp)),
            contentAlignment = Alignment.Center
        ) {
            val image = preview
            if (image != null) {
                Image(
                    bitmap = image,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    alpha = PREVIEW_ALPHA,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Text(
                    text = if (currentPath == null) "No photo selected" else "Loading...",
                    color = colors.onBackground.copy(alpha = 0.6f)
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        Button(
            onClick = {
                picker.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                )
            },
            enabled = !busy,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (busy) "Saving..." else "Choose photo")
        }

        if (currentPath != null) {
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = {
                    val old = currentPath
                    env.repository.updateTheme { it.copy(backgroundImagePath = null) }
                    scope.launch {
                        withContext(Dispatchers.IO) { ImageStore.delete(context, old) }
                    }
                    message = "Photo removed"
                },
                enabled = !busy,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Remove photo")
            }
        }

        message?.let {
            Spacer(Modifier.height(12.dp))
            Text(text = it, color = colors.primary)
        }

        Spacer(Modifier.height(20.dp))
        Text(
            text = "The photo is copied into the app and stays on your phone. " +
                "Nothing is uploaded. It is shown faintly behind every screen " +
                "so the circles and text stay readable.",
            color = colors.onBackground.copy(alpha = 0.6f),
            fontSize = 12.sp
        )
    }
}
