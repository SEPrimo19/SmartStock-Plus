package com.example.smartstock.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.smartstock.data.dao.InventoryDao
import com.example.smartstock.data.entity.AssetStatusEntity
import com.example.smartstock.data.entity.CategoryEntity
import com.example.smartstock.data.entity.InventoryItem
import com.example.smartstock.data.entity.ItemHistory
import com.example.smartstock.data.entity.ItemUsageRecord
import com.example.smartstock.data.entity.LocalUser
import com.example.smartstock.data.entity.LinkedBarcode
import com.example.smartstock.data.entity.PendingSyncEntity

@Database(
    entities = [
        InventoryItem::class,
        ItemHistory::class,
        CategoryEntity::class,
        AssetStatusEntity::class,
        LocalUser::class,
        ItemUsageRecord::class,
        PendingSyncEntity::class,
        LinkedBarcode::class
    ],
    version = 10,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun inventoryDao(): InventoryDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "smartstock_database"
                )
                .addMigrations(MIGRATION_1_2)
                .addMigrations(MIGRATION_2_3)
                .addMigrations(MIGRATION_3_4)
                .addMigrations(MIGRATION_4_5)
                .addMigrations(MIGRATION_5_6)
                .addMigrations(MIGRATION_6_7)
                .addMigrations(MIGRATION_7_8)
                .addMigrations(MIGRATION_8_9)
                .addMigrations(MIGRATION_9_10)
                .build()
                INSTANCE = instance
                instance
            }
        }

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS item_history_new (
                        historyId INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        itemId INTEGER NOT NULL,
                        action TEXT NOT NULL,
                        details TEXT NOT NULL,
                        timestamp INTEGER NOT NULL,
                        FOREIGN KEY(itemId) REFERENCES inventory_items(id) ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    INSERT INTO item_history_new (historyId, itemId, action, details, timestamp)
                    SELECT historyId, itemId, action, details, timestamp
                    FROM item_history
                    WHERE itemId IN (SELECT id FROM inventory_items)
                    """.trimIndent()
                )
                db.execSQL("DROP TABLE item_history")
                db.execSQL("ALTER TABLE item_history_new RENAME TO item_history")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_item_history_itemId ON item_history(itemId)")
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE inventory_items ADD COLUMN createdAt INTEGER NOT NULL DEFAULT 0"
                )
                db.execSQL(
                    "UPDATE inventory_items SET createdAt = lastUpdated WHERE createdAt = 0"
                )

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS categories (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        name TEXT NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_categories_name ON categories(name)")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS asset_statuses (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        name TEXT NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS index_asset_statuses_name ON asset_statuses(name)"
                )

                db.execSQL(
                    """
                    INSERT OR IGNORE INTO categories(name)
                    VALUES ('Equipment'), ('Tools'), ('Supplies')
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    INSERT OR IGNORE INTO asset_statuses(name)
                    VALUES ('Available'), ('In-Use'), ('Damaged'), ('Retired')
                    """.trimIndent()
                )
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE inventory_items ADD COLUMN inUseQuantity INTEGER NOT NULL DEFAULT 0"
                )
                db.execSQL(
                    """
                    UPDATE inventory_items
                    SET inUseQuantity = CASE
                        WHEN status = 'In-Use' THEN quantity
                        ELSE 0
                    END
                    """.trimIndent()
                )
            }
        }

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE inventory_items ADD COLUMN assetCode TEXT NOT NULL DEFAULT ''"
                )
                db.execSQL(
                    """
                    UPDATE inventory_items
                    SET assetCode = 'SS-' || printf('%06d', id)
                    WHERE assetCode = ''
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS index_inventory_items_assetCode ON inventory_items(assetCode)"
                )
            }
        }

        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS local_users (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        name TEXT NOT NULL,
                        email TEXT NOT NULL,
                        role TEXT NOT NULL,
                        isActive INTEGER NOT NULL DEFAULT 1
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS index_local_users_email ON local_users(email)"
                )
                db.execSQL(
                    """
                    INSERT OR IGNORE INTO local_users(name, email, role, isActive)
                    VALUES
                    ('Admin User', 'admin@smartstock.local', 'Admin', 1),
                    ('Staff User', 'staff@smartstock.local', 'Staff', 1)
                    """.trimIndent()
                )
            }
        }

        private val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS pending_sync (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        operation TEXT NOT NULL,
                        entityId INTEGER NOT NULL,
                        payload TEXT NOT NULL,
                        createdAt INTEGER NOT NULL,
                        retryCount INTEGER NOT NULL DEFAULT 0
                    )
                    """.trimIndent()
                )
            }
        }

        private val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS linked_barcodes (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        itemId INTEGER NOT NULL,
                        barcodeValue TEXT NOT NULL,
                        label TEXT NOT NULL DEFAULT '',
                        linkedAt INTEGER NOT NULL,
                        FOREIGN KEY(itemId) REFERENCES inventory_items(id) ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS index_linked_barcodes_barcodeValue ON linked_barcodes(barcodeValue)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_linked_barcodes_itemId ON linked_barcodes(itemId)"
                )
                db.execSQL(
                    "ALTER TABLE item_usage_records ADD COLUMN barcodeId INTEGER DEFAULT NULL"
                )
            }
        }

        private val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Adds cloudId / updatedAt / deletedAt to every domain table so
                // the bidirectional Supabase sync (Phase 6) can use last-write-
                // wins on updatedAt and soft-delete via deletedAt. cloudId is
                // the server-side UUID; null means the row hasn't been pushed
                // yet. Backfill updatedAt from each table's existing timestamp
                // where one exists so the first sync pushes everything.
                val tablesWithTimestamp = listOf(
                    "inventory_items"      to "lastUpdated",
                    "item_history"         to "timestamp",
                    "item_usage_records"   to "checkedOutAt",
                    "linked_barcodes"      to "linkedAt"
                )
                val tablesWithoutTimestamp = listOf("categories", "asset_statuses")
                val now = System.currentTimeMillis()

                for ((table, _) in tablesWithTimestamp) {
                    db.execSQL("ALTER TABLE $table ADD COLUMN cloudId TEXT DEFAULT NULL")
                    db.execSQL("ALTER TABLE $table ADD COLUMN updatedAt INTEGER NOT NULL DEFAULT 0")
                    db.execSQL("ALTER TABLE $table ADD COLUMN deletedAt INTEGER DEFAULT NULL")
                }
                for (table in tablesWithoutTimestamp) {
                    db.execSQL("ALTER TABLE $table ADD COLUMN cloudId TEXT DEFAULT NULL")
                    db.execSQL("ALTER TABLE $table ADD COLUMN updatedAt INTEGER NOT NULL DEFAULT 0")
                    db.execSQL("ALTER TABLE $table ADD COLUMN deletedAt INTEGER DEFAULT NULL")
                }

                for ((table, src) in tablesWithTimestamp) {
                    db.execSQL("UPDATE $table SET updatedAt = $src WHERE updatedAt = 0")
                }
                for (table in tablesWithoutTimestamp) {
                    db.execSQL("UPDATE $table SET updatedAt = $now WHERE updatedAt = 0")
                }

                val allTables = tablesWithTimestamp.map { it.first } + tablesWithoutTimestamp
                for (table in allTables) {
                    db.execSQL(
                        "CREATE UNIQUE INDEX IF NOT EXISTS index_${table}_cloudId ON $table(cloudId)"
                    )
                }
            }
        }

        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Add imageUri column to inventory_items
                db.execSQL(
                    "ALTER TABLE inventory_items ADD COLUMN imageUri TEXT DEFAULT NULL"
                )

                // Add password column to local_users
                db.execSQL(
                    "ALTER TABLE local_users ADD COLUMN password TEXT NOT NULL DEFAULT ''"
                )

                // Set default passwords for existing seed users
                db.execSQL(
                    "UPDATE local_users SET password = 'admin123' WHERE email = 'admin@smartstock.local'"
                )
                db.execSQL(
                    "UPDATE local_users SET password = 'staff123' WHERE email = 'staff@smartstock.local'"
                )

                // Create item_usage_records table
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS item_usage_records (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        itemId INTEGER NOT NULL,
                        quantity INTEGER NOT NULL,
                        location TEXT NOT NULL,
                        usedBy TEXT NOT NULL,
                        checkedOutAt INTEGER NOT NULL,
                        returnedAt INTEGER DEFAULT NULL,
                        returnReason TEXT DEFAULT NULL,
                        status TEXT NOT NULL DEFAULT 'Active',
                        FOREIGN KEY(itemId) REFERENCES inventory_items(id) ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_item_usage_records_itemId ON item_usage_records(itemId)"
                )
            }
        }
    }
}
