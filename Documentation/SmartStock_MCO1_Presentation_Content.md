


# SmartStock+ MCO 1 Presentation Content
## Mobile Programming 2 - Midterm MCO Presentation
### Based on Template: 18 Slides | Estimated Duration: 20-25 minutes

---

## SLIDE 1: Title Slide

**SmartStock+**
Offline-First Inventory & Asset Management System

- **Group:** Team 2
- **Course:** Mobile Programming 2
- **Instructor:** [Instructor Name]
- **Date:** [Presentation Date]
- **Members:**
  - Jhon Clarence B. Rulona - Project Leader, Back-End Developer
  - Cris Gerard O. Carpon - Project Leader, Back-End Developer
  - Jasper Keith P. Teorica - Project Leader
  - Mark Jacob S. Allero - UI/UX Designer
  - John Dennis Y. Chan - UI/UX Designer
  - Leopoldo C. Villaflores III - UI/UX Designer
  - Dustin Aaron D. Tocayon - Back-End Developer, QA/Tester
  - Aleah A. Cantiga - Quality Assurance/Tester
  - Angel Mae S. Dura - Quality Assurance/Tester
  - Heizel D. Panugaling - Quality Assurance/Tester
  - Justin Jamaica P. Julaton - Quality Assurance/Tester
  - Roselyn B. Deguino - Document Specialist
  - Lou Ariane Mae B. Delos Reyes - Document Specialist
  - Rynel Jay V. Vallejos - Document Specialist

---

## SLIDE 2: Project Overview

- **What it does:** SmartStock+ is an Android inventory and asset management app that allows organizations to track equipment, tools, and supplies on mobile devices — even without internet.
- **Purpose:** Replace error-prone paper logs and spreadsheets with a reliable, portable, offline-first mobile solution.
- **Target Users:** School laboratories, IT departments, student organizations, small offices, and asset custodians.

---

## SLIDE 3: Project Goal

- Build a scalable mobile app with clean architecture (MVVM + Repository)
- Implement offline-first readiness using Room database
- Use Hilt for dependency injection across ViewModels and Repositories
- Deliver role-based access control (Admin & Staff)
- Prepare architecture for cloud sync integration (MCO 2)

---

## SLIDE 4: Midterm Scope

**What IS included (MCO 1):**
- Local database (Room v7) with 6 entities and full migration chain
- Hilt Dependency Injection (KSP-based)
- 9 Jetpack Compose screens with Material Design 3
- MVVM + Repository data flow
- Authentication with role-based access (Admin/Staff)
- Item usage tracking with destination location
- Item image capture (camera + gallery)
- Barcode/QR scanning

**What is NOT included (MCO 2):**
- Supabase / Cloud API integration
- Remote data sync
- Usage reporting with date filters

---

## SLIDE 5: App Architecture Overview

**Content: Architecture Diagram (create as a visual diagram in the slide)**

```
Design this as a layered block diagram with arrows showing data flow:

+=========================================================+
|                     UI LAYER                             |
|  Jetpack Compose + Material Design 3                     |
|  [Splash] [Auth] [Dashboard] [Inventory] [Detail]        |
|  [Add/Edit] [History] [CameraScan] [Profile]             |
|  collectAsStateWithLifecycle()                           |
+============================|=============================+
                     StateFlow |  ^ Flow<List<T>>
                             v  |
+=========================================================+
|                  VIEWMODEL LAYER                         |
|  @HiltViewModel + @Inject constructor                    |
|  [InventoryViewModel]    [DashboardViewModel]            |
|  Role checks | Validation | State management             |
+============================|=============================+
                    suspend  |  ^ Flow
                             v  |
+=========================================================+
|                 REPOSITORY LAYER                         |
|  InventoryRepository(dao, cloudSyncDataSource)           |
|  [InventoryRepository]                                   |
|  Single source of truth | Cloud sync ready (NoOp stub)   |
+============================|=============================+
                    suspend  |  ^ Flow
                             v  |
+=========================================================+
|                   DATA LAYER                             |
|  Room Database v7 (Provided by Hilt @Module @Singleton)  |
|  [InventoryDao] - 35 operations                         |
|  6 Entities | 6 Migrations | Reactive Flow queries       |
+=========================================================+

Side annotation (right side of diagram):
  HILT DI manages all layer connections
  @Module -> @Provides -> @Inject -> @HiltViewModel
```

