# SmartStock+ MCO 1 Presentation Scripts (Updated)
## Mobile Programming 2 - Midterm MCO Presentation
### 18 Slides | Estimated Duration: 20-25 minutes

---

## Speaker Assignment Summary

| Slide | Title | Speaker |
|-------|-------|---------|
| 1 | Title Slide | Jhon Clarence B. Rulona |
| 2 | Project Overview | Jasper Keith P. Teorica |
| 3 | Project Goal | Roselyn B. Deguino |
| 4 | Midterm Scope | Cris Gerard O. Carpon |
| 5 | App Architecture Overview | Leopoldo C. Villaflores III |
| 6 | Why Use Hilt? | Dustin Aaron D. Tocayon |
| 7 | Hilt Module | Lou Ariane Mae B. Delos Reyes |
| 8 | Data Model (Entity) | Jhon Clarence B. Rulona |
| 9 | DAO (Data Access Object) | Heizel D. Panugaling |
| 10 | Repository Pattern | Cris Gerard O. Carpon |
| 11 | ViewModel | Justin Jamaica P. Julaton |
| 12 | UI (Compose Screenshots) | Mark Jacob S. Allero |
| 13 | Data Flow with Hilt | John Dennis Y. Chan |
| 14 | Demonstration | Jhon Clarence + Mark Jacob |
| 15 | Key Achievements | Aleah A. Cantiga |
| 16 | Challenges Encountered | Angel Mae S. Dura |
| 17 | Preparation for Final Term | Rynel Jay V. Vallejos |
| 18 | Conclusion | Jasper Keith P. Teorica |

**All 14 members present. All members support Q&A.**

---

## Individual Presentation Scripts

---

### SLIDE 1 - Title Slide
**Speaker: Jhon Clarence B. Rulona**
*Role: Project Leader, Back-End Developer*

"Good day, everyone. We are Team 2, and today we are presenting our midterm progress for SmartStock+: an Offline-First Inventory and Asset Management System. This project is developed for our Mobile Programming 2 course. What we will be showing today covers our MCO 1 deliverables, including our application architecture, Hilt dependency injection, Room database implementation, and all nine Jetpack Compose screens. Our app is built on Android using Kotlin, Jetpack Compose with Material Design 3, Hilt for dependency injection, and Room for local persistence. Let me briefly introduce our team — we have 14 members organized by role: Project Leaders, Back-End Developers, UI/UX Designers, Quality Assurance Testers, and Document Specialists. Every member will be presenting today. Now I will pass it to Jasper to discuss our project overview."

---

### SLIDE 2 - Project Overview
**Speaker: Jasper Keith P. Teorica**
*Role: Project Leader*

"SmartStock+ is an Android-based inventory and asset management application. It is designed for organizations that need to track equipment, tools, and supplies using a mobile device. The key feature of our app is its offline-first design. Users can access and manage their entire inventory without an internet connection. All data is stored locally in a Room database, and the architecture is prepared for cloud synchronization in MCO 2. Our target users include school laboratories, IT departments, student organizations, small offices, and anyone responsible for managing physical assets. The problem we solve is straightforward — manual tracking methods like paper logs and spreadsheets are error-prone, hard to maintain, and completely inaccessible during network outages. SmartStock+ provides a reliable mobile alternative. Now, Roselyn will discuss our project goals."

---

### SLIDE 3 - Project Goal
**Speaker: Roselyn B. Deguino**
*Role: Document Specialist*

"Our project goals for SmartStock+ are centered on building a solid, scalable foundation. First, we aim to build a scalable mobile app structure following clean architecture principles — specifically MVVM with the Repository pattern, which separates our UI, business logic, and data layers. Second, we implement offline-first readiness using Room database so the app is fully functional without internet. Third, we use Hilt for dependency injection, which is Google's recommended DI framework for Android. This ensures our dependencies are managed cleanly and automatically across ViewModels and Repositories. Fourth, we deliver role-based access control with Admin and Staff roles, each with different permission levels. And finally, we prepare the architecture for cloud sync integration, which will be completed in MCO 2. Now, Cris will explain our midterm scope."

---

### SLIDE 4 - Midterm Scope
**Speaker: Cris Gerard O. Carpon**
*Role: Project Leader, Back-End Developer*

