package com.pulsefin.core.data.local

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow

/**
 * Room is the single source of truth: Jellyfin responses are mirrored here so the UI reads
 * from local storage for instant response and offline capability, and a background refresh
 * keeps it current.
 */
@Entity(tableName = "songs")
data class SongEntity(
    @PrimaryKey val id: String,
    val title: String,
    val albumName: String,
    val artistName: String,
    val durationMs: Long,
    val artworkUrl: String?,
    val isFavorite: Boolean = false,
)

@Entity(tableName = "albums")
data class AlbumEntity(
    @PrimaryKey val id: String,
    val name: String,
    val artistName: String,
    val artworkUrl: String?,
    val year: Int?,
)

@Entity(tableName = "artists")
data class ArtistEntity(
    @PrimaryKey val id: String,
    val name: String,
    val artworkUrl: String?,
)

@Entity(tableName = "recent_searches")
data class RecentSearchEntity(
    @PrimaryKey val query: String,
    val searchedAtMs: Long,
)

@Entity(tableName = "playlists")
data class PlaylistEntity(
    @PrimaryKey val id: String,
    val name: String,
    val artworkUrl: String?,
    val songCount: Int,
    // '|'-joined member art URLs (up to 4) — plain-string column, matching this file's existing
    // hand-rolled entity<->domain mapping style rather than adding a Room TypeConverter.
    val memberArtworkUrls: String = "",
)

@Dao
interface SongDao {
    @Query("SELECT * FROM songs ORDER BY title")
    fun observeAll(): Flow<List<SongEntity>>

    /** IDs of favorited songs, observed so the heart reflects instantly across screens. */
    @Query("SELECT id FROM songs WHERE isFavorite = 1")
    fun observeFavoriteIds(): Flow<List<String>>

    @Query("UPDATE songs SET isFavorite = :favorite WHERE id = :id")
    suspend fun setFavorite(id: String, favorite: Boolean)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(songs: List<SongEntity>)

    @Query("DELETE FROM songs")
    suspend fun clear()

    /** Atomic swap so observers see a single emission (no empty flash between clear and insert). */
    @Transaction
    suspend fun replaceAll(songs: List<SongEntity>) {
        clear()
        upsertAll(songs)
    }
}

@Dao
interface AlbumDao {
    @Query("SELECT * FROM albums ORDER BY name")
    fun observeAll(): Flow<List<AlbumEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(albums: List<AlbumEntity>)

    @Query("DELETE FROM albums")
    suspend fun clear()

    @Transaction
    suspend fun replaceAll(albums: List<AlbumEntity>) {
        clear()
        upsertAll(albums)
    }
}

@Dao
interface ArtistDao {
    @Query("SELECT * FROM artists ORDER BY name")
    fun observeAll(): Flow<List<ArtistEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(artists: List<ArtistEntity>)

    @Query("DELETE FROM artists")
    suspend fun clear()

    @Transaction
    suspend fun replaceAll(artists: List<ArtistEntity>) {
        clear()
        upsertAll(artists)
    }
}

@Dao
interface RecentSearchDao {
    @Query("SELECT * FROM recent_searches ORDER BY searchedAtMs DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<RecentSearchEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: RecentSearchEntity)

    @Query("DELETE FROM recent_searches WHERE query = :query")
    suspend fun delete(query: String)

    @Query("DELETE FROM recent_searches")
    suspend fun clearAll()
}

@Dao
interface PlaylistDao {
    @Query("SELECT * FROM playlists ORDER BY name")
    fun observeAll(): Flow<List<PlaylistEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(playlists: List<PlaylistEntity>)

    @Query("DELETE FROM playlists WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM playlists")
    suspend fun clear()

    @Transaction
    suspend fun replaceAll(playlists: List<PlaylistEntity>) {
        clear()
        upsertAll(playlists)
    }
}

@Entity(tableName = "downloads")
data class DownloadEntity(
    @PrimaryKey val songId: String,
    val title: String,
    val artistName: String,
    val artworkUrl: String?,
    val state: String,
    val progressPercent: Int = 0,
    val bytesDownloaded: Long = 0L,
    val totalBytes: Long = 0L,
    val updatedAtMs: Long = 0L,
)

@Dao
interface DownloadDao {
    @Query("SELECT * FROM downloads ORDER BY updatedAtMs DESC")
    fun observeAll(): Flow<List<DownloadEntity>>

    @Query("SELECT * FROM downloads WHERE songId = :songId")
    fun observeBySongId(songId: String): Flow<DownloadEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: DownloadEntity)

    @Query("DELETE FROM downloads WHERE songId = :songId")
    suspend fun delete(songId: String)

    @Query("DELETE FROM downloads")
    suspend fun clearAll()
}

@Database(
    entities = [
        SongEntity::class, AlbumEntity::class, ArtistEntity::class, RecentSearchEntity::class,
        PlaylistEntity::class, DownloadEntity::class,
    ],
    version = 9,
    exportSchema = true,
)
abstract class PulseFinDatabase : RoomDatabase() {
    abstract fun songDao(): SongDao
    abstract fun albumDao(): AlbumDao
    abstract fun artistDao(): ArtistDao
    abstract fun recentSearchDao(): RecentSearchDao
    abstract fun playlistDao(): PlaylistDao
    abstract fun downloadDao(): DownloadDao

    /**
     * Wipes every locally-mirrored table — used on logout so no trace of the prior user remains.
     * Room's KSP processor only instruments `@Transaction` on `@Dao` methods, not methods on the
     * `@Database` class itself, so this must use the real `withTransaction` API instead of the
     * (silently inert, here) annotation.
     */
    open suspend fun clearAll() = withTransaction {
        songDao().clear()
        albumDao().clear()
        artistDao().clear()
        recentSearchDao().clearAll()
        playlistDao().clear()
        downloadDao().clearAll()
    }

    companion object {
        fun build(context: Context): PulseFinDatabase {
            // This is a single-user local cache (songs/albums/artists/playlists are re-synced
            // from the Jellyfin server; downloaded files survive via Media3's own index, only
            // the DownloadEntity metadata here is rebuilt) rather than a system of record, so a
            // rebuilt-from-scratch cache on a schema bump is a better outcome for users than an
            // unrecoverable crash loop on every relaunch. Applies to all build types.
            //
            // Schemas are now exported to core/data/schemas/ (see build.gradle.kts's
            // room.schemaLocation), so a real Migration can be diffed and added via
            // .addMigrations(Migration(from, to) { db -> ... }) whenever a schema change would
            // lose data users actually care about (e.g. recent_searches, which today is the only
            // table not re-derivable from the server and would be silently dropped by the
            // fallback below). Until such a migration is added, destructive recreation remains
            // the safety net.
            return Room.databaseBuilder(
                context.applicationContext,
                PulseFinDatabase::class.java,
                "pulsefin.db",
            )
                .fallbackToDestructiveMigration(dropAllTables = true)
                .build()
        }
    }
}
