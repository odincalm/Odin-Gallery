package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        FavoriteEntity::class,
        AlbumEntity::class,
        AlbumMediaEntity::class,
        DeletedMediaEntity::class,
        HiddenMediaEntity::class,
        PlaybackPositionEntity::class,
        UserPrefEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class OdinDatabase : RoomDatabase() {
    abstract fun favoriteDao(): FavoriteDao
    abstract fun albumDao(): AlbumDao
    abstract fun deletedMediaDao(): DeletedMediaDao
    abstract fun hiddenMediaDao(): HiddenMediaDao
    abstract fun playbackDao(): PlaybackDao
    abstract fun userPrefDao(): UserPrefDao

    companion object {
        @Volatile
        private var INSTANCE: OdinDatabase? = null

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Add status column to recently_deleted if not present
                db.execSQL("ALTER TABLE recently_deleted ADD COLUMN status TEXT NOT NULL DEFAULT 'TRASHED'")

                // Create hidden_media table
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS hidden_media (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        originalMediaId INTEGER NOT NULL,
                        uriString TEXT NOT NULL,
                        hiddenAt INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_hidden_media_originalMediaId ON hidden_media (originalMediaId)")
            }
        }

        fun getInstance(context: Context): OdinDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    OdinDatabase::class.java,
                    "odin_gallery.db"
                )
                    .addMigrations(MIGRATION_1_2)
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
