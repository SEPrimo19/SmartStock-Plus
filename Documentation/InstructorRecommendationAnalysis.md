YEs# SmartStock+ Instructor Recommendation Analysis

**Course:** Mobile Programming 2
**Date of Analysis:** April 10, 2026
**Consultation Date:** March 2026
**Target Implementation:** MCO 1 Enhancement (Recommendations + Hilt DI) + MCO 2 (WorkManager, Background Processing, Cloud Sync)

---

## I. Overview

This document analyzes the instructor's recommendations received during the midterm consultation. The instructor's **10 feature recommendations** and **Hilt Dependency Injection migration** are improvements to the **MCO 1 build** and must be implemented as part of the MCO 1 enhancement cycle. **Hilt DI is the #1 priority** — migrating first ensures all subsequent recommendation refactors (new ViewModels, entities, screens) are built on proper DI from the start.

The remaining **4 technical requirements** (WorkManager, Background Processing, Work Policies, Work Constraints) are designated for **MCO 2 development** and will be implemented alongside Supabase cloud sync integration.

This document also covers alignment with the **MP-2 Midterm MCO Presentation Template** and grading rubric. The presentation will be updated once all MCO 1 recommendations and Hilt DI are implemented.

---

## II. Instructor Recommendations — MCO 1 Enhancements

These 10 recommendations must be implemented to improve the current MCO 1 build.

---

### Recommendation 1: Separate AuthScreen for User Role Access (Admin & Staff)

**Current State:** NOT IMPLEMENTED

The current build handles login/logout through the **ProfileScreen** — users tap "Login as Admin" or "Login as Staff" buttons in a session access section. There is no dedicated, separate AuthScreen that gates the app on launch.

**What Exists:**
- `UserRole.kt` — enum with Admin/Staff roles and permission flags (`canManageInventory`, `canAdjustUsage`)
- `InventoryViewModel` — has `loginAs(role)`, `logout()`, `isLoggedIn: StateFlow<Boolean>`, `currentUserRole: StateFlow<UserRole>`
- ProfileScreen login buttons call `viewModel.loginAs(UserRole.Admin)` or `viewModel.loginAs(UserRole.Staff)`

**What's Missing:**
- A dedicated `AuthScreen.kt` composable that appears on app launch
- Navigation guard: the app should not allow access to Dashboard/Inventory until the user authenticates
- The AuthScreen should present a proper login form (email/password or user selection) and route to the appropriate role-based experience
- NavGraph update: `startDestination` should route to AuthScreen instead of Splash (or Splash -> AuthScreen -> Home)

**Action Required:**
- Create `AuthScreen.kt` in `ui/screens/`
- Add `Screen.Auth` to the sealed `Screen` class
- Update `NavGraph.kt` to route Splash -> Auth -> Home
- Enforce authentication state check before allowing navigation to protected screens
- After creating a Staff user (by Admin), that user should be selectable on the AuthScreen for login
- Add authentication credentials (password/PIN) to `LocalUser` entity
- Room migration to add credential field to `local_users` table

---

### Recommendation 2: Empty State in Two-Pane Layout (Inventory Screen)

**Current State:** PARTIALLY IMPLEMENTED

The current build has an adaptive two-pane layout via `AdaptiveScaffold.kt` and `MasterDetailPane`. `InventoryListScreen.kt` uses `MasterDetailPane` with a `DetailPlaceholder` composable when no item is selected.

**What Exists:**
- `AdaptiveScaffold.kt` and `WindowSize.kt` — adaptive layout infrastructure
- `MasterDetailPane` — master-detail split for expanded screens
- `DetailPlaceholder` — shown when no item is selected (right pane)
- Left pane shows scrollable inventory list with search/filter

**What's Missing:**
- The instructor wants the right pane to clearly show a "Select an item" placeholder state BEFORE the user clicks any item
- The left panel must remain browsable and scrollable independently
- Need to verify the detail pane properly clears/resets when navigating away and back

**Action Required:**
- Review and improve `DetailPlaceholder` to be more visually clear (icon + instructional text like "Select an item to view details")
- Ensure the left pane scroll state is preserved when selecting/deselecting items
- Test on actual tablet/landscape to confirm independent scroll behavior

---

### Recommendation 3: Admin Can CRUD and Add Staff

**Current State:** PARTIALLY IMPLEMENTED

