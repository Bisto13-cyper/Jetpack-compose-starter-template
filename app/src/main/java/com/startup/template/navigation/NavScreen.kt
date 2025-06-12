package com.startup.template.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

sealed class NavScreen {

    @Serializable
    data object HomeRoute : NavKey

    @Serializable
    data object DetailsRoute : NavKey
}

