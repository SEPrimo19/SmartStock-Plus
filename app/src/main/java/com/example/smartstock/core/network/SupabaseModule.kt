package com.example.smartstock.core.network

import com.example.smartstock.BuildConfig
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.annotations.SupabaseInternal
import io.github.jan.supabase.functions.Functions
import io.github.jan.supabase.gotrue.Auth
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.storage.Storage
import io.ktor.client.plugins.HttpTimeout
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object SupabaseModule {

    @OptIn(SupabaseInternal::class)
    @Provides
    @Singleton
    fun provideSupabaseClient(): SupabaseClient {
        val url = BuildConfig.SUPABASE_URL
        val anonKey = BuildConfig.SUPABASE_ANON_KEY
        check(url.isNotBlank() && anonKey.isNotBlank()) {
            "Supabase credentials are missing. Add SUPABASE_URL and " +
                "SUPABASE_ANON_KEY to local.properties and rebuild."
        }
        return createSupabaseClient(supabaseUrl = url, supabaseKey = anonKey) {
            install(Auth)
            install(Postgrest)
            install(Functions)
            install(Storage)
            // Default ktor request timeout is 10s, which times out on the
            // very first request after a Supabase schema reload while
            // PostgREST rebuilds its schema cache. 30s gives the cache
            // rebuild + cold-start RLS policy compilation enough headroom
            // and still keeps the user from waiting forever on a dead net.
            httpConfig {
                install(HttpTimeout) {
                    requestTimeoutMillis = 30_000
                    connectTimeoutMillis = 15_000
                    socketTimeoutMillis = 30_000
                }
            }
        }
    }
}
