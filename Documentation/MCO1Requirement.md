# SmartStock+ MCO 1 Requirements Compliance Report

**Course:** Mobile Programming 2
**Assessment:** Midterm Project Progress Check (MCO 1)
**Date:** March 24, 2026
**Group:** Team 2

---

## I. MCO 1 Objective

> Design and implement the foundational structure of an offline-capable inventory system using clean architecture, Room-based local persistence, and responsive Jetpack Compose UI.

---

## II. Functional Requirements Compliance

### A. Application Architecture

| Requirement | Status | Implementation Details |
|---|---|---|
| MVVM architecture | **Done** | `InventoryViewModel` and `DashboardViewModel` extend `androidx.lifecycle.ViewModel`; all UI state is managed through `StateFlow` and observed by Compose screens via `collectAsStateWithLifecycle()`. |
| Repository pattern | **Done** | `InventoryRepository.kt` serves as the single abstraction layer between the DAO and ViewModels. All data operations (CRUD, search, filter) are routed through the repository. |
| ViewModel for UI logic | **Done** | Business logic (validation, role checks, sorting, filtering) resides in `InventoryViewModel`. Dashboard-specific presentation logic is in `DashboardViewModel`. |
| Clean separation of UI & data | **Done** | Project is organized into distinct packages: `ui/` (screens, components, theme, navigation), `data/` (entities, DAO, repository, database), and `core/` (network, sync). No data-layer code exists in UI composables. |

### B. Local Data Persistence (Room Database)

| Requirement | Status | Implementation Details |
|---|---|---|
| Room Database configured | **Done** | `AppDatabase.kt` — Room database at version 6 with a complete migration chain (v1 through v6). Singleton pattern with thread-safe double-checked locking. Database name: `smartstock_database`. |
| Entity classes created | **Done** | **5 entities implemented** (roadmap required 3): `InventoryItem`, `CategoryEntity`, `AssetStatusEntity`, `ItemHistory`, `LocalUser`. All annotated with `@Entity`, `@PrimaryKey`, and appropriate `@Index` / `@ForeignKey` constraints. |
| DAO with CRUD operations | **Done** | `InventoryDao.kt` provides: `insertItem()`, `updateItem()`, `deleteItem()`, `getAllItems()`, `getItemById()`, `searchItems()`, `getItemsByCategory()`, `getItemsByStatus()`, `getAvailableItems()`, `getInUseItems()`, plus history and user management queries. All read operations return `Flow<T>` for reactive streaming; all mutations are `suspend` functions. |
| Data persists after app restart | **Done** | Room with SQLite backing ensures data survives app restarts. Migration chain preserves data across all schema versions. Reference data is seeded on first launch via `seedReferenceData()`, `seedIfEmpty()`, and `seedUsers()`. |

#### Room Entities Detail

| Entity | Table Name | Key Fields | Notes |
|---|---|---|---|
| `InventoryItem` | `inventory_items` | id, assetCode, name, description, category, quantity, inUseQuantity, condition, status, location, createdAt, lastUpdated | Unique index on `assetCode`. Computed properties: `availableQuantity`, `isUsageTrackable`. |
| `CategoryEntity` | `categories` | id, name | Unique index on `name`. Seeded with: Equipment, Tools, Supplies. |
| `AssetStatusEntity` | `asset_statuses` | id, name | Unique index on `name`. Seeded with: Available, In-Use, Damaged, Retired. |
| `ItemHistory` | `item_history` | id, itemId, action, details, timestamp | Foreign key to `InventoryItem` with CASCADE delete. Index on `itemId`. |
| `LocalUser` | `local_users` | id, name, email, role, isActive | Unique index on `email`. Seeded with default Admin and Staff users. |

#### DAO Operations Summary

| Operation Type | Methods |
|---|---|
| **Insert** | `insertItem()`, `insertHistory()`, `insertCategories()`, `insertAssetStatuses()`, `insertUser()` |
| **Update** | `updateItem()`, `updateUser()` |
| **Delete** | `deleteItem()`, `deleteUser()` |
| **Query (single)** | `getItemById()`, `getItemByAssetCode()`, `getItemsCount()`, `getUsersCount()` |
| **Query (list)** | `getAllItems()`, `searchItems()`, `getAvailableItems()`, `getInUseItems()`, `getItemsByCategory()`, `getItemsByStatus()`, `getAllHistory()`, `getHistoryDataItem()`, `getAllCategoryNames()`, `getAllStatusNames()`, `getAllUsers()` |

### C. Jetpack Compose UI