**Presenter notes (not on slide):**
- UI observes StateFlow via collectAsStateWithLifecycle
- ViewModel handles business logic, validation, role-based permission checks
- Repository abstracts data source — ready to add remote API for MCO 2
- Room provides local persistence with reactive Flow queries

---

## SLIDE 6: Why Use Hilt?

**Features & Benefits:**
- **Compile-time safety:** Catches dependency errors at build time, not runtime
- **Annotation-driven:** Simple `@HiltViewModel`, `@Inject`, `@Module` annotations
- **Scoped lifecycles:** Dependencies tied to Android component lifecycles (Application, Activity, ViewModel)
- **Reduced boilerplate:** No manual factory classes needed for ViewModels
- **Scalability:** Easy to add new dependencies as the app grows (cloud sync, WorkManager in MCO 2)
- **Google-recommended:** Official DI solution for Android development

**Why we migrated from Manual DI:**
- AGP 9.0.1 required a KSP-only approach (no Hilt Gradle plugin)
- Clean `@HiltAndroidApp` application class replaces manual singleton management
- ViewModels receive dependencies automatically via constructor injection

---

## SLIDE 7: Hilt Module (Providing Dependencies)

```kotlin
@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase {
        return AppDatabase.getDatabase(context)
    }

    @Provides
    fun provideInventoryDao(database: AppDatabase): InventoryDao {
        return database.inventoryDao()
    }

    @Provides
    @Singleton
    fun provideCloudSyncDataSource(): CloudSyncDataSource {
        return NoOpCloudSyncDataSource()
    }

    @Provides
    @Singleton
    fun provideRepository(
        inventoryDao: InventoryDao,
        cloudSyncDataSource: CloudSyncDataSource
    ): InventoryRepository {
        return InventoryRepository(inventoryDao, cloudSyncDataSource)
    }

    @Provides
    @Singleton
    fun provideConnectivityObserver(@ApplicationContext context: Context): ConnectivityObserver {
        return NetworkConnectivityObserver(context)
    }
}
```

**Explain:**
- `@Module` + `@InstallIn(SingletonComponent::class)` = app-wide singletons
- Database uses factory pattern (`AppDatabase.getDatabase()`) with all 6 migrations inside
- `CloudSyncDataSource` provided as NoOp stub — ready for Supabase in MCO 2
- Repository receives both DAO and cloud sync data source
- `ConnectivityObserver` monitors network state for future sync
- Hilt manages the entire dependency graph automatically

---

## SLIDE 8: Data Model (Entity)

```kotlin
@Entity(
    tableName = "inventory_items",
    indices = [Index(value = ["assetCode"], unique = true)]
)
data class InventoryItem(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val assetCode: String,
    val name: String,
    val description: String?,
    val category: String,
    val quantity: Int,
    val inUseQuantity: Int = 0,
    val condition: String,
    val status: String,
    val location: String?,
    val imageUri: String? = null,
    val createdAt: Long,
    val lastUpdated: Long
)
```

**6 Entities Total:**
1. `InventoryItem` - Core inventory table with unique asset code index
2. `ItemHistory` - Audit trail with foreign key cascade delete
3. `ItemUsageRecord` - Tracks checkout location, return reason, status
4. `LocalUser` - Admin/Staff users with unique email index
5. `CategoryEntity` - Dynamic categories (Equipment, Tools, Supplies + custom)
6. `AssetStatusEntity` - Reference statuses (Available, In-Use, Damaged, Retired)

---

## SLIDE 9: DAO (Data Access Object)

```kotlin
@Dao
interface InventoryDao {

    @Query("SELECT * FROM inventory_items ORDER BY lastUpdated DESC")
    fun getAllItems(): Flow<List<InventoryItem>>

    @Query("SELECT * FROM inventory_items WHERE name LIKE '%' || :q || '%' 
            OR assetCode LIKE '%' || :q || '%'")
    fun searchItems(q: String): Flow<List<InventoryItem>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItem(item: InventoryItem)

    @Update
    suspend fun updateItem(item: InventoryItem)

    @Delete
    suspend fun deleteItem(item: InventoryItem)

    @Query("SELECT DISTINCT name FROM categories")
    fun getAllCategoryNames(): Flow<List<String>>
}
```

