package com.superapp.app.features.settings.appearance

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.superapp.app.core.images.ImageStore
import com.superapp.app.core.settings.FavoriteLook
import com.superapp.app.core.settings.FeatureStyle
import com.superapp.app.core.settings.FavoritesStore
import com.superapp.app.core.theme.ThemeSettings
import com.superapp.app.core.settings.SettingsEnv
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * SETTINGS FILE 9 - Settings > Appearance > Favorites.
 *
 * Save the app's current look and bring it back with one tap.
 *
 * How to use it:
 *  1. Set up colors and a background photo the way you like.
 *  2. Type a name (optional) and tap "Save current look".
 *  3. Change anything you want. Later tap "Apply" on that favorite and
 *     the colors, circle colors and the photo all come back at once.
 *
 * Each card shows a small preview of the saved colors and photo. A card
 * says "Active" when its colors match what the app uses right now.
 *
 * HOW TO CHANGE:
 *  - Preview size : change 64.dp in FavoriteCard
 *  - What a favorite remembers : see FavoritesStore.kt
 */
@Composable
fun FavoritesPage(env: SettingsEnv) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val colors = MaterialTheme.colorScheme

    val store = remember { FavoritesStore(context) }
    val favorites by store.items.collectAsState()
    val theme by env.repository.theme.collectAsState()

    var name by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = "Save your current colors, circle colors and background photo " +
                "as one favorite. Tap it later to bring everything back at once.",
            color = colors.onBackground.copy(alpha = 0.7f),
            fontSize = 13.sp
        )

        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("Name (optional)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Button(
            onClick = {
                scope.launch {
                    busy = true
                    val saved = withContext(Dispatchers.IO) {
                        store.saveCurrent(name, env.repository)
                    }
                    name = ""
                    message = "Saved \"${saved.name}\""
                    busy = false
                }
            },
            enabled = !busy,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Save current look")
        }

        message?.let { Text(text = it, color = colors.primary) }

        Spacer(Modifier.height(6.dp))
        Text(
            text = "Your favorites",
            color = colors.primary,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold
        )

        if (favorites.isEmpty()) {
            Text(
                text = "Nothing saved yet.",
                color = colors.onBackground.copy(alpha = 0.6f)
            )
        }

        favorites.forEach { look ->
            FavoriteCard(
                look = look,
                active = isSameLook(look, theme, env.repository.featureStyles.value),
                busy = busy,
                onApply = {
                    scope.launch {
                        busy = true
                        withContext(Dispatchers.IO) { store.apply(look, env.repository) }
                        message = "Applied \"${look.name}\""
                        busy = false
                    }
                },
                onDelete = {
                    scope.launch {
                        withContext(Dispatchers.IO) { store.delete(look.id) }
                        message = "Deleted \"${look.name}\""
                    }
                }
            )
        }
    }
}

/** True when the saved appearance matches the current appearance. */
private fun isSameLook(
    look: FavoriteLook,
    theme: ThemeSettings,
    styles: Map<String, FeatureStyle>
): Boolean {
    val savedTheme = look.theme
    val sameTheme =
        savedTheme.background == theme.background &&
            savedTheme.surface == theme.surface &&
            savedTheme.primary == theme.primary &&
            savedTheme.onBackground == theme.onBackground &&
            savedTheme.circleScale == theme.circleScale &&
            savedTheme.glowStrength == theme.glowStrength &&
            savedTheme.glowSize == theme.glowSize &&
            savedTheme.glowColor == theme.glowColor &&
            savedTheme.lineStyle == theme.lineStyle &&
            savedTheme.lineColor == theme.lineColor &&
            savedTheme.lineWidth == theme.lineWidth &&
            savedTheme.lineAlpha == theme.lineAlpha &&
            (savedTheme.backgroundImagePath != null) == (theme.backgroundImagePath != null)

    if (!sameTheme) return false

    val currentAccents = styles.mapNotNull { (id, style) ->
        style.accent?.let { id to it }
    }.toMap()

    return look.accents == currentAccents
}

@Composable
private fun FavoriteCard(
    look: FavoriteLook,
    active: Boolean,
    busy: Boolean,
    onApply: () -> Unit,
    onDelete: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(16.dp)
    val path = look.theme.backgroundImagePath

    val photo by produceState<ImageBitmap?>(initialValue = null, key1 = path) {
        value = if (path == null) null
        else withContext(Dispatchers.IO) { ImageStore.loadScaled(path, 200) }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(colors.surface)
            .border(1.dp, colors.primary.copy(alpha = if (active) 0.9f else 0.25f), shape)
            .padding(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // Small preview: the saved background color, photo and accent.
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(look.theme.background))
                    .border(2.dp, Color(look.theme.primary), RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                val image = photo
                if (image != null) {
                    Image(
                        bitmap = image,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        alpha = 0.35f,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                Box(
                    modifier = Modifier
                        .size(18.dp)
                        .clip(CircleShape)
                        .background(Color(look.theme.surface))
                        .border(3.dp, Color(look.theme.primary), CircleShape)
                )
            }

            Spacer(Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = look.name,
                    color = colors.onSurface,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = if (path != null) "Colors + photo" else "Colors only",
                    color = colors.onSurface.copy(alpha = 0.6f),
                    fontSize = 12.sp
                )
            }

            if (active) {
                Text(text = "Active", color = colors.primary, fontSize = 12.sp)
            }
        }

        Spacer(Modifier.height(12.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = onApply,
                enabled = !busy,
                modifier = Modifier.weight(1f)
            ) {
                Text("Apply")
            }
            OutlinedButton(
                onClick = onDelete,
                enabled = !busy
            ) {
                Text("Delete")
            }
        }
    }
}