**What Exists:**
- Admin role has `canManageInventory = true` — can insert, update, delete inventory items
- Admin can add users in ProfileScreen — there is an "Add User" form with name, email, and role fields
- `InventoryDao` has `insertUser()`, `updateUser()`, `deleteUser()` methods
- `InventoryRepository` exposes `insertUser()`, `updateUser()`, `deleteUser()`
- ProfileScreen shows user list with delete buttons (Admin only)

**What's Missing:**
- The user creation flow should be more prominent and tied to the new AuthScreen system (Rec. 1)
- After creating a Staff user, that user should be selectable during authentication on the AuthScreen
- Password/PIN field for created users (currently no authentication credential — just email)
- User management should be clearly separated as an Admin-only feature section

**Action Required:**
- Tie user creation to the new AuthScreen authentication flow
- Add authentication credentials (password/PIN) to `LocalUser` entity
- Ensure created Staff users appear on the AuthScreen for login
- Room migration to add credential field to `local_users` table

---

### Recommendation 4: Staff Can Update Condition Directly from Item Click Modal

**Current State:** NOT IMPLEMENTED AS DESCRIBED

The current build allows condition updates only through the full **Add/Edit Item form** (`AddEditItemScreen.kt`), which requires opening the edit dialog and scrolling to the condition field. Conditions exist as: New, Good, Fair, Poor.

**What Exists:**
- `InventoryItem.condition` field in the entity
- `AddEditItemScreen.kt` — has `FilterChip` selectors for conditions: `listOf("New", "Good", "Fair", "Poor")`
- Staff currently CANNOT edit items at all (`canManageInventory = false` blocks all edit operations)

**What's Missing:**
- When Staff clicks an item in the inventory list, a modal/bottom sheet should appear with **direct condition update buttons** (New, Good, Fair, Poor) — NOT through the full edit form
- Staff should be able to update ONLY the condition — not other fields like name, quantity, category
- The `UserRole.Staff` needs a new permission: `canUpdateCondition = true`

**Action Required:**
- Create a `ConditionUpdateBottomSheet` or `ConditionUpdateDialog` composable
- Add a new permission flag to `UserRole`: `canUpdateCondition`
- In `InventoryViewModel`, add `updateItemCondition(item, newCondition)` method that only updates the condition field
- In `InventoryListScreen`, when Staff clicks an item, show the quick condition modal instead of navigating to detail
- Record condition change in `ItemHistory`

---

### Recommendation 5: Use Item — Add Location for Delivery/Usage Tracking

**Current State:** NOT IMPLEMENTED AS DESCRIBED

The current `useItem()` function only tracks quantity changes (inUseQuantity). It does not capture WHERE the item is being delivered or used.

**What Exists:**
- `InventoryViewModel.useItem(item, amount)` — checks out items by increasing `inUseQuantity`
- `InventoryItem.location` field exists but represents the item's storage location, not the delivery destination
- `ItemHistory` records "Item Checked Out" with quantity details but no location info

**What's Missing:**
- When clicking "Use," a location input field should be required (e.g., "Faculty Room," "Lab 201")
- A **separate screen or section** to track all items currently in use, showing WHERE each item is located
- This enables Staff to easily find items for maintenance reports
- Need a new entity or fields to track per-usage location (since multiple units of the same item can be at different locations)

**Action Required:**
- Create a new `ItemUsageRecord` entity:
  ```kotlin
  @Entity(tableName = "item_usage_records")
  data class ItemUsageRecord(
      @PrimaryKey(autoGenerate = true) val id: Int = 0,
      val itemId: Int,           // FK to InventoryItem
      val quantity: Int,          // How many units checked out
      val location: String,       // Where delivered/used (e.g., "Faculty Room")
      val usedBy: String,         // Who checked it out
      val checkedOutAt: Long,     // Timestamp
      val returnedAt: Long? = null, // Null = still in use
      val returnReason: String? = null, // Ties to Rec. 6
      val status: String = "Active" // Active / Returned
  )
  ```
- Modify `useItem()` to require a `location: String` parameter
- Create a new `UsageTrackingScreen.kt` — shows all active checkouts grouped by item or location
- Staff can view this screen to know where every checked-out item is located
- Add navigation route for the new screen

---

### Recommendation 6: Return Item — Specify Reason for Return

**Current State:** NOT IMPLEMENTED

