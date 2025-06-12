# Jetpack startup Template
A modern Android starter template built with the latest Jetpack technologies to kickstart your Android app development with best practices out of the box.


## 🛠️ Tech Stack & Libraries

*   [Kotlin](https://kotlinlang.org/)
*   [Jetpack Compose (Alpha version for Material 3 expressive features)](https://developer.android.com/jetpack/compose)
*   [Hilt (for Dependency Injection)](https://developer.android.com/training/dependency-injection/hilt-android)
*   [Navigation Compose](https://developer.android.com/guide/navigation/navigation-3)
*   [AndroidX Libraries](https://developer.android.com/jetpack/androidx)

## 🚀 Getting Started

### Prerequisites

*   Android Studio (latest stable version).
*   JDK 11 or higher

### Installation

1.  Clone the repository:
   
2.  Open the project in Android Studio.
3.  Let Android Studio sync the Gradle files and download the necessary dependencies.
4.  Build and run the application on an emulator or a physical device.

📦 Features Included
*   Modular Navigation: Uses Navigation Compose to handle seamless screen transitions.
*   Dependency Injection: Hilt is pre-configured to provide dependencies easily and cleanly.
*   Network Layer Setup: Includes a ready-to-use network module for making API calls.
*   Material 3 Compose UI: Leverages the latest Material Design 3 components (Alpha).
*   Clean Architecture: Encourages separation of concerns and scalability.
*   AndroidX Support: Incorporates essential AndroidX libraries for modern app development.

## 🏗️ Project Structure

(Adjust this section if your actual structure differs significantly)
```
        
        src/
         ├─ data/          # Network and repository implementations
         ├─ domain/        # Domain-specific logic and models
         ├─ di/            # Dependency injection modules
         ├─ Presentation/            # Compose UI components and screens
         ├─ navigation/    # Navigation graph and routes
         └─ MainActivity.kt # Entry point
```
