package com.superapp.app.core.ui

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.superapp.app.core.feature.FeatureIcon

/**
 * FILE 6 - One glowing circle with its name underneath.
 *
 * The home screen draws one of these for every feature.
 * It is tappable and shows any kind of FeatureIcon.
 *
 * HOW TO CHANGE THE LOOK:
 *  - Ring thickness  : change 3.dp in .border(...)
 *  - Glow strength   : change 0.35f in the drawBehind block
 *  - Glow size       : change 0.85f in the drawBehind block
 *  - Name text size  : change 12.sp
 *  - Icon size       : change the 0.4f / 0.45f / 0.5f factors in IconContent
 *
 * NOTE: for Custom icons (images the user picked), keep images small
 * (for example 256x256) so the home screen stays smooth.
 */
@Composable
fun FeatureCircle(
    title: String,
    icon: FeatureIcon,
    accent: Color,
    diameter: Dp,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme

    Column(
        modifier = modifier.width(diameter + 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(diameter)
                .drawBehind {
                    val glowRadius = this.size.minDimension * 0.85f
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(accent.copy(alpha = 0.35f), Color.Transparent),
                            center = center,
                            radius = glowRadius
                        ),
                        radius = glowRadius,
                        center = center
                    )
                }
                .clip(CircleShape)
                .background(colors.surface)
                .border(3.dp, accent, CircleShape)
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            IconContent(icon = icon, accent = accent, diameter = diameter)
        }

        Spacer(Modifier.height(6.dp))

        Text(
            text = title,
            color = colors.onBackground,
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun IconContent(icon: FeatureIcon, accent: Color, diameter: Dp) {
    when (icon) {
        is FeatureIcon.Emoji -> Text(
            text = icon.value,
            fontSize = (diameter.value * 0.4f).sp
        )

        is FeatureIcon.Vector -> Icon(
            imageVector = icon.image,
            contentDescription = null,
            tint = accent,
            modifier = Modifier.size(diameter * 0.45f)
        )

        is FeatureIcon.Drawable -> Image(
            painter = painterResource(icon.resId),
            contentDescription = null,
            modifier = Modifier.size(diameter * 0.5f)
        )

        is FeatureIcon.Custom -> CustomImage(path = icon.path, diameter = diameter)
    }
}

/** Shows an image file from the phone. Falls back to a picture emoji if it can't be read. */
@Composable
private fun CustomImage(path: String, diameter: Dp) {
    val bitmap = remember(path) {
        runCatching { BitmapFactory.decodeFile(path)?.asImageBitmap() }.getOrNull()
    }

    if (bitmap != null) {
        Image(
            bitmap = bitmap,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
    } else {
        Text(text = "🖼", fontSize = (diameter.value * 0.4f).sp)
    }
}