The current `returnItem()` function only tracks the quantity being returned. It does not capture WHY the item is being returned.

**What Exists:**
- `InventoryViewModel.returnItem(item, amount)` — decreases `inUseQuantity`
- `ItemHistory` records "Item Returned" with quantity details only

**What's Missing:**
- When clicking "Return," a reason/note field should be required (e.g., "No longer needed," "Maintenance required," "Defective," "Event ended")
- The return reason should be stored in `ItemHistory.details` and the `ItemUsageRecord` (from Rec. 5)
- The return should update the `ItemUsageRecord` with `returnedAt` and `returnReason`

**Action Required:**
- Add `reason: String` parameter to `returnItem()` in ViewModel
- Update the Return dialog UI to include a reason text field or predefined reason chips
- Store return reason in both `ItemHistory` and `ItemUsageRecord`
- Validate that a reason is provided before allowing the return

---

### Recommendation 7: Staff Can Update Condition/Status but Cannot Add Items

**Current State:** PARTIALLY IMPLEMENTED (permissions only)

**What Exists:**
- `UserRole.Staff` has `canManageInventory = false` — Staff cannot add, edit, or delete items
- `UserRole.Staff` has `canAdjustUsage = true` — Staff can use/return items

**What's Missing:**
- Staff currently cannot update the condition of items at all (blocked by `canManageInventory` check)
- Need a granular permission: Staff should be able to update condition/status but NOT add or delete items
- This ties directly to Recommendation 4 (quick condition update modal)

**Action Required:**
- Refactor `UserRole` permissions to be more granular:
  ```kotlin
  enum class UserRole(
      val displayName: String,
      val canAddItem: Boolean,
      val canEditItem: Boolean,
      val canDeleteItem: Boolean,
      val canUpdateCondition: Boolean,   // NEW
      val canAdjustUsage: Boolean
  ) {
      Admin(displayName = "Admin",
          canAddItem = true, canEditItem = true, canDeleteItem = true,
          canUpdateCondition = true, canAdjustUsage = true),
      Staff(displayName = "Staff",
          canAddItem = false, canEditItem = false, canDeleteItem = false,
          canUpdateCondition = true, canAdjustUsage = true)
  }
  ```
- Update all permission checks across ViewModel and UI to use the new granular flags

---

### Recommendation 8: Staff Can Check All Used Items for Reports (Monthly/Annually)

**Current State:** NOT IMPLEMENTED

The current `HistoryScreen` shows a timeline of all events but does not provide filtered reporting capability by date range or usage status.

**What Exists:**
- `HistoryScreen.kt` — shows all history events grouped by date
- `ItemHistory` entity tracks all actions with timestamps
- `InventoryDao.getAllHistory()` returns all history ordered by timestamp

**What's Missing:**
- A dedicated reporting view or filter for Staff to see all items currently in use and where they are located
- Date range filter (monthly, annually, custom range)
- Grouped report by item, location, or date period
- Summary view for maintenance/inventory reports

**Action Required:**
- Create `UsageReportScreen.kt` — or extend `UsageTrackingScreen` (from Rec. 5) with date filters
- Add DAO queries:
  ```kotlin
  @Query("SELECT * FROM item_usage_records WHERE checkedOutAt BETWEEN :startDate AND :endDate")
  fun getUsageByDateRange(startDate: Long, endDate: Long): Flow<List<ItemUsageRecord>>
  
  @Query("SELECT * FROM item_usage_records WHERE status = 'Active'")
  fun getActiveUsageRecords(): Flow<List<ItemUsageRecord>>
  ```
- Add period filter chips: "This Month," "This Year," "All Time," "Custom Range"
- Show summary statistics: total items used, total returned, currently active, by location
- Navigation route for the report screen

---

### Recommendation 9: Dynamic Categories (User Can Add Categories)

**Current State:** NOT IMPLEMENTED IN UI (data layer ready)

Categories are currently **static** — hardcoded as reference data seeded from `InventoryReferenceData.defaultCategories = listOf("Equipment", "Tools", "Supplies")`.

**What Exists:**
- `CategoryEntity` — Room entity for categories
- `InventoryDao.insertCategories()` — can insert new categories (with IGNORE conflict)
- `InventoryDao.getAllCategoryNames()` — returns all category names
- `AddEditItemScreen` uses `categoryNames` from ViewModel for the category dropdown
- The infrastructure for dynamic categories exists at the data layer but is NOT exposed in the UI

