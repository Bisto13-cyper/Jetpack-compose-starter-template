package com.superapp.app.features.editor

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.superapp.app.features.editor.data.EditorSettingsStore
import com.superapp.app.features.editor.data.WorkspaceStore
import com.superapp.app.features.editor.model.OpenDocument
import com.superapp.app.features.editor.model.WorkspaceNode
import com.superapp.app.features.editor.search.FileHit
import com.superapp.app.features.editor.ui.CodeEditorView
import com.superapp.app.features.editor.ui.EditorQuickToolbar
import com.superapp.app.features.editor.ui.EditorSettingsScreen
import com.superapp.app.features.editor.ui.FileTreePane
import com.superapp.app.features.editor.ui.FindReplaceBar
import com.superapp.app.features.editor.ui.TabStrip
import com.superapp.app.features.editor.ui.WorkspaceSearchOverlay
import com.superapp.app.features.editor.workspace.WorkspaceRepository
import kotlinx.coroutines.launch

@Composable
fun EditorRoot() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings = EditorSettingsStore.settings

    val openDocs = remember { mutableStateListOf<OpenDocument>() }
    var activeIndex by remember { mutableIntStateOf(0) }
    var rootNode by remember { mutableStateOf<WorkspaceNode?>(null) }
    var showSettings by remember { mutableStateOf(false) }
    var showSearch by remember { mutableStateOf(false) }
    var showFind by remember { mutableStateOf(false) }

    val openTree = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri != null) {
            WorkspaceStore.setWorkspace(context, uri)
            rootNode = WorkspaceNode(uri, "Workspace", isDirectory = true)
        }
    }

    // Restore workspace
    LaunchedEffect(Unit) {
        WorkspaceStore.workspaceUri?.let { uri ->
            rootNode = WorkspaceNode(uri, "Workspace", isDirectory = true)
        }
        val (tabs, active) = WorkspaceStore.loadOpenTabs()
        tabs.forEach { u ->
            val name = u.lastPathSegment?.substringAfterLast(':') ?: "file"
            scope.launch {
                val doc = WorkspaceRepository.readDocument(
                    context, u, name, settings.largeFileLimitBytes
                )
                openDocs.add(doc)
            }
        }
        activeIndex = active.coerceIn(0, (tabs.size - 1).coerceAtLeast(0))
    }

    // Persist tabs
    LaunchedEffect(openDocs.size, activeIndex) {
        WorkspaceStore.saveOpenTabs(openDocs.map { it.uri }, activeIndex)
    }

    if (showSettings) {
        EditorSettingsScreen(onClose = { showSettings = false })
        return
    }

    if (showSearch && rootNode != null) {
        WorkspaceSearchOverlay(
            root = rootNode!!,
            onClose = { showSearch = false },
            onOpenHit = { hit: FileHit ->
                showSearch = false
                scope.launch {
                    val existing = openDocs.indexOfFirst { it.uri == hit.uri }
                    if (existing >= 0) {
                        activeIndex = existing
                    } else {
                        val doc = WorkspaceRepository.readDocument(
                            context, hit.uri, hit.fileName, settings.largeFileLimitBytes
                        )
                        openDocs.add(doc)
                        activeIndex = openDocs.lastIndex
                    }
                }
            }
        )
        return
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(Color(settings.backgroundColor))
    ) {
        // Top bar
        Row(Modifier.fillMaxWidth()) {
            TextButton(onClick = { openTree.launch(null) }) {
                Text("Open folder", color = Color(settings.accentColor), fontSize = 13.sp)
            }
            if (WorkspaceStore.workspaceUri != null) {
                TextButton(onClick = {
                    WorkspaceStore.clearWorkspace()
                    rootNode = null
                    openDocs.clear()
                }) {
                    Text("Close workspace", color = Color(settings.lineNumberColor), fontSize = 12.sp)
                }
            }
        }

        EditorQuickToolbar(
            canSave = openDocs.getOrNull(activeIndex)?.isDirty == true,
            onSave = {
                val doc = openDocs.getOrNull(activeIndex) ?: return@EditorQuickToolbar
                scope.launch {
                    val ok = WorkspaceRepository.writeDocument(context, doc.uri, doc.content)
                    if (ok) {
                        openDocs[activeIndex] = doc.copy(savedContent = doc.content)
                    }
                }
            },
            onFind = { showFind = !showFind },
            onSearchWorkspace = { if (rootNode != null) showSearch = true },
            onSettings = { showSettings = true }
        )

        if (showFind && openDocs.isNotEmpty()) {
            val doc = openDocs[activeIndex]
            FindReplaceBar(
                text = doc.content,
                onReplace = { newText ->
                    openDocs[activeIndex] = doc.copy(content = newText)
                }
            )
        }

        TabStrip(
            docs = openDocs,
            activeIndex = activeIndex,
            onSelect = { activeIndex = it },
            onClose = { i ->
                openDocs.removeAt(i)
                if (activeIndex >= openDocs.size) activeIndex = (openDocs.size - 1).coerceAtLeast(0)
            }
        )

        Row(Modifier.fillMaxSize()) {
            // File tree
            Box(Modifier.width(200.dp)) {
                FileTreePane(
                    root = rootNode,
                    onOpenFile = { node ->
                        scope.launch {
                            val existing = openDocs.indexOfFirst { it.uri == node.uri }
                            if (existing >= 0) {
                                activeIndex = existing
                            } else {
                                val doc = WorkspaceRepository.readDocument(
                                    context, node.uri, node.name, settings.largeFileLimitBytes
                                )
                                openDocs.add(doc)
                                activeIndex = openDocs.lastIndex
                            }
                        }
                    }
                )
            }

            // Editor
            Box(Modifier.weight(1f)) {
                val doc = openDocs.getOrNull(activeIndex)
                if (doc != null) {
                    CodeEditorView(
                        document = doc,
                        onContentChange = { text ->
                            openDocs[activeIndex] = doc.copy(content = text)
                        }
                    )
                } else {
                    Text(
                        if (rootNode == null) "Open a folder to begin"
                        else "Select a file from the tree",
                        color = Color(settings.lineNumberColor),
                        fontSize = 14.sp,
                        modifier = Modifier.padding(24.dp)
                    )
                }
            }
        }
    }
}