"For our midterm scope, let me clarify what is included and what is planned for later. In MCO 1, we have implemented the following: a local Room database at version 7 with six entities and a complete migration chain from version 1 through 7. We have Hilt dependency injection using a KSP-based approach. We built nine Jetpack Compose screens with Material Design 3 components. Our data flows through the MVVM pattern — from ViewModel to Repository to Room. We have a full authentication system with role-based access for Admin and Staff users. We implemented item usage tracking where users specify a destination location when checking out items, and provide a reason when returning them. We also added item image capture using the device camera or gallery. And we have barcode and QR code scanning integrated. What is NOT included in MCO 1 and is planned for MCO 2: Supabase cloud API integration, remote data synchronization, and usage reporting with date filters. Now, Leopoldo will walk us through our application architecture."

---

### SLIDE 5 - App Architecture Overview
**Speaker: Leopoldo C. Villaflores III**
*Role: UI/UX Designer*

"As you can see in this diagram, our application follows a layered MVVM architecture with four distinct layers, and Hilt manages the dependency injection across all of them. Starting from the top — the UI Layer is built entirely with Jetpack Compose and Material Design 3. It contains all nine screens you see listed here. The UI observes state using collectAsStateWithLifecycle, which is lifecycle-aware. Moving down, the ViewModel Layer contains our two ViewModels — InventoryViewModel and DashboardViewModel — both annotated with @HiltViewModel so Hilt creates and injects them automatically. This layer handles all business logic: validation, role-based permission checks, and state management. The arrows between UI and ViewModel show that StateFlow pushes state down to the UI, while Flow streams data back up reactively. Below that is the Repository Layer. InventoryRepository uses @Inject constructor, so Hilt provides the DAO dependency. This is our single source of truth and the layer we will extend for cloud sync in MCO 2. At the bottom is the Data Layer — Room database at version 7 with six entities, over 20 DAO operations, and seven migrations. Notice the annotation on the right side — Hilt's @Module, @Provides, @Inject, and @HiltViewModel annotations wire this entire chain together at compile time. No manual factories, no service locators. Now, Dustin will explain why we chose Hilt."

---

### SLIDE 6 - Why Use Hilt?
**Speaker: Dustin Aaron D. Tocayon**
*Role: Back-End Developer, QA/Tester*

"Hilt is Google's recommended dependency injection library for Android, built on top of Dagger. We chose Hilt for several important reasons. First, compile-time safety — Hilt catches dependency errors during the build, not at runtime, so we avoid crashes in production. Second, it is annotation-driven. Simple annotations like @HiltViewModel, @Inject, and @Module replace hundreds of lines of manual factory code. Third, scoped lifecycles — dependencies are tied to Android component lifecycles. Our database is a singleton scoped to the application, while ViewModels are scoped to their respective screens. Fourth, reduced boilerplate. Before Hilt, we needed custom ViewModelProvider.Factory classes for every ViewModel. With Hilt, the framework handles ViewModel creation automatically. Fifth, scalability. As our app grows in MCO 2 with cloud sync services, WorkManager, and additional ViewModels, Hilt makes it easy to add new dependencies without restructuring. One technical challenge we faced: the Hilt Gradle plugin was incompatible with AGP 9.0.1 because it dropped the BaseExtension API. We resolved this by using a KSP-only approach — applying the KSP plugin directly without the Hilt Gradle plugin. Now, Lou Ariane Mae will show you the actual Hilt module code."

---

### SLIDE 7 - Hilt Module (Providing Dependencies)
**Speaker: Lou Ariane Mae B. Delos Reyes**
*Role: Document Specialist*

"This is our AppModule — the Hilt module that provides all our dependencies. The @Module annotation tells Hilt this class contains dependency definitions, and @InstallIn with SingletonComponent means these are app-wide singletons that live for the entire application lifecycle. The first @Provides function creates our Room database. It receives the application context through Hilt's @ApplicationContext annotation. We build the database with all seven migrations — from version 1 through 7 — and a seed callback that populates the default categories and statuses on first launch. The @Singleton annotation ensures only one database instance exists. The second @Provides function extracts the InventoryDao from the database. And the third provides the InventoryRepository with the DAO injected. With this single module, Hilt manages our entire dependency graph. When a ViewModel needs the repository, Hilt knows it needs the DAO, which needs the database, which needs the context — and it resolves the entire chain automatically. Now, Jhon Clarence will walk through our data model."

