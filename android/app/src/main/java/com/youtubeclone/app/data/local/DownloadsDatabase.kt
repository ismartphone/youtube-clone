package com.youtubeclone.app.data.local

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

/**
 * Vidéo téléchargée pour la lecture hors ligne (ADR-019) : tout ce que l'écran « Téléchargements »
 * et le lecteur hors ligne affichent sans réseau. Les fichiers (vidéo, miniature, sous-titres) sont
 * dans le stockage privé de l'application, jamais dans la galerie.
 */
@Entity(tableName = "downloaded_videos")
data class DownloadedVideoEntity(
    @PrimaryKey val videoId: String,
    val title: String,
    val channelName: String,
    val durationSeconds: Int,
    /** Miniature enregistrée dans le stockage privé ; null si elle n'a pas pu l'être. */
    val thumbnailPath: String?,
    /** Sous-titres WebVTT enregistrés avec la vidéo ; null s'il n'y en a pas. */
    val subtitlePath: String?,
    /** Taille annoncée par le serveur, en octets ; null si elle est inconnue. */
    val sizeBytes: Long?,
    /** Fin de l'abonnement Premium : au-delà, la lecture est refusée, même hors ligne. */
    val expiresAt: Long,
    /** Dernière revalidation par le serveur : au-delà de 30 jours, la lecture est refusée. */
    val validatedAt: Long,
    val createdAt: Long,
)

@Dao
interface DownloadDao {
    @Query("SELECT * FROM downloaded_videos ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<DownloadedVideoEntity>>

    @Query("SELECT * FROM downloaded_videos")
    suspend fun getAll(): List<DownloadedVideoEntity>

    @Query("SELECT * FROM downloaded_videos WHERE videoId = :videoId")
    suspend fun get(videoId: String): DownloadedVideoEntity?

    @Query("SELECT COUNT(*) FROM downloaded_videos")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: DownloadedVideoEntity)

    @Query("UPDATE downloaded_videos SET expiresAt = :expiresAt, validatedAt = :validatedAt WHERE videoId IN (:videoIds)")
    suspend fun revalidate(videoIds: List<String>, expiresAt: Long, validatedAt: Long)

    @Query("DELETE FROM downloaded_videos WHERE videoId = :videoId")
    suspend fun delete(videoId: String)
}

@Database(entities = [DownloadedVideoEntity::class], version = 1, exportSchema = false)
abstract class DownloadsDatabase : RoomDatabase() {
    abstract fun downloadDao(): DownloadDao
}
