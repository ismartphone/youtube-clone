package com.youtubeclone.app.data.local

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

/**
 * Vidéo téléchargée pour la lecture hors ligne (ADR-019) : tout ce que l'écran « Téléchargements »
 * et le lecteur hors ligne affichent sans réseau. Les fichiers (vidéo, miniature, sous-titres) sont
 * dans le stockage privé de l'application, jamais dans la galerie.
 *
 * Une ligne par compte et par vidéo (ADR-022) : le téléchargement appartient au compte qui l'a
 * fait. Si deux comptes téléchargent la même vidéo, le fichier est partagé (même identifiant
 * Media3) et n'est retiré que lorsque plus aucune ligne ne le référence.
 */
@Entity(tableName = "downloaded_videos", primaryKeys = ["userId", "videoId"])
data class DownloadedVideoEntity(
    /** Compte propriétaire ; vide pour une ligne d'avant l'ADR-022, pas encore attribuée. */
    val userId: String,
    val videoId: String,
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
    @Query("SELECT * FROM downloaded_videos WHERE userId = :userId ORDER BY createdAt DESC")
    fun observeAll(userId: String): Flow<List<DownloadedVideoEntity>>

    @Query("SELECT * FROM downloaded_videos WHERE userId = :userId")
    suspend fun getAll(userId: String): List<DownloadedVideoEntity>

    @Query("SELECT * FROM downloaded_videos WHERE userId = :userId AND videoId = :videoId")
    suspend fun get(userId: String, videoId: String): DownloadedVideoEntity?

    @Query("SELECT COUNT(*) FROM downloaded_videos WHERE userId = :userId")
    suspend fun count(userId: String): Int

    /** Lignes, tous comptes confondus, qui référencent le fichier de [videoId]. */
    @Query("SELECT COUNT(*) FROM downloaded_videos WHERE videoId = :videoId")
    suspend fun countOwners(videoId: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: DownloadedVideoEntity)

    @Query("UPDATE downloaded_videos SET expiresAt = :expiresAt, validatedAt = :validatedAt WHERE userId = :userId AND videoId IN (:videoIds)")
    suspend fun revalidate(userId: String, videoIds: List<String>, expiresAt: Long, validatedAt: Long)

    @Query("DELETE FROM downloaded_videos WHERE userId = :userId AND videoId = :videoId")
    suspend fun delete(userId: String, videoId: String)

    /** Lignes d'avant l'ADR-022, sans compte : attribuées au compte actif (décision 4). */
    @Query("UPDATE downloaded_videos SET userId = :userId WHERE userId = ''")
    suspend fun claimUnowned(userId: String)
}

@Database(entities = [DownloadedVideoEntity::class], version = 2, exportSchema = false)
abstract class DownloadsDatabase : RoomDatabase() {
    abstract fun downloadDao(): DownloadDao
}

/**
 * Version 2 (ADR-022) : colonne userId et clé primaire (userId, videoId). Les lignes existantes
 * reçoivent un compte vide ; DownloadsSync les attribue au compte actif au démarrage, ou les
 * supprime s'il n'y a pas de session.
 */
val MIGRATION_DOWNLOADS_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE downloaded_videos_new (
                userId TEXT NOT NULL, videoId TEXT NOT NULL, title TEXT NOT NULL,
                channelName TEXT NOT NULL, durationSeconds INTEGER NOT NULL, thumbnailPath TEXT,
                subtitlePath TEXT, sizeBytes INTEGER, expiresAt INTEGER NOT NULL,
                validatedAt INTEGER NOT NULL, createdAt INTEGER NOT NULL,
                PRIMARY KEY(userId, videoId))""",
        )
        db.execSQL(
            """INSERT INTO downloaded_videos_new SELECT '', videoId, title, channelName,
                durationSeconds, thumbnailPath, subtitlePath, sizeBytes, expiresAt, validatedAt,
                createdAt FROM downloaded_videos""",
        )
        db.execSQL("DROP TABLE downloaded_videos")
        db.execSQL("ALTER TABLE downloaded_videos_new RENAME TO downloaded_videos")
    }
}
