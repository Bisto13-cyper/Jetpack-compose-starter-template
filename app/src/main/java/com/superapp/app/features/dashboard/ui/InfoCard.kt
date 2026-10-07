package com.superapp.app.features.dashboard.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.superapp.app.features.dashboard.CardLook
import com.superapp.app.features.dashboard.CardShape
import com.superapp.app.features.dashboard.DashboardStyle
import com.superapp.app.features.dashboard.InfoSection
import com.superapp.app.features.dashboard.UNAVAILABLE

fun DashboardStyle.shape(): Shape = when (cardShape) {
    CardShape.ROUNDED -> RoundedCornerShape(cornerRadiusDp.dp)
    CardShape.CUT -> CutCornerShape(cornerRadiusDp.dp)
    CardShape.SQUARE -> RectangleShape
}

/** Reusable information card used by every dashboard category. */
@Composable
fun InfoCard(
    section: InfoSection,
    style: DashboardStyle,
    modifier: Modifier = Modifier,
    onAction: (String) -> Unit = {},
    extra: @Composable (() -> Unit)? = null
) {
    val scheme = MaterialTheme.colorScheme
    val text = style.textColor?.let { Color(it) } ?: scheme.onSurface
    val accent = style.accentColor?.let { Color(it) } ?: scheme.primary
    val progressColor = style.progressColor?.let { Color(it) } ?: accent
    val container = (style.cardColor?.let { Color(it) } ?: scheme.surfaceVariant)
        .copy(alpha = style.cardAlpha.coerceIn(0f, 1f))
    val outlined = style.cardLook == CardLook.OUTLINED

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = style.shape(),
        color = if (outlined) container.copy(alpha = container.alpha * 0.35f) else container,
        contentColor = text,
        border = if (outlined) BorderStroke(1.dp, accent) else null
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(section.title, color = accent, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)

            section.progress?.let { p ->
                Box(
                    Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp))
                        .background(text.copy(alpha = 0.15f))
                ) {
                    Box(
                        Modifier.fillMaxWidth(p.coerceIn(0f, 1f)).fillMaxHeight()
                            .background(progressColor)
                    )
                }
            }

            section.rows.forEach { row ->
                Column {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(row.label, color = text.copy(alpha = 0.7f), modifier = Modifier.weight(1f))
                        if (row.value != null) {
                            Text(row.value, color = text, textAlign = TextAlign.End, modifier = Modifier.weight(1.2f))
                        } else {
                            Text(
                                UNAVAILABLE, color = text.copy(alpha = 0.45f), fontStyle = FontStyle.Italic,
                                textAlign = TextAlign.End, modifier = Modifier.weight(1.2f)
                            )
                        }
                    }
                    if (row.note != null) {
                        Text(row.note, color = text.copy(alpha = 0.5f), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            extra?.invoke()

            if (section.actionId != null && section.actionLabel != null) {
                TextButton(onClick = { onAction(section.actionId) }) { Text(section.actionLabel, color = accent) }
            }
            section.footnote?.let {
                Text(it, color = text.copy(alpha = 0.5f), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