**What's Missing:**
- UI for adding custom categories when adding or editing an item
- The Add/Edit Item category dropdown should include an "Add New Category" option
- Optionally, a category management screen where Admin can add/rename/delete categories

**Action Required:**
- Add "Add New Category" option at the bottom of the category dropdown in `AddEditItemScreen`
- When selected, show a text field for the new category name
- Call `repository.insertCategory(newCategory)` to persist
- The new category should immediately appear in the dropdown
- Optionally: Admin-only category management in Settings/Profile

---

### Recommendation 10: Capture Item Image (Barcode Scan + Camera)

**Current State:** NOT IMPLEMENTED

The current build supports barcode scanning for adding and searching items (via GMS BarCode Scanner), but does not capture or display item images.

**What Exists:**
- `CameraScanScreen.kt` — barcode scanner UI with scan result handling
- `DashboardScreen.kt` — barcode scan integration for quick lookup
- Barcode scanning successfully adds items and searches for existing items by asset code
- `InventoryItem` entity has NO image field

**What's Missing:**
- Ability to capture a photo of the item using the device camera during add/edit
- Image storage in Room database (as file path or URI — NOT raw blob for performance)
- Item image display in the Inventory List screen (thumbnail in each item card)
- Item image display when searching through barcode scanning (show item photo in scan result)
- Image storage in cloud (Supabase Storage) for MCO 2

**Action Required (MCO 1 — Local Storage):**
- Add `imageUri: String?` field to `InventoryItem` entity
- Room migration to add `imageUri` column to `inventory_items` table
- Implement camera capture using `ActivityResultContracts.TakePicture()` or CameraX
- Save captured image to app's internal storage (`filesDir` or `cacheDir`)
- Store the local file path/URI in `InventoryItem.imageUri`
- Update `AddEditItemScreen.kt` — add "Take Photo" button that launches camera
- Update `InventoryItemCard` in `CommonComponents.kt` — display thumbnail image if available
- Update `ItemDetailScreen.kt` — display full-size item image
- Update barcode scan result display — show item image when a scanned code matches an existing item
- Fallback placeholder icon when no image is available

**Action Required (MCO 2 — Cloud Storage):**
- Upload images to Supabase Storage bucket when cloud sync is active
- Store cloud URL alongside local URI for redundancy
- Download and cache cloud images for offline access
- Sync image state: local-only -> uploading -> synced

---

## III. Instructor's Design Question — Analysis

> "What proper decision to do about the use item and from the unused item? For example there are 5 computer items and the 3 was delivered to specific location and there are remaining 2 items in the inventory. When the staff are checking for the used items, because they have the same Asset Code, when the items needs to return what should the system do?"

### Recommended Approach: Usage Record Tracking

The system should implement **individual usage records** per checkout transaction, not just a quantity counter:

1. **On "Use" (Checkout):**
   - Create an `ItemUsageRecord` with: itemId, quantity, destination location, checkedOutBy, timestamp
   - Each checkout is a separate record even for the same asset code
   - Example: 3 computers delivered to "Faculty Room" = 1 usage record (qty: 3, location: "Faculty Room")

2. **On "Return":**
   - Staff selects WHICH usage record to return from (not just a generic quantity)
   - Staff specifies return reason and quantity
   - The usage record is updated with `returnedAt` timestamp and reason
   - If partial return (e.g., return 1 of 3), the record splits or updates quantity

3. **Inventory State:**
   - `InventoryItem.quantity` = total owned (5 computers)
   - Sum of active `ItemUsageRecord.quantity` = total in use (3 computers)
   - Available = total - in use (2 computers)
   - The current `inUseQuantity` field on `InventoryItem` becomes a computed aggregate

4. **Staff Tracking View:**
   - Shows all active usage records: which items, how many, where, since when
   - Filterable by location, date, item category
   - Enables maintenance planning and accountability

---

## IV. Hilt Dependency Injection — MCO 1 Priority #1

**Current State:** NOT IMPLEMENTED (Manual DI used)

The current build uses manual dependency injection through `SmartStockApp` (Application class) with `by lazy` singletons and custom `ViewModelProvider.Factory` classes. **Hilt DI is the first thing to implement** — all subsequent MCO 1 enhancements (new screens, ViewModels, entities, repositories) will be built on top of Hilt from the start, avoiding double refactoring.

