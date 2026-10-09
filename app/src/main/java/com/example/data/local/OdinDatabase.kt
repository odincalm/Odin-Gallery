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
        UserPrefEntity::class,
        TelegramBackupItem::class
    ],
    version = 4,
    exportSchema = false
)
abstract class OdinDatabase : RoomDatabase() {
    abstract fun favoriteDao(): FavoriteDao
    abstract fun albumDao(): AlbumDao
    abstract fun deletedMediaDao(): DeletedMediaDao
    abstract fun hiddenMediaDao(): HiddenMediaDao
    abstract fun playbackDao(): PlaybackDao
    abstract fun userPrefDao(): UserPrefDao
    abstract fun telegramBackupDao(): TelegramBackupDao

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

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS telegram_backup_items (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        localMediaId INTEGER NOT NULL,
                        uriString TEXT,
                        filePath TEXT,
                        fileName TEXT NOT NULL,
                        mediaType TEXT NOT NULL,
                        sizeBytes INTEGER NOT NULL,
                        fileHash TEXT NOT NULL,
                        telegramMessageId INTEGER NOT NULL DEFAULT 0,
                        telegramFileId INTEGER NOT NULL DEFAULT 0,
                        thumbnailFileId INTEGER NOT NULL DEFAULT 0,
                        thumbnailPath TEXT,
                        status TEXT NOT NULL DEFAULT 'PENDING',
                        errorMessage TEXT,
                        retryCount INTEGER NOT NULL DEFAULT 0,
                        dateModified INTEGER NOT NULL DEFAULT 0,
                        width INTEGER NOT NULL DEFAULT 0,
                        height INTEGER NOT NULL DEFAULT 0,
                        duration INTEGER NOT NULL DEFAULT 0,
                        queuedAt INTEGER NOT NULL,
                        completedAt INTEGER
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_telegram_backup_items_localMediaId ON telegram_backup_items (localMediaId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_telegram_backup_items_fileHash ON telegram_backup_items (fileHash)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_telegram_backup_items_telegramMessageId ON telegram_backup_items (telegramMessageId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_telegram_backup_items_status ON telegram_backup_items (status)")
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE telegram_backup_items ADD COLUMN thumbnailFileId INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE telegram_backup_items ADD COLUMN thumbnailPath TEXT")
                db.execSQL("ALTER TABLE telegram_backup_items ADD COLUMN dateModified INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE telegram_backup_items ADD COLUMN width INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE telegram_backup_items ADD COLUMN height INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE telegram_backup_items ADD COLUMN duration INTEGER NOT NULL DEFAULT 0")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_telegram_backup_items_telegramMessageId ON telegram_backup_items (telegramMessageId)")
            }
        }

        fun getInstance(context: Context): OdinDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    OdinDatabase::class.java,
                    "odin_gallery.db"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
