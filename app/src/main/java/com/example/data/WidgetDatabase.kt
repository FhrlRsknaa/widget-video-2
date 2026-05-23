package com.example.data

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

@Entity(tableName = "video_widget_configs")
data class VideoWidgetConfig(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val title: String,
    val localFolderName: String, // Name of subfolder in context.filesDir where frames are stored
    val frameCount: Int,
    val fps: Int, // Playback speed (frames per second)
    val videoUriString: String, // Original selected video URI or file path
    val isLooping: Boolean = true,
    val creationTime: Long = System.currentTimeMillis()
)

@Entity(tableName = "widget_bindings")
data class WidgetBinding(
    @PrimaryKey val appWidgetId: Int, // The Android home screen widget instance ID
    val configId: Int // The target VideoWidgetConfig ID to play
)

@Dao
interface VideoWidgetDao {
    @Query("SELECT * FROM video_widget_configs ORDER BY creationTime DESC")
    fun getAllConfigs(): Flow<List<VideoWidgetConfig>>

    @Query("SELECT * FROM video_widget_configs WHERE id = :id")
    suspend fun getConfigById(id: Int): VideoWidgetConfig?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertConfig(config: VideoWidgetConfig): Long

    @Query("DELETE FROM video_widget_configs WHERE id = :id")
    suspend fun deleteConfigById(id: Int)

    @Query("SELECT * FROM widget_bindings WHERE appWidgetId = :appWidgetId")
    suspend fun getBindingForWidget(appWidgetId: Int): WidgetBinding?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBinding(binding: WidgetBinding)

    @Query("DELETE FROM widget_bindings WHERE appWidgetId = :appWidgetId")
    suspend fun deleteBinding(appWidgetId: Int)

    @Query("SELECT * FROM widget_bindings")
    suspend fun getAllBindings(): List<WidgetBinding>
}

@Database(entities = [VideoWidgetConfig::class, WidgetBinding::class], version = 1, exportSchema = false)
abstract class WidgetDatabase : RoomDatabase() {
    abstract fun videoWidgetDao(): VideoWidgetDao

    companion object {
        @Volatile
        private var INSTANCE: WidgetDatabase? = null

        fun getDatabase(context: Context): WidgetDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    WidgetDatabase::class.java,
                    "widget_database"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
