package com.youtubeclone.app.playback.download

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.cache.NoOpCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.exoplayer.offline.DownloadManager
import androidx.media3.exoplayer.scheduler.Requirements
import androidx.room.Room
import com.youtubeclone.app.data.local.DownloadDao
import com.youtubeclone.app.data.local.DownloadsDatabase
import com.youtubeclone.app.data.local.MIGRATION_DOWNLOADS_1_2
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.io.File
import java.util.concurrent.Executors
import javax.inject.Singleton

/**
 * Téléchargements hors ligne (ADR-019) : le DownloadManager de Media3 écrit dans un cache dédié,
 * dans le stockage privé de l'application, sans éviction automatique. Rien n'est visible depuis la
 * galerie ni un explorateur de fichiers.
 */
@OptIn(UnstableApi::class)
@Module
@InstallIn(SingletonComponent::class)
object DownloadModule {

    @Provides
    @Singleton
    fun provideDatabaseProvider(@ApplicationContext context: Context): StandaloneDatabaseProvider =
        StandaloneDatabaseProvider(context)

    @Provides
    @Singleton
    fun provideDownloadCache(
        @ApplicationContext context: Context,
        databaseProvider: StandaloneDatabaseProvider,
    ): SimpleCache = SimpleCache(File(context.filesDir, DOWNLOADS_FOLDER), NoOpCacheEvictor(), databaseProvider)

    @Provides
    @Singleton
    fun provideDownloadManager(
        @ApplicationContext context: Context,
        databaseProvider: StandaloneDatabaseProvider,
        cache: SimpleCache,
    ): DownloadManager = DownloadManager(
        context,
        databaseProvider,
        cache,
        DefaultHttpDataSource.Factory(),
        Executors.newFixedThreadPool(DOWNLOAD_THREADS),
    ).apply {
        // Un téléchargement à la fois : le premier fini est le premier lisible.
        maxParallelDownloads = 1
        // Pas d'exigence d'un réseau « validé » (accès à Internet) : le serveur peut être joignable
        // sans lui, en réseau local. Sans réseau du tout, le téléchargement échoue et l'écran le dit.
        requirements = Requirements(0)
    }

    @Provides
    @Singleton
    fun provideDownloadsDatabase(@ApplicationContext context: Context): DownloadsDatabase =
        Room.databaseBuilder(context, DownloadsDatabase::class.java, "downloads.db")
            .addMigrations(MIGRATION_DOWNLOADS_1_2)
            .build()

    @Provides
    fun provideDownloadDao(database: DownloadsDatabase): DownloadDao = database.downloadDao()

    private const val DOWNLOADS_FOLDER = "downloads"
    private const val DOWNLOAD_THREADS = 2
}
