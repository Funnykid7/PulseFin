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
    val streamUrl: String?,
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

    @Query("DELETE FROM playlists")
    suspend fun clear()

    @Transaction
    suspend fun replaceAll(playlists: List<PlaylistEntity>) {
        clear()
        upsertAll(playlists)
    }
}

@Database(
    entities = [
        SongEntity::class, AlbumEntity::class, ArtistEntity::class, RecentSearchEntity::class,
        PlaylistEntity::class,
    ],
    version = 6,
    exportSchema = false,
)
abstract class PulseFinDatabase : RoomDatabase() {
    abstract fun songDao(): SongDao
    abstract fun albumDao(): AlbumDao
    abstract fun artistDao(): ArtistDao
    abstract fun recentSearchDao(): RecentSearchDao
    abstract fun playlistDao(): PlaylistDao

    companion object {
        fun build(context: Context): PulseFinDatabase =
            Room.databaseBuilder(
                context.applicationContext,
                PulseFinDatabase::class.java,
                "pulsefin.db",
            )
                .fallbackToDestructiveMigration(dropAllTables = true)
                .build()
    }
}
