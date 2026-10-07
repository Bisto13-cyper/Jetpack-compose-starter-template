package com.superapp.app.features.favorites

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.superapp.app.core.feature.FeatureIcon
import com.superapp.app.core.feature.FeatureModule
import com.superapp.app.features.favorites.ui.FavoritesScreen

class FavoritesFeature : FeatureModule {
    override val id = "favorites"
    override val title = "Favorites"
    override val icon = FeatureIcon.Emoji("⭐")
    override val defaultAccent = Color(0xFFFB7185)

    @Composable
    override fun Content(onBack: () -> Unit) {
        BackHandler(onBack = onBack)
        FavoritesScreen()
    }
}
