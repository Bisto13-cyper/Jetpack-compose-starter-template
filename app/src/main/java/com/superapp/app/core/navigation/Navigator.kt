package com.superapp.app.core.navigation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * FILE 4 - Navigation without any extra library.
 *
 * The app is always showing exactly ONE "route":
 *  - Routes.HOME     : the circles screen
 *  - Routes.SETTINGS : the settings dashboard
 *  - any feature id  : that feature's screen (example: "apis")
 *
 * To open something from any screen:
 *   navigator.go("apis")
 *   navigator.home()
 *   navigator.settings()
 *
 * Because a feature's route is simply its id, adding a new feature
 * needs NO change in this file.
 */
object Routes {
    const val HOME = "home"
    const val SETTINGS = "settings"
}

class Navigator(start: String = Routes.HOME) {

    /** The route currently on screen. Compose redraws when this changes. */
    var current: String by mutableStateOf(start)
        private set

    val isHome: Boolean
        get() = current == Routes.HOME

    fun go(route: String) {
        current = route
    }

    fun home() = go(Routes.HOME)

    fun settings() = go(Routes.SETTINGS)
}
