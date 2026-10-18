package com.superapp.app.features.favorites.ui

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import android.widget.ImageView
import androidx.lifecycle.viewmodel.compose.viewModel
import com.superapp.app.features.favorites.data.FavoriteNode

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

    Column(
        modifier = Modifier
            .fillMaxSize()
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
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text("⭐", fontSize = 42.sp)
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = "No favorites yet",
                        style = MaterialTheme.typography.titleMedium,
                        color = colors.onBackground
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "Your favorite apps and groups will appear here when added.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.onSurfaceVariant
                    )
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
                                    is FavoriteNode.Group -> onOpenGroup(node.id)
                                    is FavoriteNode.App -> {
                                        val intent = context.packageManager.getLaunchIntentForPackage(node.packageName)
                                        if (intent != null) context.startActivity(intent)
                                    }
                                }
                            }
                            .padding(vertical = 12.dp, horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        when (node) {
                            is FavoriteNode.Group -> Text("📁", modifier = Modifier.padding(end = 12.dp), fontSize = 24.sp)
                            is FavoriteNode.App -> AndroidView(
                                modifier = Modifier.size(40.dp).padding(end = 8.dp),
                                factory = { viewContext ->
                                    ImageView(viewContext).apply {
                                        scaleType = ImageView.ScaleType.FIT_CENTER
                                    }
                                },
                                update = { image ->
                                    image.setImageDrawable(runCatching {
                                        context.packageManager.getApplicationIcon(node.packageName)
                                    }.getOrNull())
                                }
                            )
                        }
                        Column {
                            Text(node.name, color = colors.onBackground, style = MaterialTheme.typography.bodyLarge)
                            if (node is FavoriteNode.Group) {
                                Text("Group", color = colors.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                    Surface(color = colors.outlineVariant.copy(alpha = 0.35f), modifier = Modifier.fillMaxWidth().height(1.dp)) {}
                }
            }
        }
    }
}
