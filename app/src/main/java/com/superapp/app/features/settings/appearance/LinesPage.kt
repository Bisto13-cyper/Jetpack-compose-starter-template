package com.superapp.app.features.settings.appearance

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.superapp.app.core.theme.LineStyle
import com.superapp.app.core.theme.ThemeSettings
import com.superapp.app.core.ui.ColorPicker
import com.superapp.app.core.settings.SettingsEnv
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * SETTINGS FILE 11 - Settings > Appearance > Connection Lines.
 *
 * Controls the lines between the center and the home circles:
 *  - Style     : Straight, Curved, or Pulse (a small light travels along each line)
 *  - Thickness : 0.5 to 6 dp
 *  - Visibility: how solid the lines are
 *  - Color     : the accent color, or any color you pick
 *
 * The preview at the top is animated and updates while you change things.
 *
 * Uses LabeledSlider and SwitchRow from GlowPage.kt (same folder).
 *
 * HOW TO CHANGE:
 *  - Thickness limits : change 0.5f..6f
 *  - Preview height   : change 180.dp
 *  - Add a new style  : add it to LineStyle (AppTheme.kt), draw it in
 *    HomeScreen.kt and in drawSpoke() below, then add a StyleChip.
 */
@Composable
fun LinesPage(env: SettingsEnv) {
    val colors = MaterialTheme.colorScheme
    val theme by env.repository.theme.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(
            text = "Preview",
            color = colors.primary,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold
        )
        LinePreview(theme)

        Text(
            text = "Style",
            color = colors.primary,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StyleChip("Straight", theme.lineStyle == LineStyle.STRAIGHT, Modifier.weight(1f)) {
                env.repository.updateTheme { it.copy(lineStyle = LineStyle.STRAIGHT) }
            }
            StyleChip("Curved", theme.lineStyle == LineStyle.CURVED, Modifier.weight(1f)) {
                env.repository.updateTheme { it.copy(lineStyle = LineStyle.CURVED) }
            }
            StyleChip("Pulse", theme.lineStyle == LineStyle.PULSE, Modifier.weight(1f)) {
                env.repository.updateTheme { it.copy(lineStyle = LineStyle.PULSE) }
            }
        }

        LabeledSlider(
            label = "Thickness",
            valueText = "%.1f dp".format(theme.lineWidth),
            value = theme.lineWidth,
            range = 0.5f..6f
        ) { v -> env.repository.updateTheme { it.copy(lineWidth = v) } }

        LabeledSlider(
            label = "Visibility",
            valueText = "${(theme.lineAlpha * 100).roundToInt()}%",
            value = theme.lineAlpha,
            range = 0f..1f
        ) { v -> env.repository.updateTheme { it.copy(lineAlpha = v) } }

        SwitchRow(
            label = "Use the accent color",
            checked = theme.lineColor == null
        ) { useAccent ->
            env.repository.updateTheme {
                it.copy(lineColor = if (useAccent) null else it.primary)
            }
        }

        if (theme.lineColor != null) {
            ColorPicker(
                title = "Line color",
                color = theme.lineColor ?: theme.primary
            ) { c -> env.repository.updateTheme { it.copy(lineColor = c) } }
        }

        OutlinedButton(
            onClick = {
                val d = ThemeSettings()
                env.repository.updateTheme {
                    it.copy(
                        lineStyle = d.lineStyle,
                        lineColor = d.lineColor,
                        lineWidth = d.lineWidth,
                        lineAlpha = d.lineAlpha
                    )
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Reset lines")
        }
    }
}

@Composable
private fun StyleChip(
    label: String,
    selected: Boolean,
    modifier: Modifier,
    onClick: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(12.dp)
    Box(
        modifier = modifier
            .clip(shape)
            .background(if (selected) colors.primary.copy(alpha = 0.2f) else colors.surface)
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = colors.primary.copy(alpha = if (selected) 1f else 0.3f),
                shape = shape
            )
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text = label, color = colors.onSurface, fontSize = 14.sp)
    }
}

/** A small animated sample: a center ring with six lines going out. */
@Composable
private fun LinePreview(theme: ThemeSettings) {
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(16.dp)

    val base = theme.lineColor?.let { Color(it) } ?: colors.primary
    val lineColor = base.copy(alpha = theme.lineAlpha.coerceIn(0f, 1f))

    val pulse: State<Float>? =
        if (theme.lineStyle == LineStyle.PULSE) {
            rememberInfiniteTransition(label = "previewPulse").animateFloat(
                initialValue = 0f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(tween(2400, easing = LinearEasing)),
                label = "previewPulseValue"
            )
        } else {
            null
        }

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(180.dp)
            .clip(shape)
            .background(colors.surface)
            .border(1.dp, colors.primary.copy(alpha = 0.4f), shape)
    ) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val stroke = theme.lineWidth.dp.toPx()
        val reachX = size.width * 0.38f
        val reachY = size.height * 0.36f

        for (i in 0 until 6) {
            val angle = Math.toRadians(-90.0 + 60.0 * i)
            val end = Offset(
                center.x + (cos(angle) * reachX).toFloat(),
                center.y + (sin(angle) * reachY).toFloat()
            )

            drawSpoke(center, end, theme.lineStyle, lineColor, stroke)

            // The little circle at the end of each line.
            drawCircle(
                color = base.copy(alpha = 0.8f),
                radius = 9.dp.toPx(),
                center = end,
                style = Stroke(width = 2.dp.toPx())
            )

            val phase = pulse?.value
            if (phase != null) {
                val t = (phase + i * 0.17f) % 1f
                drawCircle(
                    color = base.copy(alpha = 0.9f),
                    radius = stroke * 2.2f,
                    center = Offset(
                        center.x + (end.x - center.x) * t,
                        center.y + (end.y - center.y) * t
                    )
                )
            }
        }

        drawCircle(
            color = base.copy(alpha = 0.9f),
            radius = 14.dp.toPx(),
            center = center,
            style = Stroke(width = 3.dp.toPx())
        )
    }
}

/** Draws one line, straight or curved. Same bend as the Home screen. */
private fun DrawScope.drawSpoke(
    from: Offset,
    to: Offset,
    style: LineStyle,
    color: Color,
    stroke: Float
) {
    if (style == LineStyle.CURVED) {
        val midX = (from.x + to.x) / 2f
        val midY = (from.y + to.y) / 2f
        val bendX = -(to.y - from.y) * 0.18f
        val bendY = (to.x - from.x) * 0.18f
        val path = Path().apply {
            moveTo(from.x, from.y)
            quadraticBezierTo(midX + bendX, midY + bendY, to.x, to.y)
        }
        drawPath(
            path = path,
            color = color,
            style = Stroke(width = stroke, cap = StrokeCap.Round)
        )
    } else {
        drawLine(
            color = color,
            start = from,
            end = to,
            strokeWidth = stroke,
            cap = StrokeCap.Round
        )
    }
}
