package com.superapp.app.features.favorites.ui

import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
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

    LazyColumn(modifier = Modifier.fillMaxSize()) {
        items(visible, key = { it.id }) { node ->
            Row(modifier = Modifier.clickable {
                when (node) {
                    is FavoriteNode.Group -> onOpenGroup(node.id)
                    is FavoriteNode.App -> {
                        val intent = context.packageManager.getLaunchIntentForPackage(node.packageName)
                        if (intent != null) context.startActivity(intent)
                    }
                }
            }.padding(16.dp)) {
                Column {
                    Text(node.name)
                    if (node is FavoriteNode.Group) Text("Group")
                }
            }
        }
    }
}
