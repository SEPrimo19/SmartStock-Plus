# SmartStock+ Project File Explanation

This document explains the purpose of each tracked project file and how it is used in SmartStock+.

## 1. Root Project Files

| File | Purpose / Usage |
| --- | --- |
| `settings.gradle.kts` | Registers the Gradle modules in the project. The Android app module is included here. |
| `build.gradle.kts` | Root Gradle build script. Defines top-level plugin and build behavior shared by the project. |
| `gradle.properties` | Project-wide Gradle settings and build flags. |
| `local.properties` | Machine-specific local SDK configuration used by Android Studio and Gradle. Not part of app logic. |
| `lint.xml` | Android lint configuration for static analysis rules. |
| `gradlew` | Unix/Linux/Mac Gradle wrapper script. Used to run Gradle without requiring a global install. |
| `gradlew.bat` | Windows Gradle wrapper script. Used to run Gradle on this machine. |
| `Project-RoadMap.txt` | Planning/reference document describing project goals and development direction. |
| `App-Project-Midterm-Progress-Report.pdf` | Midterm documentation/report used for academic progress reporting. |

## 2. Gradle Wrapper Files

| File | Purpose / Usage |
| --- | --- |
| `gradle/libs.versions.toml` | Central version catalog for libraries and plugins used by the app. |
| `gradle/wrapper/gradle-wrapper.properties` | Defines which Gradle version the wrapper downloads and uses. |
| `gradle/wrapper/gradle-wrapper.jar` | Gradle wrapper bootstrap binary. Required for `gradlew` and `gradlew.bat`. |

## 3. App Module Configuration and Documentation

| File | Purpose / Usage |
| --- | --- |
| `app/build.gradle.kts` | App module build configuration. Declares Android settings, Kotlin settings, dependencies, Compose, Room, ML Kit, and packaging behavior. |
| `app/proguard-rules.pro` | ProGuard/R8 rules for code shrinking and release obfuscation. |
| `app/FullRoadMap` | Internal roadmap/reference file for planned app features and scope. |
| `app/MCO1_FEATURE_MAPPING.md` | Maps implemented project code/features to MCO 1 requirements. |
| `app/MCO2_CLOUD_PREPARATION.md` | Documents the cloud-sync preparation layer and future MCO 2 cloud direction. |

## 4. Android Manifest and XML Configuration

| File | Purpose / Usage |
| --- | --- |
| `app/src/main/AndroidManifest.xml` | Declares the application, main activity, app theme, permissions, and launcher configuration. |
| `app/src/main/res/xml/data_extraction_rules.xml` | Android backup/data extraction policy file. |
| `app/src/main/res/xml/backup_rules.xml` | Backup behavior rules for app data. |

## 5. Main Application Entry Files

| File | Purpose / Usage |
| --- | --- |
| `app/src/main/java/com/example/smartstock/SmartStockApp.kt` | Application class. Initializes app-level dependencies and startup behavior such as seeding local users or app-level setup. |
| `app/src/main/java/com/example/smartstock/MainActivity.kt` | Android activity entry point. Hosts the Compose UI and starts the app flow. |

## 6. Core Layer

### 6.1 Network / Connectivity

| File | Purpose / Usage |
| --- | --- |
| `app/src/main/java/com/example/smartstock/core/network/ConnectivityObserver.kt` | Interface/contract for monitoring network connectivity state. |
| `app/src/main/java/com/example/smartstock/core/network/NetworkConnectivityObserver.kt` | Concrete connectivity observer implementation used to detect online/offline status in the app. |

### 6.2 Sync

| File | Purpose / Usage |
| --- | --- |
| `app/src/main/java/com/example/smartstock/core/sync/CloudSyncDataSource.kt` | Sync abstraction layer for future cloud integration. Used to keep the app architecture ready for MCO 2 cloud sync without changing the offline-first flow yet. |

## 7. Data Layer

### 7.1 Database

| File | Purpose / Usage |
| --- | --- |
| `app/src/main/java/com/example/smartstock/data/AppDatabase.kt` | Room database definition. Registers entities, DAO access, and database migrations. |

### 7.2 DAO

| File | Purpose / Usage |
| --- | --- |
| `app/src/main/java/com/example/smartstock/data/dao/InventoryDao.kt` | DAO for local database operations such as insert, update, delete, query, filter, sort, and item/history/user access. |

### 7.3 Repository

