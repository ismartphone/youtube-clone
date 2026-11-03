package com.youtubeclone.app.data.di

import com.youtubeclone.app.BuildConfig
import com.youtubeclone.app.data.analytics.PostHogAnalytics
import com.youtubeclone.app.data.realtime.LiveVideoClient
import com.youtubeclone.app.data.remote.AuthApi
import com.youtubeclone.app.data.remote.AuthInterceptor
import com.youtubeclone.app.data.remote.CategoryApi
import com.youtubeclone.app.data.remote.ChannelApi
import com.youtubeclone.app.data.remote.CommentApi
import com.youtubeclone.app.data.remote.PremiumApi
import com.youtubeclone.app.data.remote.RefreshApi
import com.youtubeclone.app.data.remote.TokenAuthenticator
import com.youtubeclone.app.data.remote.VideoApi
import com.youtubeclone.app.data.repository.AuthRepositoryImpl
import com.youtubeclone.app.data.repository.CategoryRepositoryImpl
import com.youtubeclone.app.data.repository.ChannelRepositoryImpl
import com.youtubeclone.app.data.repository.CommentRepositoryImpl
import com.youtubeclone.app.data.repository.PreferencesRepositoryImpl
import com.youtubeclone.app.data.repository.PremiumRepositoryImpl
import com.youtubeclone.app.data.repository.VideoRepositoryImpl
import com.youtubeclone.app.domain.analytics.Analytics
import com.youtubeclone.app.domain.repository.AuthRepository
import com.youtubeclone.app.domain.repository.CategoryRepository
import com.youtubeclone.app.domain.repository.ChannelRepository
import com.youtubeclone.app.domain.repository.CommentRepository
import com.youtubeclone.app.domain.repository.LiveVideoRepository
import com.youtubeclone.app.domain.repository.PreferencesRepository
import com.youtubeclone.app.domain.repository.PremiumRepository
import com.youtubeclone.app.domain.repository.VideoRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
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
    fun provideOkHttpClient(
        authInterceptor: AuthInterceptor,
        tokenAuthenticator: TokenAuthenticator,
    ): OkHttpClient = OkHttpClient.Builder()
        .addInterceptor(authInterceptor)
        .authenticator(tokenAuthenticator)
        .build()

    @Provides
    @Singleton
    fun provideRetrofit(json: Json, client: OkHttpClient): Retrofit = Retrofit.Builder()
        .baseUrl(BuildConfig.API_BASE_URL)
        .client(client)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()

    @Provides
    @Singleton
    fun provideVideoApi(retrofit: Retrofit): VideoApi = retrofit.create(VideoApi::class.java)

    @Provides
    @Singleton
    fun provideAuthApi(retrofit: Retrofit): AuthApi = retrofit.create(AuthApi::class.java)

    @Provides
    @Singleton
    fun provideChannelApi(retrofit: Retrofit): ChannelApi = retrofit.create(ChannelApi::class.java)

    @Provides
    @Singleton
    fun provideCommentApi(retrofit: Retrofit): CommentApi = retrofit.create(CommentApi::class.java)

    @Provides
    @Singleton
    fun providePremiumApi(retrofit: Retrofit): PremiumApi = retrofit.create(PremiumApi::class.java)

    @Provides
    @Singleton
    fun provideCategoryApi(retrofit: Retrofit): CategoryApi = retrofit.create(CategoryApi::class.java)

    // Client à part, sans intercepteur ni authenticator : voir RefreshApi.
    @Provides
    @Singleton
    fun provideRefreshApi(json: Json): RefreshApi = Retrofit.Builder()
        .baseUrl(BuildConfig.API_BASE_URL)
        .client(OkHttpClient())
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()
        .create(RefreshApi::class.java)
}

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindVideoRepository(impl: VideoRepositoryImpl): VideoRepository

    @Binds
    @Singleton
    abstract fun bindAuthRepository(impl: AuthRepositoryImpl): AuthRepository

    @Binds
    @Singleton
    abstract fun bindChannelRepository(impl: ChannelRepositoryImpl): ChannelRepository

    @Binds
    @Singleton
    abstract fun bindCommentRepository(impl: CommentRepositoryImpl): CommentRepository

    @Binds
    @Singleton
    abstract fun bindLiveVideoRepository(impl: LiveVideoClient): LiveVideoRepository

    @Binds
    @Singleton
    abstract fun bindCategoryRepository(impl: CategoryRepositoryImpl): CategoryRepository

    @Binds
    @Singleton
    abstract fun bindPreferencesRepository(impl: PreferencesRepositoryImpl): PreferencesRepository

    @Binds
    @Singleton
    abstract fun bindPremiumRepository(impl: PremiumRepositoryImpl): PremiumRepository

    @Binds
    @Singleton
    abstract fun bindAnalytics(impl: PostHogAnalytics): Analytics
}
