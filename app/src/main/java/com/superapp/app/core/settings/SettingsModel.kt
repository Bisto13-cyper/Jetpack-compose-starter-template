package com.superapp.app.features.settings

import androidx.compose.runtime.Composable
import com.superapp.app.core.settings.SettingsRepository

/**
 * FILE 10 - The whole Settings menu as a tree.
 *
 * Every entry is a SettingsNode:
 *  - A node WITH children  -> opens a list of its children (like "Security").
 *  - A node WITHOUT children -> opens its own settings screen (like "App Lock").
 *  - A leaf with content = null shows a "Coming soon" page.
 *
 * Nodes can be nested as deep as you like
 * (Advanced > Developer Mode > Debug Console).
 *
 * HOW TO MAKE A SETTING REAL (when its screen file is delivered):
 *   Add one line to its node:
 *     content = { repo -> ColorsThemesScreen(repo) }
 *   The "Coming soon" tag disappears by itself.
 *
 * HOW TO ADD A NEW SETTING:
 *   Add a new SettingsNode(...) inside the right section below.
 *
 * HOW TO ADD A NEW SECTION:
 *   Add a new SettingsNode(...) with children = listOf(...) to settingsRoot.
 *
 * HOW TO CHANGE ORDER / NAMES / EMOJIS: edit the lines below.
 */
data class SettingsNode(
    /** Unique short name. */
    val id: String,

    /** Name shown in the list. */
    val title: String,

    /** Emoji shown at the start of the row. */
    val emoji: String,

    /** Small gray line under the title. */
    val subtitle: String = "",

    /** Sub-options. If not empty, tapping opens a list of them. */
    val children: List<SettingsNode> = emptyList(),

    /** Shown on the Coming soon page, usually what the setting is waiting for. */
    val note: String? = null,

    /** The screen of this setting. null = "Coming soon". */
    val content: (@Composable (SettingsRepository) -> Unit)? = null
)

