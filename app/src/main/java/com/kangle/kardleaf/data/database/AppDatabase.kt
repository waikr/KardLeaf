package com.kangle.kardleaf.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.kangle.kardleaf.data.repository.PrefsManager
import com.kangle.kardleaf.data.repository.isVaultDatabaseName

@Database(
    entities = [NoteEntity::class, LabelEntity::class, NoteHistoryEntity::class, PrivacyNoteEntity::class, NoteRemarkEntity::class, TaskEntity::class, TaskGroupEntity::class, NoteLinkEntity::class],
    version = 20,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun noteDao(): NoteDao

    abstract fun labelDao(): LabelDao

    abstract fun noteHistoryDao(): NoteHistoryDao

    abstract fun privacyNoteDao(): PrivacyNoteDao

    abstract fun noteRemarkDao(): NoteRemarkDao

    abstract fun taskDao(): TaskDao

    abstract fun noteLinkDao(): NoteLinkDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null
        private var instanceName: String? = null

        fun getDatabase(context: Context): AppDatabase {
            val databaseName = PrefsManager.activeVaultDatabaseName(context.applicationContext)
            require(isVaultDatabaseName(databaseName)) { "Invalid vault database name" }
            return synchronized(this) {
                if (INSTANCE != null && instanceName == databaseName) return@synchronized INSTANCE!!
                INSTANCE?.close()
                val instance =
                    Room.databaseBuilder(
                        context.applicationContext,
                        AppDatabase::class.java,
                        databaseName,
                    )
                        .addMigrations(MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12, MIGRATION_12_13, MIGRATION_13_14, MIGRATION_14_15, MIGRATION_15_16, MIGRATION_16_17, MIGRATION_17_18, MIGRATION_18_19, MIGRATION_19_20)
                        .build()
                INSTANCE = instance
                instanceName = databaseName
                instance
            }
        }

        fun checkpoint(context: Context) {
            runCatching {
                getDatabase(context).openHelper.writableDatabase
                    .query("PRAGMA wal_checkpoint(FULL)")
                    .close()
            }
        }

        fun closeDatabase() {
            synchronized(this) {
                INSTANCE?.close()
                INSTANCE = null
                instanceName = null
            }
        }

        fun deleteDatabase(
            context: Context,
            databaseName: String,
        ): Boolean {
            require(isVaultDatabaseName(databaseName)) { "Invalid vault database name" }
            synchronized(this) {
                if (instanceName == databaseName) closeDatabase()
                return context.applicationContext.deleteDatabase(databaseName)
            }
        }
    }
}
