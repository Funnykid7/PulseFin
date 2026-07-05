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

@Dao
interface SongDao {
    @Query("SELECT * FROM songs ORDER BY title")
    fun observeAll(): Flow<List<SongEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(songs: List<SongEntity>)

    @Query("DELETE FROM songs")
    suspend fun clear()
}

@Dao
interface AlbumDao {
    @Query("SELECT * FROM albums ORDER BY name")
    fun observeAll(): Flow<List<AlbumEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(albums: List<AlbumEntity>)

    @Query("DELETE FROM albums")
    suspend fun clear()
}

@Dao
interface ArtistDao {
    @Query("SELECT * FROM artists ORDER BY name")
    fun observeAll(): Flow<List<ArtistEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(artists: List<ArtistEntity>)

    @Query("DELETE FROM artists")
    suspend fun clear()
}

@Database(
    entities = [SongEntity::class, AlbumEntity::class, ArtistEntity::class],
    version = 2,
    exportSchema = false,
)
abstract class PulseFinDatabase : RoomDatabase() {
    abstract fun songDao(): SongDao
    abstract fun albumDao(): AlbumDao
    abstract fun artistDao(): ArtistDao

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