**What Exists (Manual DI):**
- `SmartStockApp.kt` — dependency root with `by lazy` initialization for database, repository, cloudSyncDataSource, connectivityObserver
- `InventoryViewModelFactory` — custom `ViewModelProvider.Factory` for `InventoryViewModel`
- `DashboardViewModelFactory` — custom `ViewModelProvider.Factory` for `DashboardViewModel`
- `MainActivity.kt` — retrieves dependencies via `(application as SmartStockApp).repository`

**MCO 1 Action Required (Migration to Hilt):**
- Add Hilt dependencies to `build.gradle.kts` (hilt-android, hilt-compiler, hilt-navigation-compose)
- Annotate `SmartStockApp` with `@HiltAndroidApp`
- Annotate `MainActivity` with `@AndroidEntryPoint`
- Create `di/AppModule.kt`:
  ```kotlin
  @Module
  @InstallIn(SingletonComponent::class)
  object AppModule {
      @Provides @Singleton
      fun provideDatabase(@ApplicationContext context: Context): AppDatabase

      @Provides
      fun provideInventoryDao(db: AppDatabase): InventoryDao

      @Provides @Singleton
      fun provideRepository(dao: InventoryDao, cloudSync: CloudSyncDataSource): InventoryRepository

      @Provides @Singleton
      fun provideCloudSyncDataSource(): CloudSyncDataSource

      @Provides @Singleton
      fun provideConnectivityObserver(@ApplicationContext context: Context): ConnectivityObserver
  }
  ```
- Convert `InventoryViewModel` to `@HiltViewModel` with `@Inject constructor`
- Convert `DashboardViewModel` to `@HiltViewModel` with `@Inject constructor`
- Remove `InventoryViewModelFactory` and `DashboardViewModelFactory`
- Replace `viewModels { Factory(...) }` with `hiltViewModel()` in Compose screens
- Aligns with **Lesson 2: Dependency Injection with Hilt** from course modules

**Why Hilt First:**
- All new ViewModels for MCO 1 recommendations (UsageTrackingViewModel, AuthViewModel, etc.) will use `@HiltViewModel` from the start
- New repositories and data sources get proper `@Inject` constructor injection
- Avoids creating more manual factories that would need to be refactored later
- The presentation template rubric awards **15 points** for Hilt DI implementation

---

## V. Technical Requirements — MCO 2 Development

The following 4 technical requirements are designated for **MCO 2** and will be implemented alongside Supabase cloud sync integration. They align with the course lessons (Lesson 3: Clean Architecture & Advanced MVVM).

### 1. Background Processing

**Current State:** NOT IMPLEMENTED

No background processing exists. All operations run on `viewModelScope` coroutines (foreground-bound).

**MCO 2 Action Required:**
- Implement background tasks for cloud sync operations
- Use Kotlin Coroutines with `applicationScope` (already exists in `SmartStockApp`) for operations that must survive Activity destruction

---

### 2. WorkManager

**Current State:** NOT IMPLEMENTED

No WorkManager dependency or worker classes exist in the project.

**MCO 2 Action Required:**
- Add WorkManager dependency to `build.gradle.kts`
- Create `SyncWorker` for periodic cloud synchronization
- Create `CleanupWorker` for maintenance tasks (e.g., archiving old history records)
- Integrate with Hilt using `@HiltWorker`

---

### 3. Work Policies

**Current State:** NOT IMPLEMENTED

**MCO 2 Action Required:**
- Define `ExistingWorkPolicy` for sync operations:
  - `KEEP` — if a sync is already running, don't start another
  - `REPLACE` — for user-triggered manual sync
- Define retry policies with `BackoffPolicy.EXPONENTIAL` for failed sync attempts
- Define `ExistingPeriodicWorkPolicy.UPDATE` for periodic sync scheduling

---

### 4. Work Constraints (Defining Constraints)

**Current State:** NOT IMPLEMENTED

The `ConnectivityObserver` exists for UI display but is not integrated with WorkManager constraints.

**MCO 2 Action Required:**
- Define constraints for sync work:
  ```kotlin
  val syncConstraints = Constraints.Builder()
      .setRequiredNetworkType(NetworkType.CONNECTED)
      .setRequiresBatteryNotLow(true)
      .build()
  ```