val settingsRoot = SettingsNode(
    id = "settings",
    title = "Settings",
    emoji = "⚙️",
    children = listOf(

        // ---------------------------------------------------------------
        SettingsNode(
            id = "appearance", title = "Appearance", emoji = "🎨",
            subtitle = "Colors, backgrounds, glow, lines",
            children = listOf(
                SettingsNode(
                    id = "colors", title = "Colors & Themes", emoji = "🎨",
                    subtitle = "Any color for nodes, lines and background"
                ),
                SettingsNode(
                    id = "backgrounds", title = "Backgrounds", emoji = "🖼️",
                    subtitle = "A different background for each screen"
                ),
                SettingsNode(
                    id = "glow", title = "Glow", emoji = "✨",
                    subtitle = "Glow color and strength"
                ),
                SettingsNode(
                    id = "lines", title = "Connection Lines", emoji = "🕸️",
                    subtitle = "Straight, curved or pulsing lines"
                )
            )
        ),

        // ---------------------------------------------------------------
        SettingsNode(
            id = "canvas", title = "Canvas", emoji = "🕸️",
            subtitle = "How the home circles are arranged",
            children = listOf(
                SettingsNode(
                    id = "node_layout", title = "Node Layout", emoji = "🧩",
                    subtitle = "Ring, grid or free layout",
                    note = "Coming soon - needs the Canvas editor on the Home screen."
                ),
                SettingsNode(
                    id = "drag_drop", title = "Drag & Drop", emoji = "✋",
                    subtitle = "Move circles anywhere you like",
                    note = "Coming soon - needs the Canvas editor on the Home screen."
                ),
                SettingsNode(
                    id = "visibility", title = "Visibility", emoji = "👁️",
                    subtitle = "Show or hide circles",
                    note = "Coming soon - needs the Canvas editor on the Home screen."
                ),
                SettingsNode(
                    id = "layout_presets", title = "Layout Presets", emoji = "💾",
                    subtitle = "Save, apply and delete layouts",
                    note = "Coming soon - needs the Canvas editor on the Home screen."
                )
            )
        ),

        // ---------------------------------------------------------------
        SettingsNode(
            id = "features", title = "Features", emoji = "🧰",
            subtitle = "Apps and features on the home screen",
            children = listOf(
                SettingsNode(
                    id = "installed_apps", title = "Installed Apps", emoji = "📲",
                    subtitle = "Add any phone app as a circle",
                    note = "Coming soon - needs the Favorites / Apps feature."
                ),
                SettingsNode(
                    id = "feature_management", title = "Feature Management", emoji = "🧩",
                    subtitle = "Turn features on or off, rename, reorder"
                ),
                SettingsNode(
                    id = "webview_engine", title = "WebView Engine", emoji = "🌐",
                    subtitle = "In-app WebView or Chrome / Brave / Firefox tabs",
                    note = "Coming soon - needs the WebView feature."
                )
            )
        ),

        // ---------------------------------------------------------------
        SettingsNode(
            id = "storage", title = "Storage", emoji = "💽",
            subtitle = "Space, folders and backups",
            children = listOf(
                SettingsNode(
                    id = "storage_manager", title = "Storage Manager", emoji = "📦",
                    subtitle = "How much space each feature uses",
                    note = "Coming soon - needs the Files feature."
                ),
                SettingsNode(
                    id = "file_directory", title = "File Directory", emoji = "📁",
                    subtitle = "Where app files are saved",
                    note = "Coming soon - needs the Files feature."
                ),
                SettingsNode(
                    id = "backup_restore", title = "Backup & Restore", emoji = "🗄️",
                    subtitle = "Export or import everything as a .json file"
                )
            )
        ),

        // ---------------------------------------------------------------
        SettingsNode(
            id = "language", title = "Language", emoji = "🌍",
            subtitle = "Arabic, English, German",
            children = listOf(
                SettingsNode(
                    id = "language_rtl", title = "Language & RTL", emoji = "🔤",
                    subtitle = "Switch language and text direction",
                    note = "Coming soon - needs the translated texts (Arabic, English, German)."
                )
            )
        ),

        // ---------------------------------------------------------------
        SettingsNode(
            id = "security", title = "Security", emoji = "🔒",
            subtitle = "Locks and biometrics",
            children = listOf(
                SettingsNode(
                    id = "app_lock", title = "App Lock", emoji = "🔐",
                    subtitle = "Ask for a lock when the app opens",
                    note = "Coming soon - needs a lock screen."
                ),
                SettingsNode(
                    id = "node_lock", title = "Node Lock", emoji = "🛡️",
                    subtitle = "Protect chosen features (Code Editor, APIs...)",
                    note = "Coming soon - needs the biometric library."
                ),
                SettingsNode(
                    id = "biometrics", title = "Biometrics", emoji = "🫆",
                    subtitle = "Fingerprint / face unlock",
                    note = "Coming soon - needs the biometric library."
                )
            )
        ),

        // ---------------------------------------------------------------
        SettingsNode(
            id = "advanced", title = "Advanced", emoji = "🛠️",
            subtitle = "Developer tools",
            children = listOf(
                SettingsNode(
                    id = "developer_mode", title = "Developer Mode", emoji = "🧪",
                    subtitle = "Tools for checking how the app works",
                    children = listOf(
                        SettingsNode(
                            id = "system_overview", title = "System Overview", emoji = "🧭",
                            subtitle = "Modules, versions and app structure"
                        ),
                        SettingsNode(
                            id = "debug_console", title = "Debug Console", emoji = "🐞",
                            subtitle = "Live logs and errors",
                            note = "Coming soon - needs the app logger."
                        ),
                        SettingsNode(
                            id = "state_inspector", title = "State Inspector", emoji = "🔎",
                            subtitle = "See saved values and circle positions"
                        )
                    )
                )
            )
        )
    )
)