**Explain:**
- 35 operations covering full CRUD, search, filtering, and aggregation
- All read queries return `Flow<List<T>>` for reactive data streaming
- Suspend functions for write operations (coroutine-safe)
- Supports search by name and asset code for barcode integration

---

## SLIDE 10: Repository Pattern

```kotlin
class InventoryRepository(
    private val inventoryDao: InventoryDao,
    private val cloudSyncDataSource: CloudSyncDataSource = NoOpCloudSyncDataSource()
) {
    val allItems: Flow<List<InventoryItem>> = inventoryDao.getAllItems()
    val categoryNames: Flow<List<String>> = inventoryDao.getAllCategoryNames()
    val cloudSyncStatus: StateFlow<CloudSyncStatus> = cloudSyncDataSource.status

    suspend fun insertItem(item: InventoryItem): Long {
        val itemId = inventoryDao.insertItem(item)
        cloudSyncDataSource.queueUpsertItem(item.copy(id = itemId.toInt()))
        return itemId
    }

    suspend fun updateItem(item: InventoryItem): Int {
        val updatedCount = inventoryDao.updateItem(item)
        if (updatedCount > 0) cloudSyncDataSource.queueUpsertItem(item)
        return updatedCount
    }

    fun getActiveUsageRecordsByItem(itemId: Int): Flow<List<ItemUsageRecord>> {
        return inventoryDao.getActiveUsageRecordsByItem(itemId)
    }
}
```

