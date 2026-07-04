package com.pulsefin.core.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * Room is the single source of truth: Jellyfin API responses are mirrored here so the UI
 * reads from local storage for instant response and offline capability. The schema starts
 * minimal (songs) and grows with the feature roadmap.
 */
@Entity(tableName = "songs")
data class SongEntity(
    @PrimaryKey val id: String,
    val title: String,
    val albumName: String,
    val artistName: String,
    val durationMs: Long,
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

@Database(entities = [SongEntity::class], version = 1, exportSchema = false)
abstract class PulseFinDatabase : RoomDatabase() {
    abstract fun songDao(): SongDao

    companion object {
        fun build(context: Context): PulseFinDatabase =
            Room.databaseBuilder(
                context.applicationContext,
                PulseFinDatabase::class.java,
                "pulsefin.db",
            ).build()
    }
}