- Apply constraints to `SyncWorker` enqueue requests
- Use `NetworkConnectivityObserver` to trigger immediate sync when connectivity is restored

---

## VI. Presentation Template Alignment

The **MP-2 Midterm MCO Presentation Template** requires a specific 18-slide structure. The presentation will be **rebuilt once Hilt DI and all MCO 1 recommendations are implemented**, then updated again after MCO 2 technical requirements are completed.

### Required Template Structure

| Template Slide | Required Content | Current Status |
|---|---|---|
| Slide 1: Title | App name, group, members, course, instructor, date | Exists (needs instructor name) |
| Slide 2: Project Overview | Brief description, purpose, target users | Exists |
| Slide 3: Project Goal | Scalable structure, offline-first, Room + Repository | Needs update |
| Slide 4: Midterm Scope | Included / NOT included | Needs update |
| Slide 5: Architecture Diagram | UI -> ViewModel (Hilt) -> Repository (Hilt) -> Room (Hilt) | Needs update — will show Hilt after migration |
| Slide 6: Why Use Hilt? | Features and benefits | Will be added after Hilt migration |
| Slide 7: Hilt Module Code | `@Module`, `@InstallIn`, `@Provides` | Will be added after Hilt migration |
| Slide 8: Data Model (Entity) | Entity code snippet | Needs update |
| Slide 9: DAO | DAO code snippet | Needs update |
| Slide 10: Repository | Repository code with `@Inject constructor` | Needs update — after Hilt migration |
| Slide 11: ViewModel | ViewModel code with `@HiltViewModel` + `@Inject constructor` | Needs update — after Hilt migration |
| Slide 12: UI (Compose) | Compose code with `hiltViewModel()` | Needs update — after Hilt migration |
| Slide 13: Data Flow Diagram | Full data flow explanation | Needs update |
| Slide 14: Demonstration | Insert, display, offline usage | Exists |
| Slide 15: Key Achievements | Working Room, clean architecture, offline-first | Exists |
| Slide 16: Challenges | Room setup, state handling, data flow debugging | Exists |
| Slide 17: Final Term Prep | Firebase/API, sync, UI/UX improvement | Exists |
| Slide 18: Conclusion | Summary, readiness for scaling | Exists |

### Grading Rubric

| Criteria | Points | Current Readiness |
|---|---|---|
| App Architecture | 25 | READY — MVVM + Repository fully implemented |
| Room Implementation | 15 | READY — 5 entities, full DAO, migration chain |
| **Hilt DI Implementation** | **15** | **MCO 1 Priority #1 — Manual DI to be migrated** |
| Repository Pattern | 15 | READY — InventoryRepository fully implemented |
| Data Flow Understanding | 10 | READY — StateFlow + Flow reactive chain |
| Demo | 10 | READY — App fully functional |
| Presentation | 10 | Will be updated after implementations |
| **TOTAL** | **100** | |

**Note:** Hilt DI migration is the **#1 priority** in the MCO 1 enhancement cycle. Once Hilt is in place, all subsequent enhancements (new ViewModels, screens, repositories) will use proper `@HiltViewModel` and `@Inject constructor` from the start. The presentation will be rebuilt to include Hilt-specific slides (6, 7, 10, 11, 12) after migration is complete.

---

## VII. Implementation Priority

### MCO 1 Enhancements — Hilt DI + Instructor Recommendations (Implement Now)

| Priority | Task | Recommendation | Complexity | Dependencies |
|---|---|---|---|---|
| **P0** | **Hilt Dependency Injection migration** | **Technical Req.** | **Medium** | **Do first — all other enhancements build on Hilt** |
| **P1** | AuthScreen with role-based login | Rec. 1 & 3 | Medium | Needs Hilt; gates all other screens |
| **P1** | Granular UserRole permissions | Rec. 4 & 7 | Low | Needed for Staff condition updates |
| **P1** | Staff quick condition update modal | Rec. 4 | Medium | Depends on permission refactor |
| **P1** | ItemUsageRecord entity + Use Item with location | Rec. 5 | High | New entity, migration, ViewModel, screen |
| **P1** | Return Item with reason | Rec. 6 | Low | Depends on ItemUsageRecord |
| **P1** | Item image capture & display | Rec. 10 | Medium | Camera integration, entity migration, UI updates |
| **P2** | Usage tracking screen | Rec. 5 & 8 | Medium | Depends on ItemUsageRecord |
| **P2** | Usage reporting with date filters | Rec. 8 | Medium | Depends on usage tracking |
| **P2** | Dynamic categories in UI | Rec. 9 | Low | Data layer ready, UI only |
| **P2** | Two-pane empty state polish | Rec. 2 | Low | UI improvement only |
| **P2** | Rebuild presentation to match template | All | Low | After all enhancements are done |

