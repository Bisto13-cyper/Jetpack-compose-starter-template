Android App (app module)

```text
├── src
│   ├── main
│   │   ├── java
│   │   │   └── com
│   │   │       └── superapp
│   │   │           └── app
│   │   │               ├── core
│   │   │               │   ├── canvas
│   │   │               │   │   └── CanvasStore.kt
│   │   │               │   ├── feature
│   │   │               │   │   └── FeatureModule.kt
│   │   │               │   ├── images
│   │   │               │   │   └── ImageStore.kt
│   │   │               │   ├── navigation
│   │   │               │   │   └── Navigator.kt
│   │   │               │   ├── settings
│   │   │               │   │   ├── FavoritesStore.kt
│   │   │               │   │   ├── SettingsModel.kt
│   │   │               │   │   └── SettingsRepository.kt
│   │   │               │   ├── theme
│   │   │               │   │   ├── AppTheme.kt
│   │   │               │   │   └── ThemePresets.kt
│   │   │               │   └── ui
│   │   │               │       ├── ColorPicker.kt
│   │   │               │       ├── FeatureCircle.kt
│   │   │               │       └── SideMenuButton.kt
│   │   │               ├── features
│   │   │               │   ├── home
│   │   │               │   │   └── HomeScreen.kt
│   │   │               │   └── settings
│   │   │               │       ├── appearance
│   │   │               │       │   ├── BackgroundsPage.kt
│   │   │               │       │   ├── ColorsPage.kt
│   │   │               │       │   ├── FavoritesPage.kt
│   │   │               │       │   ├── GlowPage.kt
│   │   │               │       │   └── LinesPage.kt
│   │   │               │       ├── canvas
│   │   │               │       │   ├── DragDropPage.kt
│   │   │               │       │   ├── NodeLayoutPage.kt
│   │   │               │       │   └── VisibilityPage.kt
│   │   │               │       ├── SettingsMenu.kt
│   │   │               │       └── SettingsScreen.kt
│   │   │               ├── AppFeatures.kt
│   │   │               └── MainActivity.kt
│   │   ├── res
│   │   │   ├── drawable
│   │   │   │   ├── app_icon.png
│   │   │   │   ├── ic_launcher_background.xml
│   │   │   │   └── ic_launcher_foreground.xml
│   │   │   ├── mipmap-anydpi-v26
│   │   │   │   ├── ic_launcher.xml
│   │   │   │   └── ic_launcher_round.xml
│   │   │   ├── mipmap-hdpi
│   │   │   │   ├── ic_launcher.webp
│   │   │   │   └── ic_launcher_round.webp
│   │   │   ├── mipmap-mdpi
│   │   │   │   ├── ic_launcher.webp
│   │   │   │   └── ic_launcher_round.webp
│   │   │   ├── mipmap-xhdpi
│   │   │   │   ├── ic_launcher.webp
│   │   │   │   └── ic_launcher_round.webp
│   │   │   ├── mipmap-xxhdpi
│   │   │   │   ├── ic_launcher.webp
│   │   │   │   └── ic_launcher_round.webp
│   │   │   ├── mipmap-xxxhdpi
│   │   │   │   ├── ic_launcher.webp
│   │   │   │   └── ic_launcher_round.webp
│   │   │   ├── values
│   │   │   │   ├── colors.xml
│   │   │   │   ├── strings.xml
│   │   │   │   └── themes.xml
│   │   │   └── xml
│   │   │       ├── backup_rules.xml
│   │   │       └── data_extraction_rules.xml
│   │   └── AndroidManifest.xml
│   └── test
│       └── java
│           └── com
│               └── startup
│                   └── template
│                       └── ExampleUnitTest.kt
├── .gitignore
├── build.gradle.kts
├── FEATURE_PROGRESS.md
├── proguard-rules.pro
├── PROJECT_STATUS.md
└── PROJECT_TREE.md
```

### Structure rules

- `core/` contains reusable infrastructure, models, storage, theme and UI primitives.
- `features/` contains user-facing feature screens.
- `features/settings/appearance/` contains the Appearance pages.
- `core/settings/` contains settings persistence and settings models.
- `AppFeatures.kt` is the single registration list for home-screen features.
