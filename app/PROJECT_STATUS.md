# Project Status

Last reviewed: 2026-10-06

## Working rules

- Fix/review existing code without changing intended behavior unnecessarily.
- Reorganize only where it genuinely improves the project structure.
- Do not add unrelated features just because a file or checklist mentions them.
- Keep the project tree updated after structural changes.
- Appearance is the current completed Settings area; other Settings areas remain in progress.
- Build testing is intentionally left to the developer.

## Changes in this review

1. Corrected the physical/package mismatch for `GlowPage.kt` and `LinesPage.kt`.
   Both now live under `features/settings/appearance/`, matching their package.
2. Removed duplicate entries from the `settingsPages` registry.
3. Added `core/settings/SettingsModel.kt` for `SettingsNode` and `SettingsEnv`, keeping the Settings model separate from the menu registry.
4. Updated Settings page imports to use the new Settings model location.
5. Made the Appearance background description match the implemented global-background behavior instead of promising per-screen backgrounds that are not implemented yet.
6. Improved Favorites' `Active` detection so it checks the complete saved Appearance state (colors, circle scale, glow, connection lines, photo presence, and circle accent overrides), while correctly ignoring the internal copied photo path.
7. Generated `PROJECT_TREE.md` and `FEATURE_PROGRESS.md`.

## Review notes

- `SettingsRepository` persists the current Appearance settings correctly, including glow and connection-line settings.
- `FavoritesStore` persists the full Appearance state and keeps private photo copies.
- `FeatureCircle` consumes Glow settings, and `HomeScreen` consumes Connection Lines settings.
- No build was run here; the uploaded archive does not include the full Gradle wrapper/project files needed for a reliable build from this archive alone.
- `applicationId = "com.startup.template"` and `namespace = "com.superapp.app"` are both present in `app/build.gradle.kts`. This was not changed because it is an app identity decision, not an Appearance/organization fix.

## Current priority

Finish Settings first. Appearance is complete for the currently implemented scope. Canvas is partially complete. The remaining Settings categories and real app features are intentionally left for later.
