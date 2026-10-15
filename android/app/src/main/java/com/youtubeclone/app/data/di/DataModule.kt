package com.youtubeclone.app.data.di

import com.youtubeclone.app.BuildConfig
import com.youtubeclone.app.data.remote.VideoApi
import com.youtubeclone.app.data.repository.VideoRepositoryImpl
import com.youtubeclone.app.domain.repository.VideoRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        // L'API peut ajouter des champs sans casser les anciennes versions de l'application.
        ignoreUnknownKeys = true
    }

    @Provides
    @Singleton
    fun provideRetrofit(json: Json): Retrofit = Retrofit.Builder()
        .baseUrl(BuildConfig.API_BASE_URL)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()

    @Provides
    @Singleton
    fun provideVideoApi(retrofit: Retrofit): VideoApi = retrofit.create(VideoApi::class.java)
}

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindVideoRepository(impl: VideoRepositoryImpl): VideoRepository
}
