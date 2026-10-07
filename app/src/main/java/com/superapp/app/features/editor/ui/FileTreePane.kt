package com.superapp.app.features.editor.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.superapp.app.features.editor.data.EditorSettingsStore
import com.superapp.app.features.editor.model.WorkspaceNode
import com.superapp.app.features.editor.workspace.WorkspaceRepository

@Composable
fun FileTreePane(
    root: WorkspaceNode?,
    onOpenFile: (WorkspaceNode) -> Unit
) {
    val context = LocalContext.current
    val s = EditorSettingsStore.settings
    var tree by remember(root?.uri) { mutableStateOf(root) }

    LaunchedEffect(root?.uri) {
        if (root != null && root.children == null) {
            tree = root.copy(children = WorkspaceRepository.listChildren(context, root.uri))
        } else {
            tree = root
        }
    }

    if (tree == null) {
        Text(
            "No workspace",
            color = Color(s.lineNumberColor),
            fontSize = 13.sp,
            modifier = Modifier.padding(12.dp)
        )
        return
    }

    LazyColumn(Modifier.fillMaxWidth()) {
        items(tree!!.children.orEmpty()) { node ->
            TreeItem(node, 0, onOpenFile) { updated ->
                // simple in-memory expand
                tree = updateChild(tree!!, updated)
            }
        }
    }
}

@Composable
private fun TreeItem(
    node: WorkspaceNode,
    depth: Int,
    onOpenFile: (WorkspaceNode) -> Unit,
    onExpand: (WorkspaceNode) -> Unit
) {
    val context = LocalContext.current
    val s = EditorSettingsStore.settings
    var expanded by remember { mutableStateOf(false) }

    Row(
        Modifier
            .fillMaxWidth()
            .clickable {
                if (node.isDirectory) {
                    if (!node.isLoaded) {
                        val children = WorkspaceRepository.listChildren(context, node.uri)
                        onExpand(node.copy(children = children))
                    }
                    expanded = !expanded
                } else {
                    onOpenFile(node)
                }
            }
            .padding(start = (depth * 12).dp, top = 4.dp, bottom = 4.dp, end = 8.dp)
    ) {
        Text(
            text = if (node.isDirectory) (if (expanded) "📂 " else "📁 ") else "📄 ",
            fontSize = 13.sp
        )
        Text(
            text = node.name,
            color = Color(s.textColor),
            fontSize = 13.sp
        )
    }
    if (node.isDirectory && expanded) {
        node.children?.forEach { child ->
            TreeItem(child, depth + 1, onOpenFile, onExpand)
        }
    }
}

private fun updateChild(root: WorkspaceNode, updated: WorkspaceNode): WorkspaceNode {
    if (root.uri == updated.uri) return updated
    val kids = root.children?.map { updateChild(it, updated) }
    return root.copy(children = kids)
}
