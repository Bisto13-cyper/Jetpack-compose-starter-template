package com.superapp.app.features.favorites.ui

import android.content.Intent
import android.content.pm.ResolveInfo
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.superapp.app.features.favorites.data.FavoriteNode

private data class LaunchableApp(val label: String, val packageName: String, val activityName: String?)

@Composable
fun FavoritesScreen(
    parentId: String? = null,
    onOpenGroup: (String) -> Unit = {}
) {
    val vm: FavoritesViewModel = viewModel()
    val nodes by vm.nodes.collectAsState()
    val context = LocalContext.current
    var currentParent by remember { mutableStateOf(parentId) }
    var groupName by remember { mutableStateOf("") }
    var showCreateGroup by remember { mutableStateOf(false) }
    var showAppPicker by remember { mutableStateOf(false) }
    val visible = nodes.filter { it.parentId == currentParent }

    val launchableApps = remember(showAppPicker) {
        if (!showAppPicker) emptyList() else {
            val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            @Suppress("DEPRECATION")
            context.packageManager.queryIntentActivities(intent, 0)
                .mapNotNull { info: ResolveInfo ->
                    val activity = info.activityInfo ?: return@mapNotNull null
                    val label = info.loadLabel(context.packageManager)?.toString()?.takeIf { it.isNotBlank() }
                        ?: activity.packageName
                    LaunchableApp(label, activity.packageName, activity.name)
                }
                .distinctBy { it.packageName }
                .sortedBy { it.label.lowercase() }
        }
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (currentParent != null) {
                TextButton(onClick = {
                    currentParent = nodes.firstOrNull { it.id == currentParent }?.parentId
                }) { Text("← Back") }
            }
            TextButton(onClick = { showCreateGroup = true }) { Text("+ Group") }
            TextButton(onClick = { showAppPicker = true }) { Text("+ App") }
        }

        if (visible.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxSize().padding(24.dp),
                verticalArrangement = Arrangement.Center
            ) {
                Text(if (currentParent == null) "No favorites yet" else "This group is empty")
                Text("Add an app or create a group to get started.")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { showAppPicker = true }) { Text("Add app") }
                    TextButton(onClick = { showCreateGroup = true }) { Text("Create group") }
                }
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(visible, key = { it.id }) { node ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                when (node) {
                                    is FavoriteNode.Group -> {
                                        currentParent = node.id
                                        onOpenGroup(node.id)
                                    }
                                    is FavoriteNode.App -> {
                                        val intent = context.packageManager.getLaunchIntentForPackage(node.packageName)
                                        if (intent != null) runCatching {
                                            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                                        }
                                    }
                                }
                            }
                            .padding(vertical = 14.dp, horizontal = 8.dp)
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(if (node is FavoriteNode.Group) "📁  ${node.name}" else "⭐  ${node.name}")
                            if (node is FavoriteNode.Group) Text("Group")
                        }
                        TextButton(onClick = { vm.delete(node.id) }) { Text("Remove") }
                    }
                }
            }
        }
    }

    if (showCreateGroup) {
        AlertDialog(
            onDismissRequest = { showCreateGroup = false },
            title = { Text("Create favorite group") },
            text = {
                OutlinedTextField(
                    value = groupName,
                    onValueChange = { groupName = it },
                    label = { Text("Group name") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(
                    enabled = groupName.isNotBlank(),
                    onClick = {
                        vm.addGroup(groupName.trim(), currentParent)
                        groupName = ""
                        showCreateGroup = false
                    }
                ) { Text("Create") }
            },
            dismissButton = {
                TextButton(onClick = { showCreateGroup = false }) { Text("Cancel") }
            }
        )
    }

    if (showAppPicker) {
        AlertDialog(
            onDismissRequest = { showAppPicker = false },
            title = { Text("Choose an app") },
            text = {
                if (launchableApps.isEmpty()) {
                    Text("No launchable apps were found.")
                } else {
                    LazyColumn {
                        items(launchableApps, key = { it.packageName }) { app ->
                            TextButton(
                                modifier = Modifier.fillMaxWidth(),
                                onClick = {
                                    vm.addApp(app.label, app.packageName, currentParent, app.activityName)
                                    showAppPicker = false
                                }
                            ) { Text(app.label) }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showAppPicker = false }) { Text("Close") }
            }
        )
    }
}