| Requirement | Status | Implementation Details |
|---|---|---|
| Inventory list screen | **Done** | `InventoryListScreen.kt` — Master-detail pane layout with search bar, category/status filter chips, sort controls, swipe-to-delete (Admin only), FAB for adding items, and connectivity badge. |
| Add/edit item screen | **Done** | `AddEditItemScreen.kt` — Dialog and full-screen variants with form validation, category/status selection dropdowns, quantity input, and asset code pre-fill from barcode scan. |
| Item detail screen | **Done** | `ItemDetailScreen.kt` — Displays full item info, usage action buttons (Use/Return), history timeline, edit/delete FABs (Admin only), and delete confirmation dialog. |
| Offline indicator | **Done** | `ConnectivityStatusBadge` component in `CommonComponents.kt` — Shows WiFi icon when online, WiFi-off icon when offline. Displayed in the inventory list top bar. Powered by `NetworkConnectivityObserver`. |
| Material Design 3 styling | **Done** | `Theme.kt` uses `lightColorScheme()` / `darkColorScheme()` with custom color palette. Dynamic color support for Android 12+. MD3 components used throughout (Card, TopAppBar, FAB, Chip, etc.). |

#### Application Screens Implemented

| # | Screen | File | Description |
|---|---|---|---|
| 1 | Splash / Loading Screen | `SplashScreen.kt` | Animated logo (alpha + scale), title text animation, auto-navigation after 1.5s. |
| 2 | Dashboard Screen | `DashboardScreen.kt` | Summary cards (Total/Available/In-Use), quick actions, recent items, barcode scan integration. |
| 3 | Inventory List Screen | `InventoryListScreen.kt` | Full inventory with search, filter, sort, swipe-to-delete, master-detail layout. |
| 4 | Add / Edit Item Screen | `AddEditItemScreen.kt` | Form with validation, category/status pickers, quantity inputs. |
| 5 | Item Detail Screen | `ItemDetailScreen.kt` | Full item view, usage tracking, history timeline, edit/delete actions. |
| 6 | History Screen | `HistoryScreen.kt` | Timeline grouped by date, event cards with formatted timestamps. |
| 7 | Profile Screen | `ProfileScreen.kt` | Login as Admin/Staff, dark mode toggle, user management (Admin only). |
| 8 | Camera Scan Screen | `CameraScanScreen.kt` | Barcode scanner UI, scanned item display, create-from-scan option. |

### D. State Management

| Requirement | Status | Implementation Details |
|---|---|---|
| StateFlow / LiveData | **Done** | `StateFlow` is used exclusively throughout the app. `MutableStateFlow` for mutable UI state (e.g., `operationError`, `isLoggedIn`, `currentUserRole`). DAO returns `Flow<T>` converted to `StateFlow` via `.stateIn()` with `SharingStarted.WhileSubscribed(5000L)`. |
| Lifecycle-aware ViewModels | **Done** | Both ViewModels use `viewModelScope` for coroutine lifecycle management. UI collects state via `collectAsStateWithLifecycle()` which automatically stops/starts collection based on lifecycle. |
| Reactive UI updates | **Done** | All data flows from Room DAO through Repository to ViewModel to Composable screens reactively. Combined state flows for search/filter/sort with `combine()` operator. |

### E. Navigation

| Requirement | Status | Implementation Details |
|---|---|---|
| Compose Navigation | **Done** | `NavGraph.kt` with `NavHost` and `composable()` route declarations. Type-safe routes defined via sealed class `Screen` (Splash, Home, Inventory, CameraScan, History, Profile). Back stack management with `popUpTo`. |

---

## III. Technical Requirements Compliance

| Requirement | Status | Evidence |
|---|---|---|
| Jetpack Compose UI components | **Done** | All screens built with Compose. Reusable components in `CommonComponents.kt` (SummaryCard, QuickActionButton, InventoryItemCard, StatusChip, ConnectivityStatusBadge, etc.). |
| Room database with DAOs | **Done** | `AppDatabase.kt` (v6), `InventoryDao.kt` with full CRUD + complex queries. |
| Repository abstraction | **Done** | `InventoryRepository.kt` wraps all DAO operations and cloud sync queueing. |
| Compose Navigation | **Done** | `NavGraph.kt` with sealed `Screen` class for type-safe routing. |
| Material Design 3 | **Done** | Custom `lightColorScheme` / `darkColorScheme`, MD3 typography, dynamic color support, edge-to-edge display. |

---

## IV. Dependency Injection Approach

### Current Build (MCO 1): Manual Dependency Injection

The current MCO 1 build uses **manual dependency injection** through the Android `Application` class — **not Hilt**. Dependencies are created and managed in `SmartStockApp.kt` using Kotlin's `by lazy` delegate for lazy, singleton initialization:

```kotlin
class SmartStockApp : Application() {
    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val database by lazy { AppDatabase.getDatabase(this) }
    val cloudSyncDataSource by lazy { NoOpCloudSyncDataSource() }
    val repository by lazy {
        InventoryRepository(database.inventoryDao(), cloudSyncDataSource)
    }
    val connectivityObserver by lazy { NetworkConnectivityObserver(this) }
}
```

