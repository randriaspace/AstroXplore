package com.example.astroxplore.core.network

import com.example.astroxplore.BuildConfig
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.realtime.Realtime
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient {
        return OkHttpClient.Builder()
            .addInterceptor(HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BODY
            })
            .addInterceptor { chain ->
                val request = chain.request()
                val newRequest = if (request.url.host.contains("api.adsabs.harvard.edu")) {
                    request.newBuilder()
                        .addHeader("Authorization", "Bearer ${BuildConfig.NASA_ADS_API_TOKEN}")
                        .build()
                } else {
                    request
                }
                chain.proceed(newRequest)
            }
            .build()
    }

    @Provides
    @Singleton
    fun provideNasaAdsApiService(okHttpClient: OkHttpClient, json: Json): NasaAdsApiService {
        val contentType = "application/json".toMediaType()
        return Retrofit.Builder()
            .baseUrl(NasaAdsApiService.BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory(contentType))
            .build()
            .create(NasaAdsApiService::class.java)
    }

    @Provides
    @Singleton
    fun provideSupabaseClient(): SupabaseClient {
        val url = BuildConfig.SUPABASE_URL.ifBlank { "https://placeholder.supabase.co" }
        val key = BuildConfig.SUPABASE_ANON_KEY.ifBlank { "placeholder-key" }
        return createSupabaseClient(
            supabaseUrl = url,
            supabaseKey = key
        ) {
            install(Auth) {
                // Keep user logged in across restarts
                alwaysAutoRefresh = true
            }
            install(Postgrest)
            install(Realtime)
        }
    }
}