### MCO 2 — Technical Requirements (Implement Later)

| Priority | Task | Complexity |
|---|---|---|
| **P1** | WorkManager + SyncWorker | Medium |
| **P1** | Work Policies & Constraints | Medium |
| **P1** | Background Processing | Medium |
| **P1** | Supabase cloud sync | High |
| **P1** | Cloud image storage (Supabase Storage) | Medium |

---

## VIII. New Entities & Schema Changes Required for MCO 1

### New Entity: `ItemUsageRecord`

```kotlin
@Entity(
    tableName = "item_usage_records",
    foreignKeys = [ForeignKey(
        entity = InventoryItem::class,
        parentColumns = ["id"],
        childColumns = ["itemId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index(value = ["itemId"])]
)
data class ItemUsageRecord(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val itemId: Int,
    val quantity: Int,
    val location: String,       // WHERE the item is delivered/used
    val usedBy: String,         // WHO checked it out
    val checkedOutAt: Long,
    val returnedAt: Long? = null,
    val returnReason: String? = null,
    val status: String = "Active" // "Active" or "Returned"
)
```

### Modified Entity: `InventoryItem`

```kotlin
// Add new field:
val imageUri: String? = null   // Local file path to captured item photo
```

### Modified Entity: `LocalUser`

```kotlin
// Add new field:
val password: String = ""      // PIN or password for AuthScreen login
```

### Room Migration Required

- Migration v6 -> v7:
  - Add `imageUri` column to `inventory_items`
  - Add `password` column to `local_users`
  - Create `item_usage_records` table with foreign key to `inventory_items`

---

## IX. New Screens Required for MCO 1

| Screen | File | Purpose |
|---|---|---|
| AuthScreen | `AuthScreen.kt` | Login screen gating app access — select user, enter PIN/password |
| UsageTrackingScreen | `UsageTrackingScreen.kt` | View all active checkouts — where items are, who has them |
| UsageReportScreen | `UsageReportScreen.kt` | Monthly/annual reports of item usage for Staff |

### Updated Navigation Flow

```
Splash -> Auth -> Home (Dashboard)
                    |-> Inventory List -> Item Detail
                    |-> Camera Scan (with image display)
                    |-> Usage Tracking (NEW)
                    |-> Usage Reports (NEW)
                    |-> History
                    |-> Profile (user management, settings)
```

---

## X. Summary

| Area | Items | Target Phase | Status |
|---|---|---|---|
| **Hilt Dependency Injection** | **Priority #1** | **MCO 1 Enhancement** | Not started — implement first |
| **Instructor Recommendations (1-10)** | **10 recommendations** | **MCO 1 Enhancement** | 0 fully done, 3 partial, 7 not started |
| Technical Requirements (1-4) | 4 requirements | MCO 2 Development | 0 done (deferred) |
| Presentation Template | 18 slides | After MCO 1 enhancements | Will rebuild |

### MCO 1 Enhancement Scope Summary

- **Hilt DI migration** (Priority #1 — do first so all new code uses proper DI)
- **3 new screens**: AuthScreen, UsageTrackingScreen, UsageReportScreen
- **1 new entity**: ItemUsageRecord
- **3 entity modifications**: InventoryItem (+imageUri), LocalUser (+password), UserRole (granular permissions)
- **1 Room migration**: v6 -> v7
- **Camera integration**: Capture and display item photos
- **Permission refactor**: Granular role-based access (Admin vs. Staff)
- **UI updates**: Condition update modal, return reason dialog, dynamic categories, image thumbnails, two-pane polish

**Implementation order:** Hilt DI first, then all instructor recommendations. The presentation will be rebuilt after all MCO 1 enhancements are complete. WorkManager, Background Processing, Work Policies, Work Constraints, and Supabase cloud sync remain MCO 2 deliverables.
