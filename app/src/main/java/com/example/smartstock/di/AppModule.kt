package com.example.smartstock.di

import android.content.Context
import com.example.smartstock.core.network.ConnectivityObserver
import com.example.smartstock.core.network.NetworkConnectivityObserver
import com.example.smartstock.core.preferences.AppPreferences
import com.example.smartstock.core.sync.CloudSyncDataSource
import com.example.smartstock.core.sync.SupabaseCloudSyncDataSource
import com.example.smartstock.core.sync.SyncManager
import com.example.smartstock.data.AppDatabase
import com.example.smartstock.data.cloud.SupabaseRemoteDataSource
import com.example.smartstock.data.dao.InventoryDao
import com.example.smartstock.data.repository.InventoryRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

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
    fun provideSyncManager(
        @ApplicationContext context: Context,
        preferences: AppPreferences
    ): SyncManager {
        return SyncManager(context, preferences)
    }

    @Provides
    @Singleton
    fun provideCloudSyncDataSource(
        remote: SupabaseRemoteDataSource,
        syncManager: SyncManager,
        preferences: AppPreferences,
        connectivityObserver: ConnectivityObserver
    ): CloudSyncDataSource {
        return SupabaseCloudSyncDataSource(
            remote = remote,
            syncManager = syncManager,
            preferences = preferences,
            connectivityObserver = connectivityObserver
        )
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