| File | Purpose / Usage |
| --- | --- |
| `app/src/main/java/com/example/smartstock/data/repository/InventoryRepository.kt` | Main repository that sits between ViewModels and Room. Centralizes data access and prepares future cloud sync hooks. |

### 7.4 Mock / Seed Data

| File | Purpose / Usage |
| --- | --- |
| `app/src/main/java/com/example/smartstock/data/MockData.kt` | Local seed/mock inventory data used for initial content or testing/demo support. |

### 7.5 Entities

| File | Purpose / Usage |
| --- | --- |
| `app/src/main/java/com/example/smartstock/data/entity/InventoryItem.kt` | Main inventory entity. Stores asset code, item name, category, quantity, in-use quantity, status, condition, location, description, and timestamps. |
| `app/src/main/java/com/example/smartstock/data/entity/ItemHistory.kt` | History log entity that records item actions and activity timestamps. |
| `app/src/main/java/com/example/smartstock/data/entity/CategoryEntity.kt` | Local category entity used for inventory classification. |
| `app/src/main/java/com/example/smartstock/data/entity/AssetStatusEntity.kt` | Local status entity used for inventory status values. |
| `app/src/main/java/com/example/smartstock/data/entity/LocalUser.kt` | Local user entity for admin/staff role records in offline mode. |

## 8. UI State and ViewModels

| File | Purpose / Usage |
| --- | --- |
| `app/src/main/java/com/example/smartstock/ui/MainViewModel.kt` | Main inventory ViewModel. Handles CRUD, search/filter/sort state, item selection, quantity usage/return logic, role-based permissions, local users, and save validation. |
| `app/src/main/java/com/example/smartstock/ui/DashboardViewModel.kt` | Dashboard/home ViewModel. Provides summary counts, recent items, online/offline status, and dashboard-facing item flows. |
| `app/src/main/java/com/example/smartstock/ui/UserRole.kt` | Defines the user roles used in the project, such as Admin and Staff, together with permission logic. |
| `app/src/main/java/com/example/smartstock/ui/MainScreen.kt` | Main screen shell that manages global screen layout, navigation rail behavior, and two-pane immersive system bar handling. |

## 9. Adaptive UI Layer

| File | Purpose / Usage |
| --- | --- |
| `app/src/main/java/com/example/smartstock/ui/adaptive/WindowSize.kt` | Calculates window size information used to decide when the app should behave in single-pane or two-pane mode. |
| `app/src/main/java/com/example/smartstock/ui/adaptive/AdaptiveScaffold.kt` | Shared adaptive layout scaffolding used across screens, including top bar behavior, pane structure, and reusable adaptive layout helpers. |

## 10. Reusable UI Components

| File | Purpose / Usage |
| --- | --- |
| `app/src/main/java/com/example/smartstock/ui/components/CommonComponents.kt` | Shared Compose UI components used across the app, such as status chips, inventory cards, empty state cards, connectivity badge, and dialog header components. |

## 11. Navigation

| File | Purpose / Usage |
| --- | --- |
| `app/src/main/java/com/example/smartstock/ui/navigation/Screen.kt` | Declares the navigation routes/screen identifiers used throughout the app. |
| `app/src/main/java/com/example/smartstock/ui/navigation/NavGraph.kt` | Compose navigation graph that wires routes to the actual screens and passes shared dependencies. |

## 12. Screen Files

| File | Purpose / Usage |
| --- | --- |
| `app/src/main/java/com/example/smartstock/ui/screens/SplashScreen.kt` | App startup splash screen with the SmartStock+ logo and launch animation. |
| `app/src/main/java/com/example/smartstock/ui/screens/DashboardScreen.kt` | Home/dashboard screen. Shows summary cards, quick actions, recent items, and home barcode scanning entry flow. |
| `app/src/main/java/com/example/smartstock/ui/screens/InventoryListScreen.kt` | Inventory list screen. Handles search, filters, sorting, inventory cards, two-pane detail behavior, and swipe-to-delete flow. |
| `app/src/main/java/com/example/smartstock/ui/screens/AddEditItemScreen.kt` | Add/Edit item screen and modal dialog. Used to create new items or update existing inventory records. |
| `app/src/main/java/com/example/smartstock/ui/screens/ItemDetailScreen.kt` | Item detail screen/dialog. Shows full item information and actions like edit, delete, use, and return. |
| `app/src/main/java/com/example/smartstock/ui/screens/HistoryScreen.kt` | Inventory activity/history screen. Displays logged item actions in a readable timeline/list format. |
| `app/src/main/java/com/example/smartstock/ui/screens/CameraScanScreen.kt` | Dedicated scan screen for barcode/code scanning. Resolves existing items and supports scan-to-create flow subject to role restrictions. |
| `app/src/main/java/com/example/smartstock/ui/screens/ProfileScreen.kt` | Profile/settings screen. Handles role switching, local user management, app preferences, and account/session UI. |

