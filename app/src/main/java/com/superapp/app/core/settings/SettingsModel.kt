package com.superapp.app.core.settings

import com.superapp.app.core.feature.FeatureRegistry

/**
 * Settings models shared by the Settings UI.
 *
 * SettingsNode describes the Settings navigation tree:
 * category -> sub-category -> page.
 *
 * SettingsEnv is the small dependency bundle passed to settings pages.
 */
class SettingsNode(
    val id: String,
    val title: String,
    val emoji: String,
    val children: List<SettingsNode> = emptyList(),
    val note: String = ""
)

class SettingsEnv(
    val repository: SettingsRepository,
    val registry: FeatureRegistry
)
