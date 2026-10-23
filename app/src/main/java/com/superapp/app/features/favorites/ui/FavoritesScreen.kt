package com.superapp.app.features.favorites.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.superapp.app.features.favorites.data.FavoriteNode
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.core.graphics.drawable.toBitmap


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FavoritesScreen(
    parentId: String? = null,
    onOpenGroup: (String) -> Unit = {}
) {
    val vm: FavoritesViewModel = viewModel()
    val nodes by vm.nodes.collectAsState()
    val context = LocalContext.current
    val visible = nodes.filter { it.parentId == parentId }
    val colors = MaterialTheme.colorScheme

    var showAddDialog by remember { mutableStateOf(false) }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddDialog = true },
                containerColor = colors.primary
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add favorite")
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Text(
                text = if (parentId == null) "Favorites" else "Favorite group",
                style = MaterialTheme.typography.headlineSmall,
                color = colors.onBackground
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "${visible.size} ${if (visible.size == 1) "item" else "items"}",
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant
            )
            Spacer(Modifier.height(12.dp))

            if (visible.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("⭐", fontSize = 42.sp)
                        Spacer(Modifier.height(12.dp))
                        Text("No favorites yet", style = MaterialTheme.typography.titleMedium, color = colors.onBackground)
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "اضغط على + لإضافة تطبيق أو مجموعة",
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.onSurfaceVariant
                        )
                    }
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(visible, key = { it.id }) { node ->
                        FavoriteRow(node = node, context = context, onOpenGroup = onOpenGroup)
                        Surface(
                            color = colors.outlineVariant.copy(alpha = 0.35f),
                            modifier = Modifier.fillMaxWidth().height(1.dp)
                        ) {}
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        AddFavoriteDialog(
            onDismiss = { showAddDialog = false },
            onAddApp = { name, pkg ->
                vm.addApp(name, pkg, parentId)
                showAddDialog = false
            },
            onAddGroup = { name ->
                vm.addGroup(name, parentId)
                showAddDialog = false
            }
        )
    }
}
@Composable
private fun FavoriteRow(
    node: FavoriteNode,
    context: android.content.Context,
    onOpenGroup: (String) -> Unit
) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                when (node) {
                    is FavoriteNode.Group -> onOpenGroup(node.id)
                    is FavoriteNode.App -> {
                        val intent = context.packageManager
                            .getLaunchIntentForPackage(node.packageName)
                        if (intent != null) context.startActivity(intent)
                    }
                }
            }
            .padding(vertical = 12.dp, horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        when (node) {
            is FavoriteNode.Group -> Text("📁", modifier = Modifier.padding(end = 12.dp), fontSize = 24.sp)
            is FavoriteNode.App -> AppIcon(packageName = node.packageName, modifier = Modifier.size(40.dp).padding(end = 8.dp))
        }
        Column {
            Text(node.name, color = colors.onBackground, style = MaterialTheme.typography.bodyLarge)
            if (node is FavoriteNode.Group) {
                Text("Group", color = colors.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
@Composable
private fun AddFavoriteDialog(
    onDismiss: () -> Unit,
    onAddApp: (name: String, packageName: String) -> Unit,
    onAddGroup: (name: String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var packageName by remember { mutableStateOf("") }
    var isGroup by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isGroup) "إضافة مجموعة" else "إضافة تطبيق") },
        text = {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = !isGroup, onClick = { isGroup = false })
                    Text("تطبيق")
                    Spacer(Modifier.width(12.dp))
                    RadioButton(selected = isGroup, onClick = { isGroup = true })
                    Text("مجموعة")
                }
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("الاسم") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                if (!isGroup) {
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = packageName,
                        onValueChange = { packageName = it },
                        label = { Text("Package name (مثال: com.whatsapp)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank() && (isGroup || packageName.isNotBlank()),
                onClick = {
                    if (isGroup) onAddGroup(name.trim())
                    else onAddApp(name.trim(), packageName.trim())
                }
            ) { Text("إضافة") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } }
    )
}
@Composable
fun AppIcon(packageName: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val drawable = remember(packageName) {
        runCatching { context.packageManager.getApplicationIcon(packageName) }.getOrNull()
    }
    val bitmap = remember(drawable) {
        drawable?.let {
            runCatching { it.toBitmap(width = 96, height = 96) }.getOrNull()
        }
    }

    if (bitmap != null) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = packageName,
            modifier = modifier,
            contentScale = ContentScale.Fit
        )
    } else {
        // fallback: مربع رمادي فيه أول حرف من الباكدج
        Box(
            modifier = modifier
                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = packageName.firstOrNull()?.uppercase() ?: "?",
                style = MaterialTheme.typography.titleMedium
            )
        }
    }
}