---

### SLIDE 8 - Data Model (Entity)
**Speaker: Jhon Clarence B. Rulona**
*Role: Project Leader, Back-End Developer*

"Our core entity is InventoryItem. It uses the @Entity annotation with a custom table name 'inventory_items' and a unique index on assetCode for barcode scanning support. The primary key auto-generates, and the fields cover everything needed for inventory management: asset code, name, description, category, quantity, in-use quantity, condition, status, location, image URI for photos, and timestamps. We also have computed properties — availableQuantity returns quantity minus in-use quantity, and isUsageTrackable checks whether the item can be checked out based on its status. In total, we have six entities. InventoryItem is the core table. ItemHistory provides an audit trail with foreign key cascade delete linked to the item. ItemUsageRecord tracks checkout details including the destination location, who checked it out, return timestamp, return reason, and status. LocalUser manages Admin and Staff accounts with a unique email index. CategoryEntity and AssetStatusEntity are reference tables seeded with defaults but also supporting dynamic additions — users can add custom categories when creating items. Now, Heizel will continue with our DAO."

---

### SLIDE 9 - DAO (Data Access Object)
**Speaker: Heizel D. Panugaling**
*Role: Quality Assurance/Tester*

"Our InventoryDao interface has over 20 operations. For reads, we have queries to get all items, search by name or asset code, filter by category or status, get item counts, retrieve history by item, get active usage records, and fetch all users. Every read operation returns a Kotlin Flow, which means the UI automatically updates when the underlying data changes — there is no need to manually refresh. For writes, we have insert, update, and delete operations for items, history records, usage records, users, categories, and statuses. These are all suspend functions, meaning they run on a background thread using Kotlin coroutines without blocking the main thread. A key design decision: our search query uses LIKE with wildcards on both name and asset code fields, so barcode scans can instantly look up matching items. And the active usage records query filters by status equals 'Active', which lets us efficiently find all items currently checked out. Now, Cris will explain our Repository pattern."

---

### SLIDE 10 - Repository Pattern
**Speaker: Cris Gerard O. Carpon**
*Role: Project Leader, Back-End Developer*

"Our InventoryRepository uses @Inject constructor, which means Hilt provides the DAO dependency automatically. The Repository serves as the single source of truth between the UI and data layers. It exposes reactive streams like allItems and categoryNames as Flow, and provides suspend functions for all write operations. The key benefit of this pattern is separation. Our ViewModels never interact with the DAO directly — they only know about the Repository. This means if we replace Room with a remote API in MCO 2, or if we add a caching layer, we only change the Repository. The ViewModels and UI remain untouched. The Repository also handles both inventory operations and usage record management. When a user checks out an item, the Repository updates the item quantities, creates an ItemUsageRecord with the destination location, and inserts an ItemHistory entry — all in a single transaction-like flow. This ensures data consistency even in offline scenarios. Now, Justin Jamaica will discuss our ViewModel implementation."

---

### SLIDE 11 - ViewModel
**Speaker: Justin Jamaica P. Julaton**
*Role: Quality Assurance/Tester*

"Our InventoryViewModel is annotated with @HiltViewModel, and its constructor uses @Inject to receive the Repository. Hilt handles the creation and lifecycle of this ViewModel automatically. For state management, we use StateFlow exclusively — no LiveData. Reactive streams from Room are converted using stateIn with SharingStarted.WhileSubscribed set to 5000 milliseconds. This means the collection stops five seconds after the last screen detaches, which saves resources. For complex state like filtered inventory results, we use Kotlin's combine operator to merge the search query, category filter, status filter, and sort setting into a single reactive stream. The ViewModel also contains business logic. For example, the useItem function checks the user's role permissions, validates the quantity, updates the item's in-use count, creates an ItemUsageRecord with the destination location and the logged-in user's name, and logs the action to history. The returnItem function does the reverse — it closes active usage records, records the return reason, and restores availability. All permission checks happen in the ViewModel layer, so the UI simply calls the function and the ViewModel decides if it is allowed. Now, Mark Jacob will show our app screens."

---

### SLIDE 12 - UI (Compose Screenshots)
**Speaker: Mark Jacob S. Allero**
*Role: UI/UX Designer*