ViewModels receive their dependencies through **custom `ViewModelProvider.Factory` implementations**:

```kotlin
// In MainActivity.kt
private val viewModel: InventoryViewModel by viewModels {
    InventoryViewModelFactory(
        repository = (application as SmartStockApp).repository
    )
}

private val dashboardViewModel: DashboardViewModel by viewModels {
    DashboardViewModelFactory(
        repository = (application as SmartStockApp).repository,
        connectivityObserver = (application as SmartStockApp).connectivityObserver
    )
}
```

#### How the Current Manual DI Works

1. **`SmartStockApp` (Application class)** acts as the dependency root, creating singleton instances of `AppDatabase`, `InventoryRepository`, `CloudSyncDataSource`, and `NetworkConnectivityObserver`.
2. **`InventoryViewModelFactory`** and **`DashboardViewModelFactory`** implement `ViewModelProvider.Factory` to pass the repository (and other dependencies) into ViewModels during construction.
3. **`MainActivity`** retrieves dependencies from the Application class via casting: `(application as SmartStockApp).repository`.

#### Why Manual DI Was Chosen for MCO 1

- Keeps the project lightweight with fewer third-party dependencies.
- Sufficient for the current scope with only 2 ViewModels and a small dependency graph.
- Easier to understand and debug during the foundational development phase.

#### MCO 2 Plan: Migration to Hilt Dependency Injection

In the MCO 2 build, the project will migrate to **Hilt (Dagger-based DI)** to support the growing complexity of cloud sync services, WorkManager tasks, and additional ViewModels. This migration will:

- Replace `SmartStockApp` manual initialization with `@HiltAndroidApp`.
- Replace `ViewModelProvider.Factory` classes with `@HiltViewModel` and `@Inject constructor`.
- Provide `@Module` / `@Provides` bindings for `AppDatabase`, `InventoryDao`, `InventoryRepository`, `CloudSyncDataSource`, and `ConnectivityObserver`.
- Enable scoped injection (`@Singleton`, `@ActivityScoped`) for better lifecycle management.
- Support `@AndroidEntryPoint` for Activities, Fragments, and WorkManager workers.

---

## V. Beyond MCO 1 — Features Already Implemented (MCO 2 Scope)

The current build includes several features that are ahead of schedule and fall under MCO 2 requirements:

| Feature | MCO 2 Requirement | Current Status |
|---|---|---|
| Barcode / QR Code Scanning | Section 7.1 | Partially implemented — GMS BarCode Scanner integrated in Dashboard and CameraScan screens. |
| User Roles & Access Control | Section 7.1 | Fully implemented — `UserRole` enum with Admin/Staff permissions, runtime enforcement on all CRUD operations. |
| Cloud Sync Architecture | Section 7.1 | Interface ready — `CloudSyncDataSource` interface with `NoOpCloudSyncDataSource` stub; all mutations queue for sync. |
| Offline Indicator | Section 7.1 | Fully implemented — `NetworkConnectivityObserver` with `ConnectivityStatusBadge` UI component. |
| Adaptive / Two-Pane Layout | Enhanced UI | Fully implemented — `AdaptiveScaffold.kt` with `WindowSizeClass` for tablet/landscape master-detail views. |
| Item History / Audit Trail | Enhanced UI | Fully implemented — `ItemHistory` entity with timeline display in HistoryScreen and ItemDetailScreen. |
| Dark Mode Toggle | Enhanced UI | Fully implemented — User-switchable theme in ProfileScreen with `rememberSaveable` persistence. |

---

## VI. MCO 1 Output Verification

| Required Output | Status | Evidence |
|---|---|---|
| Fully functional offline-first inventory app | **Met** | All inventory CRUD operations work with Room. Data persists across restarts. No network required for core functionality. |
| Clean and scalable architecture | **Met** | MVVM + Repository pattern with clear package separation. Cloud sync interface ready for MCO 2 extension. |
| Persistent local asset records | **Met** | Room database v6 with migration chain. 5 entities, full DAO, seeded reference data. |

---

## VII. Summary

The SmartStock+ MCO 1 build **fully meets all required specifications** outlined in Section 6 of the Project Roadmap. The application demonstrates:

- A well-structured MVVM architecture with repository pattern
- Complete Room database implementation with 5 entities, comprehensive DAO, and migration chain
- Responsive Jetpack Compose UI with 8 screens, Material Design 3, and Compose Navigation
- Reactive state management using StateFlow and lifecycle-aware collection
- Manual dependency injection via Application class and ViewModelProvider.Factory (with Hilt DI planned for MCO 2)

The build also includes several MCO 2 features already in progress, positioning the project ahead of schedule for the final submission.