## 13. Theme Files

| File | Purpose / Usage |
| --- | --- |
| `app/src/main/java/com/example/smartstock/ui/theme/Color.kt` | Defines the app color palette for light/dark mode and custom status/accent colors. |
| `app/src/main/java/com/example/smartstock/ui/theme/Theme.kt` | Builds the Material 3 theme and color schemes used throughout the app. |
| `app/src/main/java/com/example/smartstock/ui/theme/Type.kt` | Defines typography settings used by the Compose theme. |

## 14. Resource Files

### 14.1 Strings and Theme XML

| File | Purpose / Usage |
| --- | --- |
| `app/src/main/res/values/strings.xml` | Central string resources for the application name and other text values that belong in Android resources. |
| `app/src/main/res/values/themes.xml` | XML theme bridge for the Android app theme used by the activity and launcher entry. |

### 14.2 App Logo and Launcher Assets

| File | Purpose / Usage |
| --- | --- |
| `app/src/main/res/drawable/smartstock_logo.png` | Main SmartStock+ logo asset used in splash and launcher foreground design. |
| `app/src/main/res/drawable/ic_launcher_foreground.xml` | Adaptive launcher foreground definition using the app logo. |
| `app/src/main/res/drawable/ic_launcher_background.xml` | Adaptive launcher background definition. |
| `app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml` | Adaptive launcher icon definition for Android 8.0+. |
| `app/src/main/res/mipmap-anydpi-v26/ic_launcher_round.xml` | Round adaptive launcher icon definition for Android 8.0+. |
| `app/src/main/res/mipmap-mdpi/ic_launcher.webp` | Raster launcher icon for mdpi devices. |
| `app/src/main/res/mipmap-mdpi/ic_launcher_round.webp` | Raster round launcher icon for mdpi devices. |
| `app/src/main/res/mipmap-hdpi/ic_launcher.webp` | Raster launcher icon for hdpi devices. |
| `app/src/main/res/mipmap-hdpi/ic_launcher_round.webp` | Raster round launcher icon for hdpi devices. |
| `app/src/main/res/mipmap-xhdpi/ic_launcher.webp` | Raster launcher icon for xhdpi devices. |
| `app/src/main/res/mipmap-xhdpi/ic_launcher_round.webp` | Raster round launcher icon for xhdpi devices. |
| `app/src/main/res/mipmap-xxhdpi/ic_launcher.webp` | Raster launcher icon for xxhdpi devices. |
| `app/src/main/res/mipmap-xxhdpi/ic_launcher_round.webp` | Raster round launcher icon for xxhdpi devices. |
| `app/src/main/res/mipmap-xxxhdpi/ic_launcher.webp` | Raster launcher icon for xxxhdpi devices. |
| `app/src/main/res/mipmap-xxxhdpi/ic_launcher_round.webp` | Raster round launcher icon for xxxhdpi devices. |

## 15. Test Files

| File | Purpose / Usage |
| --- | --- |
| `app/src/test/java/com/example/smartstock/ExampleUnitTest.kt` | Basic local unit test placeholder. |
| `app/src/androidTest/java/com/example/smartstock/ExampleInstrumentedTest.kt` | Basic instrumented Android test placeholder. |

## 16. How These Files Work Together

The main runtime flow is:

`MainActivity -> MainScreen -> NavGraph -> Screen -> ViewModel -> Repository -> DAO -> Room Database`

Supporting systems also connect into that flow:

- `ConnectivityObserver` updates online/offline UI state.
- `CameraScanScreen` and Home scan use ML Kit code scanning.
- `CloudSyncDataSource` is the placeholder extension point for future cloud sync.
- `Theme`, `CommonComponents`, and adaptive layout files keep the UI consistent across phone and two-pane layouts.

## 17. Suggested Use of This Document

Use this file when you need to:

- explain the project structure during presentation
- onboard a group member to the codebase
- connect a file to a requirement or feature
- identify where to edit a specific behavior in the app
