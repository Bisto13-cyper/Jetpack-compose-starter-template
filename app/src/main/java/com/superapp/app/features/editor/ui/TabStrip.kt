package com.superapp.app.features.editor.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.superapp.app.features.editor.data.EditorSettingsStore
import com.superapp.app.features.editor.model.OpenDocument

@Composable
fun TabStrip(
    docs: List<OpenDocument>,
    activeIndex: Int,
    onSelect: (Int) -> Unit,
    onClose: (Int) -> Unit
) {
    val s = EditorSettingsStore.settings
    Row(
        Modifier
            .horizontalScroll(rememberScrollState())
            .background(Color(s.backgroundColor))
            .padding(horizontal = 4.dp, vertical = 2.dp)
    ) {
        docs.forEachIndexed { i, doc ->
            val selected = i == activeIndex
            Row(
                Modifier
                    .clickable { onSelect(i) }
                    .background(
                        if (selected) Color(s.accentColor).copy(alpha = 0.25f)
                        else Color.Transparent
                    )
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Text(
                    text = doc.name + if (doc.isDirty) " •" else "",
                    color = if (selected) Color(s.accentColor) else Color(s.textColor),
                    fontSize = 13.sp,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                )
                Text(
                    text = " ×",
                    color = Color(s.lineNumberColor),
                    fontSize = 13.sp,
                    modifier = Modifier
                        .padding(start = 4.dp)
                        .clickable { onClose(i) }
                )
            }
        }
    }
}