"On this slide, you can see screenshots of all nine screens in our app, all built with Jetpack Compose and Material Design 3. Starting from the top left — the Splash Screen with our animated logo. Next is the Auth Screen where users log in with email and password. Notice the bottom navigation is hidden here — users must authenticate first. The Dashboard shows summary cards for total, available, and in-use item counts with quick action buttons. The Inventory List is our main working screen with search, category and status filters, sort controls, and swipe-to-delete for Admins. The Add/Edit Item form now includes image capture — users can take a photo with the camera or pick from the gallery — and custom categories through a plus icon. The Item Detail screen displays the item photo, full information, and action buttons for Use, Return, and Condition updates. The Use dialog asks for a destination location. The Return dialog asks for a reason. And the Condition modal offers New, Good, Fair, and Poor options. We also have the History screen for the audit trail, Camera Scan for barcodes and QR codes, and the Profile screen with account info, dark mode toggle, and user management for Admins. Now, John Dennis will explain how data flows through these screens."

---

### SLIDE 13 - Data Flow with Hilt Explanation
**Speaker: John Dennis Y. Chan**
*Role: UI/UX Designer*

"Let me walk you through how data flows in our app, using the 'Use Item' action as an example. It starts with a user action — the user taps the Use button on the Item Detail screen and fills in the quantity and destination location. This triggers a call to the ViewModel's useItem function. The ViewModel, which was injected by Hilt, first checks if the logged-in user's role has permission to check out items. If not, it sets an error message. If allowed, it validates the quantity against available stock. Then it calls the Repository — also injected by Hilt — which performs three operations: it updates the item's in-use quantity in Room, inserts an ItemUsageRecord that records where the item is going and who checked it out, and inserts an ItemHistory entry for the audit trail. Because Room returns Flow, the database change automatically emits a new value. The StateFlow in the ViewModel picks up this change, and because the Compose UI is collecting this flow with collectAsStateWithLifecycle, the screen recomposes to show the updated quantities. The entire chain — from user tap to UI update — is reactive and automatic. Hilt made this possible by wiring the ViewModel to the Repository to the DAO at compile time. Now, Jhon Clarence and Mark Jacob will give a live demonstration."

---

### SLIDE 14 - Demonstration
**Speakers: Jhon Clarence B. Rulona + Mark Jacob S. Allero**

**Jhon Clarence:**
"Let us give you a live demonstration of SmartStock+. I will launch the app now."

**Mark Jacob:**
"Here is our Splash Screen with the animated logo. Notice it transitions to the Login screen — not the Dashboard. The bottom navigation is hidden here because the user must authenticate first."

**Jhon Clarence:**
"I will log in as Admin using the default credentials. And now we are on the Dashboard with our summary cards. Let me navigate to the Inventory List. I will search for an item — watch how the results update in real time as I type. I can filter by category and status using these chips."

**Mark Jacob:**
"Let me add a new item. I will fill in the name, select a category — and watch this: I can tap the plus icon to add a custom category. Now I will take a photo using the camera button. I will set the quantity and condition, and save."

**Jhon Clarence:**
"The item appears immediately in the list. Let me tap on it — here is the detail screen showing the photo we just captured. Now I will tap Use — notice the dialog asks for a destination location, not the storage location. I will enter 'Faculty Room' and check out 2 units. The quantities update instantly."

**Mark Jacob:**
"Now I will return one unit. The return dialog asks for a reason — I will enter 'Task completed.' And the available quantity goes back up. Let me also update the condition — tapping Condition gives us a quick modal with New, Good, Fair, and Poor options."

**Jhon Clarence:**
"Now let me log out and log in as Staff. Notice the differences — Staff cannot add or delete items, but can use, return, and update conditions. And all of this works completely offline — the entire app runs on the local Room database. Thank you."

---

### SLIDE 15 - Key Achievements
**Speaker: Aleah A. Cantiga**
*Role: Quality Assurance/Tester*

