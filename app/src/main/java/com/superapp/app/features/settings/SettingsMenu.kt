package com.superapp.app.features.settings

import androidx.compose.runtime.Composable
import com.superapp.app.core.feature.FeatureRegistry
import com.superapp.app.core.settings.SettingsRepository
import com.superapp.app.features.settings.appearance.ColorsPage
import com.superapp.app.features.settings.appearance.FavoritesPage
import com.superapp.app.features.settings.appearance.GlowPage
import com.superapp.app.features.settings.appearance.LinesPage

/**
 * SETTINGS FILE 1 - The whole Settings menu, as data.
 *
 * Settings is a tree:  Category -> (sub-category) -> Page.
 * Tapping a row opens its children. A row with no children is a PAGE.
 *
 * HOW TO ADD A SETTINGS ITEM:
 *   1. Add one SettingsNode line to settingsTree below.
 *   2. Write its page (a @Composable function that takes SettingsEnv).
 *   3. Register the page with one line in settingsPages (bottom of this file).
 *
 * Until step 3 is done, the row shows "Coming soon" automatically and its
 * page shows the note. When the page is registered, the tag disappears.
 *
 * IDs must be unique. Pattern: "category.page", for example "security.applock".
 */
class SettingsNode(
    val id: String,
    val title: String,
    val emoji: String,
    /** Sub-items. If empty, this node is a page. */
    val children: List<SettingsNode> = emptyList(),
    /** What this page will do. Shown on the "Coming soon" page. */
    val note: String = ""
)

/**
 * What every settings page receives:
 *  - repository : read and save settings
 *  - registry   : the list of all features (for feature management, locks...)
 */
class SettingsEnv(
    val repository: SettingsRepository,
    val registry: FeatureRegistry
)

val settingsTree: List<SettingsNode> = listOf(

    SettingsNode(
        id = "appearance", title = "Appearance", emoji = "🎨",
        children = listOf(
            SettingsNode(
                "appearance.colors", "Colors & Themes", "🌈",
                note = "Pick any color for the background, panels, accent and text, and for each circle."
            ),
            SettingsNode(
                "appearance.backgrounds", "Backgrounds", "🖼️",
                note = "A separate background image or color for each screen (Home, WebView, APIs, Code Editor...)."
            ),
            SettingsNode(
                "appearance.glow", "Glow", "✨",
                note = "Glow strength and size around the circles."
            ),
            SettingsNode(
                "appearance.lines", "Connection Lines", "🕸️",
                note = "Line color, width and style: straight, curved, or animated pulsing."
            ),
            SettingsNode(
                "appearance.favorite", "Favorites", "🌟",
                note = "Your favorite themes and colors."
            )	    
        )
    ),

    SettingsNode(
        id = "canvas", title = "Canvas", emoji = "🕸️",
        children = listOf(
            SettingsNode(
                "canvas.layout", "Node Layout", "🧭",
                note = "Circle size, spacing and ring arrangement on the home screen."
            ),
            SettingsNode(
                "canvas.drag", "Drag & Drop", "✋",
                note = "Drag circles anywhere on the home canvas and keep their positions."
            ),
            SettingsNode(
                "canvas.visibility", "Visibility", "👁️",
                note = "Show or hide any circle."
            ),
            SettingsNode(
                "canvas.presets", "Layout Presets", "💾",
                note = "Save, preview, apply and delete layouts as JSON presets."
            )
        )
    ),

    SettingsNode(
        id = "features", title = "Features", emoji = "🧩",
        children = listOf(
            SettingsNode(
                "features.apps", "Installed Apps", "📱",
                note = "Add any installed Android app as a circle on the home screen. Needs a new circle type on the home screen."
            ),
            SettingsNode(
                "features.manage", "Feature Management", "🔧",
                note = "Turn features on or off and change their order."
            ),
            SettingsNode(
                "features.webview", "WebView Engine", "🌐",
                note = "Choose In-App WebView or Custom Tabs (Chrome, Brave, Firefox). Needs the WebView feature."
            )
        )
    ),

    SettingsNode(
        id = "storage", title = "Storage", emoji = "🗄️",
        children = listOf(
            SettingsNode(
                "storage.manager", "Storage Manager", "📊",
                note = "See how much space every feature takes and how many files it has."
            ),
            SettingsNode(
                "storage.directory", "File Directory", "📂",
                note = "Change the folder used for files, assets and project data."
            ),
            SettingsNode(
                "storage.backup", "Backup & Restore", "☁️",
                note = "Export and import all settings and states as one .json file."
            )
        )
    ),

    SettingsNode(
        id = "language", title = "Language", emoji = "🌍",
        children = listOf(
            SettingsNode(
                "language.language", "Language & RTL", "🔤",
                note = "Arabic, English or German, with automatic right-to-left layout."
            )
        )
    ),

    SettingsNode(
        id = "security", title = "Security", emoji = "🔒",
        children = listOf(
            SettingsNode(
                "security.applock", "App Lock", "🔑",
                note = "Protect the whole app with a PIN."
            ),
            SettingsNode(
                "security.nodelock", "Node Lock", "🛡️",
                note = "Lock chosen circles such as Code Editor or API credentials."
            ),
            SettingsNode(
                "security.biometrics", "Biometrics", "👆",
                note = "Use fingerprint or face unlock for the locks above."
            )
        )
    ),

    SettingsNode(
        id = "advanced", title = "Advanced", emoji = "🛠️",
        children = listOf(
            SettingsNode(
                id = "advanced.dev", title = "Developer Mode", emoji = "💻",
                children = listOf(
                    SettingsNode(
                        "advanced.dev.overview", "System Overview", "🧬",
                        note = "App structure, active modules, version and build info."
                    ),
                    SettingsNode(
                        "advanced.dev.console", "Debug Console", "🐞",
                        note = "Live logs, events and errors. Network requests need the APIs feature."
                    ),
                    SettingsNode(
                        "advanced.dev.state", "State Inspector", "🔍",
                        note = "Inspect saved settings, circle positions and stored values."
                    )
                )
            )
        )
    )
)

/**
 * THE PAGE REGISTRY - connects a settings item to its page.
 *
 * Each time a new settings page file is delivered, ONE line is added here:
 *     "appearance.colors" to { env -> ColorsPage(env) },
 *
 * Any item that is not listed here shows "Coming soon".
 * Pages should scroll by themselves (use Modifier.verticalScroll).
 */

val settingsPages: Map<String, @Composable (SettingsEnv) -> Unit> = mapOf(
    page("appearance.colors") { env -> ColorsPage(env) },
    page("appearance.backgrounds") { env -> BackgroundsPage(env) },
    page("appearance.favorite") { env -> FavoritesPage(env) },
    page("appearance.glow") {env -> GlowPage(env)},
    page("appearance.lines") {env -> LinesPage(env)},
)
)

