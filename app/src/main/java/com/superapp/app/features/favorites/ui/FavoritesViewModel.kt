package com.superapp.app.features.favorites.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.superapp.app.features.favorites.data.FavoriteNode
import com.superapp.app.features.favorites.data.FavoritesRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class FavoritesViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = FavoritesRepository(application)
    private val _nodes = MutableStateFlow(repository.load())
    val nodes: StateFlow<List<FavoriteNode>> = _nodes.asStateFlow()

    fun addGroup(name: String, parentId: String? = null) {
        val next = _nodes.value + repository.createGroup(name, parentId)
        repository.save(next)
        _nodes.value = next
    }

    fun addApp(name: String, packageName: String, parentId: String? = null, activityName: String? = null) {
        val next = _nodes.value + repository.createApp(name, packageName, parentId, activityName)
        repository.save(next)
        _nodes.value = next
    }

    fun delete(id: String) {
        val ids = buildSet {
            fun collect(parent: String) {
                add(parent)
                _nodes.value.filter { it.parentId == parent }.forEach { collect(it.id) }
            }
            collect(id)
        }
        val next = _nodes.value.filterNot { it.id in ids }
        repository.save(next)
        _nodes.value = next
    }
}