"As the QA team, we have verified every feature in the current build. Here are our key achievements. First, a working Room database at version 7 with six entities and a complete migration chain from version 1 through 7 — all migrations preserve existing data. Second, clean MVVM architecture with Hilt dependency injection using a KSP-based approach. We successfully resolved the AGP 9.0.1 compatibility issue without compromising functionality. Third, a full offline-first design — every feature works without internet. Fourth, role-based authentication with an Admin and Staff login system, where each role has six granular permissions controlling what they can see and do. Fifth, a complete item usage lifecycle — checkout with destination tracking and return with a reason field. Sixth, image capture via camera and gallery with Coil image loading. Seventh, barcode and QR code scanning integrated with inventory lookup. Eighth, an adaptive UI that switches between bottom navigation in portrait and a navigation rail in landscape mode. Ninth, dark mode support with Material Design 3 dynamic theming. And tenth, nine Compose screens with fully reactive StateFlow-driven updates. Every MCO 1 requirement has been met and exceeded. Now, Angel Mae will discuss the challenges we encountered."

---

### SLIDE 16 - Challenges Encountered
**Speaker: Angel Mae S. Dura**
*Role: Quality Assurance/Tester*

"During development, we encountered several significant challenges. The first and most impactful was the Hilt and AGP 9.0.1 incompatibility. The Hilt Gradle plugin relied on an API called BaseExtension that was removed in AGP 9.0.1. Our back-end team resolved this by using a KSP-only approach — we apply KSP directly and have our Application class extend a generated Hilt base class instead of using the plugin's bytecode transformation. The second challenge was Room migration complexity. We went through seven schema versions. Each migration required careful SQL ALTER TABLE statements to add new columns and tables while preserving all existing user data. For example, migration 6 to 7 added the imageUri column and the item_usage_records table. Third, we encountered an issue with Flow-based data inside coroutines. Using collect() on a Room Flow inside viewModelScope.launch would suspend indefinitely. We fixed this by using the .first() operator for one-shot queries where we only need the current value. Fourth, combining multiple reactive streams — search, filter, sort, and role state — into a single output required careful use of Kotlin's combine operator to avoid race conditions. Fifth, camera integration required setting up a FileProvider, handling runtime permissions, and configuring content URIs correctly. Now, Rynel Jay will discuss our plans for the final term."

---

### SLIDE 17 - Preparation for Final Term (MCO 2)
**Speaker: Rynel Jay V. Vallejos**
*Role: Document Specialist*

"For MCO 2, we have a clear roadmap built on the solid foundation we have demonstrated today. Our top priority is cloud synchronization using Supabase. Our Repository already abstracts the data layer, so we will add a remote data source alongside the existing Room DAO — the ViewModel layer will not need to change. Second, we will implement a dedicated usage tracking screen. This responds directly to our instructor's recommendation — Staff users need a screen to see all checked-out items, where they are located, and who checked them out. This will support monthly and annual reporting with date filters for maintenance reports. Third, we will integrate WorkManager for reliable background sync. Even if the app is closed, pending changes will sync to the cloud when connectivity is restored. Fourth, we will enhance the barcode scanning flow with auto-match and potentially batch scanning support. And fifth, we will polish the UI with two-pane empty state improvements and Compose animations. All of the instructor's remaining recommendations will be fully addressed during this phase. Now, Jasper will close our presentation."

---

### SLIDE 18 - Conclusion
**Speaker: Jasper Keith P. Teorica**
*Role: Project Leader*

"That concludes our MCO 1 midterm presentation for SmartStock+. To summarize what we have delivered — a fully functional offline-first inventory management app with clean MVVM architecture and Hilt dependency injection. A Room database at version 7 with six entities, over 20 DAO operations, and a complete migration chain. Nine Jetpack Compose screens with Material Design 3. Role-based authentication with granular permissions. Item usage tracking with destination locations and return reasons. Image capture and barcode scanning. And an adaptive layout that works across phones and tablets. Our architecture is designed for scalability — the Repository pattern means we can plug in cloud sync for MCO 2 without restructuring the app. We are confident in our progress and ready for the final term. We are now open for any questions. Thank you."

---

## Notes for All Speakers

1. **Pace:** Each speaker has roughly 1-2 minutes per slide. Do not rush.
2. **Face the audience**, not the screen. Refer to the slide only to point at specific items.
3. **Transitions:** Each speaker explicitly names the next speaker when handing off.
4. **Technical terms:** When mentioning StateFlow, Room, DAO, Hilt, MVVM — briefly explain what they do if the audience seems unfamiliar.
5. **Rehearse the live demo** at least twice before the actual presentation.
6. **Demo backup:** If the live demo has technical issues, have screenshots ready as backup slides.
7. **Q&A:** All members should be prepared to answer questions related to their slide and their role area.