**Explain:**
- Constructor receives both DAO and CloudSyncDataSource (provided by Hilt's AppModule)
- CloudSyncDataSource defaults to NoOp — cloud sync is stubbed for MCO 1
- Write operations (insert/update) queue changes to cloud sync data source
- Separates the data layer from UI — ViewModels never touch the DAO directly
- MCO 2: swap `NoOpCloudSyncDataSource` for Supabase implementation
- Handles inventory, usage records, history, and user operations

---

## SLIDE 11: ViewModel

```kotlin
@HiltViewModel
class InventoryViewModel @Inject constructor(
    private val repository: InventoryRepository
) : ViewModel() {

    val categoryNames: StateFlow<List<String>> = repository.categoryNames
        .map { it.ifEmpty { InventoryReferenceData.defaultCategories } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000),
                 InventoryReferenceData.defaultCategories)

    private val _currentUserRole = MutableStateFlow(UserRole.Staff)
    val currentUserRole: StateFlow<UserRole> = _currentUserRole.asStateFlow()

    fun useItem(item: InventoryItem, amount: Int, location: String) {
        // Role check, validation, update item, create usage record, log history
    }

    fun returnItem(item: InventoryItem, amount: Int, reason: String) {
        // Close active usage records, update quantities, log history
    }
}
```

**Explain:**
- `@HiltViewModel` + `@Inject constructor` = Hilt manages ViewModel creation
- StateFlow for reactive state, combined with `stateIn` for lifecycle-aware sharing
- Business logic: role-based permission checks, input validation, usage tracking

---

## SLIDE 12: UI (Compose)

**Content: App Screenshots**

*Insert screenshots of the 9 app screens here. Arrange in a grid or multi-row layout.*

1. Splash Screen
2. Auth/Login Screen
3. Dashboard
4. Inventory List
5. Add/Edit Item (with image capture and custom category)
6. Item Detail (with Use/Return/Condition actions)
7. History Screen
8. Camera Scan Screen
9. Profile & Settings

**Note:** This slide replaces code with actual app screenshots per instructor's instruction to consolidate all UI visuals into one slide.

---

## SLIDE 13: Data Flow with Hilt Explanation

**Diagram:**

```
User Action (tap "Use Item")
        |
ViewModel (Injected by Hilt)
  - Checks role permissions
  - Validates input
  - Creates usage record
        |
Repository (Injected by Hilt)
  - Updates item in Room
  - Inserts ItemUsageRecord
  - Inserts ItemHistory
        |
Room Database
  - Persists changes locally
  - Emits updated Flow
        |
UI Updates Automatically
  - StateFlow triggers recomposition
  - Item detail reflects new quantities
```

**Key point:** Hilt wires every layer together at compile time. No manual factory classes, no service locators — just annotations.

---

## SLIDE 14: Demonstration

**Live Demo Checklist:**
1. **App Launch:** Splash animation -> Login screen (no bottom nav)
2. **Authentication:** Login as Admin (admin@smartstock.local / admin123)
3. **Insert Data:** Add new item with image capture (camera or gallery), custom category
4. **Display Data:** Search, filter by category/status, view item detail with photo
5. **Usage Tracking:** Use item -> specify destination location -> Return item with reason
6. **Condition Update:** Staff quick condition modal (New/Good/Fair/Poor)
7. **Role Switching:** Log out, log in as Staff -> observe restricted permissions
8. **Offline Usage:** Turn off internet -> all features continue working
9. **Adaptive Layout:** Show landscape/tablet two-pane layout (if available)

---

## SLIDE 15: Key Achievements

- Working Room database v7 with 6 entities and complete migration chain (v1-v7)
- Clean MVVM architecture with Hilt DI (KSP-based, no Gradle plugin)
- Offline-first design — fully functional without internet
- Role-based authentication (Admin full CRUD, Staff limited operations)
- Item usage lifecycle: checkout with destination -> return with reason
- Image capture via camera and gallery with Coil display
- Barcode/QR scanning integrated with inventory lookup
- Adaptive UI: bottom navigation (portrait) + navigation rail (landscape)
- Dark mode support with Material Design 3 theming
- 9 Compose screens with reactive StateFlow-driven updates

---

## SLIDE 16: Challenges Encountered

- **Hilt + AGP 9.0.1 incompatibility:** The Hilt Gradle plugin dropped support for `BaseExtension` API in AGP 9.0.1. We resolved this by using a KSP-only approach with `@HiltAndroidApp(Application::class)` extending `Hilt_SmartStockApp()`.
- **Room migration complexity:** Managing 7 schema versions with data-preserving migrations required careful SQL `ALTER TABLE` statements and thorough testing.
- **Flow-based data in coroutines:** Using `Flow.collect()` inside `viewModelScope.launch` caused indefinite suspension. Fixed by using `.first()` for one-shot queries within coroutines.
- **Reactive state composition:** Combining search, filter, sort, and role state into a single reactive stream required careful use of Kotlin's `combine` operator.
- **Camera integration:** FileProvider configuration and runtime permission handling for camera capture across different Android versions.

---

## SLIDE 17: Preparation for Final Term (MCO 2)

- **Cloud Sync:** Integrate Supabase for real-time cross-device data synchronization
- **Usage Reporting:** Dedicated screen to track all checked-out items with date filters (monthly/annually)
- **Background Sync:** WorkManager for reliable offline-to-cloud data sync
- **Enhanced Scanning:** Improved barcode/QR flow with auto-match and batch scanning
- **UI Polish:** Two-pane empty state improvements, Compose animations
- **Instructor Recommendations:** Complete remaining items (usage tracking screen, reporting)

---

## SLIDE 18: Conclusion

- SmartStock+ delivers a fully functional offline-first inventory management system
- Clean architecture (MVVM + Repository + Hilt DI) ensures scalability
- All MCO 1 requirements met and exceeded with additional features
- Architecture is ready for MCO 2 cloud integration — just plug in the remote data source
- The team is confident and prepared to proceed to the final term

**Thank you. We are open for questions.**

---

## RUBRIC ALIGNMENT

| Criteria | Points | How We Address It |
|---|---|---|
| App Architecture | 25 | MVVM + Repository + clean layer separation |
| Room Implementation | 15 | v7, 6 entities, 35 DAO ops, migration chain |
| Hilt DI Implementation | 15 | @HiltViewModel, @Module, KSP-based injection |
| Repository Pattern | 15 | Single abstraction, @Inject constructor, Flow |
| Data Flow Understanding | 10 | StateFlow, combine, lifecycle-aware collection |
| Demo | 10 | Full CRUD, auth, usage tracking, offline |
| Presentation | 10 | 18 slides, all members participate |
| **TOTAL** | **100** | |
