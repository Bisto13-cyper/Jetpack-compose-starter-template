package com.superapp.app.core.feature

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * FILE 1 - The "contract" every feature follows.
 *
 * Every feature in the app (APIs, Code Editor, WebView, Files...) is ONE class
 * that implements FeatureModule. The home screen (circles), the top side
 * button and the navigation all read from this contract, so none of them
 * need to change when you add a new feature.
 *
 * HOW TO ADD A NEW FEATURE (later, once AppFeatures.kt exists):
 *   1. Create a folder: features/<yourfeature>/
 *   2. Create a class there that implements FeatureModule
 *   3. Add ONE line to AppFeatures.kt:  YourFeature(),
 */
interface FeatureModule {

    /** Unique short name, used for navigation. Lowercase, no spaces. Example: "apis" */
    val id: String

    /** Name shown under the circle on the home screen. */
    val title: String

    /** What is drawn inside the circle. See FeatureIcon below. */
    val icon: FeatureIcon

    /**
     * Default glow/ring color of the circle.
     * The user can override it from the Settings dashboard later.
     */
    val defaultAccent: Color

    /**
     * The screen of the feature. Draw your UI here.
     * Call onBack() to return to the home screen.
     */
    @Composable
    fun Content(onBack: () -> Unit)
}

/**
 * What can appear inside a circle. Pick one:
 *  - Emoji  : simplest, no files needed. Example: FeatureIcon.Emoji("🌐")
 *  - Vector : a Compose vector icon.
 *  - Drawable: your own image from res/drawable. Example: FeatureIcon.Drawable(R.drawable.my_icon)
 *  - Custom : a path to an image the user picked in Settings (stored on the phone).
 */
sealed interface FeatureIcon {
    data class Emoji(val value: String) : FeatureIcon
    data class Vector(val image: ImageVector) : FeatureIcon
    data class Drawable(val resId: Int) : FeatureIcon
    data class Custom(val path: String) : FeatureIcon
}

/**
 * Holds all registered features, in the order they appear on the home screen.
 * Built once from the list in AppFeatures.kt.
 */
class FeatureRegistry(features: List<FeatureModule>) {

    init {
        val duplicates = features.groupBy { it.id }.filterValues { it.size > 1 }.keys
        require(duplicates.isEmpty()) { "Duplicate feature id(s): $duplicates" }
    }

    private val items: List<FeatureModule> = features.toList()

    /** All features, in display order. */
    fun all(): List<FeatureModule> = items

    /** Find a feature by id, or null if it doesn't exist. */
    fun find(id: String): FeatureModule? = items.firstOrNull { it.id == id }
}
