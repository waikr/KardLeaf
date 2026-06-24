package com.kangle.kardleaf.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [NoteEntity::class, LabelEntity::class, NoteHistoryEntity::class, PrivacyNoteEntity::class, NoteRemarkEntity::class],
    version = 14,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun noteDao(): NoteDao

    abstract fun labelDao(): LabelDao

    abstract fun noteHistoryDao(): NoteHistoryDao

    abstract fun privacyNoteDao(): PrivacyNoteDao

    abstract fun noteRemarkDao(): NoteRemarkDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance =
                    Room.databaseBuilder(
                        context.applicationContext,
                        AppDatabase::class.java,
                        "kardleaf_database",
                    )
                        .addMigrations(MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12, MIGRATION_12_13, MIGRATION_13_14)
                        .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
