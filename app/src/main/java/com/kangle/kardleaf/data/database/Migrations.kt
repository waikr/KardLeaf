package com.kangle.kardleaf.data.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/*
 * Room 迁移放置处。
 *
 * version = 6 为正式迁移基线：
 *  - 不提供向 version 5 及更早版本的迁移，也不伪造 MIGRATION_5_6。
 *  - 当数据库结构升级到 version 7 时，在此新增 MIGRATION_6_7，并在
 *    AppDatabase.getDatabase 中通过 .addMigrations(MIGRATION_6_7) 注册。
 *  - 基线 schema 由 exportSchema = true 导出到 app/schemas，作为后续
 *    迁移正确性的参照。
 *
 * 当前 MIGRATION_6_7 仅添加索引，不修改表结构、不删除数据：
 *  - notes 表 7 个复合索引，覆盖首页/归档/回收站/文件夹/收藏/标签列表等高频查询
 *  - note_history 表 2 个索引，覆盖按 noteId 查历史版本及按时间排序
 */

val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE INDEX IF NOT EXISTS `index_notes_active_sort`
            ON `notes` (`isTrashed`, `isArchived`, `isPinned`, `lastModifiedMs`)
            """.trimIndent(),
        )

        db.execSQL(
            """
            CREATE INDEX IF NOT EXISTS `index_notes_all_with_archive_sort`
            ON `notes` (`isTrashed`, `isPinned`, `lastModifiedMs`)
            """.trimIndent(),
        )

        db.execSQL(
            """
            CREATE INDEX IF NOT EXISTS `index_notes_trash_sort`
            ON `notes` (`isTrashed`, `lastModifiedMs`)
            """.trimIndent(),
        )

        db.execSQL(
            """
            CREATE INDEX IF NOT EXISTS `index_notes_archive_sort`
            ON `notes` (`isArchived`, `isTrashed`, `lastModifiedMs`)
            """.trimIndent(),
        )

        db.execSQL(
            """
            CREATE INDEX IF NOT EXISTS `index_notes_folder_sort`
            ON `notes` (`folder`, `isTrashed`, `isPinned`, `lastModifiedMs`)
            """.trimIndent(),
        )

        db.execSQL(
            """
            CREATE INDEX IF NOT EXISTS `index_notes_favorite_sort`
            ON `notes` (`isFavorite`, `isTrashed`, `lastModifiedMs`)
            """.trimIndent(),
        )

        db.execSQL(
            """
            CREATE INDEX IF NOT EXISTS `index_notes_labels`
            ON `notes` (`isTrashed`, `isArchived`, `folder`)
            """.trimIndent(),
        )

        db.execSQL(
            """
            CREATE INDEX IF NOT EXISTS `index_note_history_note_saved`
            ON `note_history` (`noteId`, `savedAtMs`)
            """.trimIndent(),
        )

        db.execSQL(
            """
            CREATE INDEX IF NOT EXISTS `index_note_history_saved`
            ON `note_history` (`savedAtMs`)
            """.trimIndent(),
        )
    }
}

/*
 * version 7 → 8：新增 privacy_notes 表，用于隐私空间笔记（仅存 Room，不写外部文件）。
 */
val MIGRATION_7_8 = object : Migration(7, 8) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `privacy_notes` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `title` TEXT NOT NULL,
                `content` TEXT NOT NULL,
                `updatedAtMs` INTEGER NOT NULL
            )
            """.trimIndent(),
        )
    }
}

/*
 * version 8 → 9：修复旧数据里 contentPreview 可能保存了完整正文的问题。
 *
 * 首页/归档/回收站列表只应该读取短预览。若旧版本曾把完整正文写入
 * contentPreview，大量测试文件会把 CursorWindow 撑爆，表现为：
 * Couldn't read row ..., col 0 from CursorWindow。
 */
val MIGRATION_8_9 = object : Migration(8, 9) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            UPDATE `notes`
            SET `contentPreview` = substr(`contentPreview`, 1, 200)
            WHERE length(`contentPreview`) > 200
            """.trimIndent(),
        )
    }
}


/*
 * version 9 → 10：新增 note_remarks 表，用于笔记备注。
 * 备注只存 Room，不写入外部 Markdown 文件，存放方式与历史版本一致。
 */
val MIGRATION_9_10 = object : Migration(9, 10) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `note_remarks` (
                `noteId` TEXT NOT NULL,
                `content` TEXT NOT NULL,
                `updatedAtMs` INTEGER NOT NULL,
                PRIMARY KEY(`noteId`)
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE INDEX IF NOT EXISTS `index_note_remarks_updated`
            ON `note_remarks` (`updatedAtMs`)
            """.trimIndent(),
        )
    }
}

/*
 * version 10 → 11：备注表从“一篇笔记一条备注”升级为“一篇笔记多条卡片备注”。
 * 旧的单条备注会保留为该笔记的第一条备注。
 */
val MIGRATION_10_11 = object : Migration(10, 11) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `note_remarks_new` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `noteId` TEXT NOT NULL,
                `content` TEXT NOT NULL,
                `createdAtMs` INTEGER NOT NULL,
                `updatedAtMs` INTEGER NOT NULL
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            INSERT INTO `note_remarks_new` (`noteId`, `content`, `createdAtMs`, `updatedAtMs`)
            SELECT `noteId`, `content`, `updatedAtMs`, `updatedAtMs`
            FROM `note_remarks`
            """.trimIndent(),
        )
        db.execSQL("DROP TABLE `note_remarks`")
        db.execSQL("ALTER TABLE `note_remarks_new` RENAME TO `note_remarks`")
        db.execSQL(
            """
            CREATE INDEX IF NOT EXISTS `index_note_remarks_note_updated`
            ON `note_remarks` (`noteId`, `updatedAtMs`)
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE INDEX IF NOT EXISTS `index_note_remarks_updated`
            ON `note_remarks` (`updatedAtMs`)
            """.trimIndent(),
        )
    }
}
/*
 * version 11 → 12：首页图片预览缓存。
 * 只新增一列 firstImageReference，用于保存正文中的第一张本地图片引用。
 * 旧数据先保持 NULL，后续刷新索引时补齐；不会改动正文、历史版本或同步内容。
 */
val MIGRATION_11_12 = object : Migration(11, 12) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            ALTER TABLE `notes` ADD COLUMN `firstImageReference` TEXT
            """.trimIndent(),
        )
    }
}


/*
 * version 12 → 13：缓存 YAML Frontmatter 中的 tags，用于首页显示和标签管理。
 * 只新增缓存列，真实标签仍以 Markdown 文件头的 tags 为准。
 */
val MIGRATION_12_13 = object : Migration(12, 13) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            ALTER TABLE `notes` ADD COLUMN `yamlTags` TEXT NOT NULL DEFAULT ''
            """.trimIndent(),
        )
    }
}


/*
 * version 13 → 14：缓存 YAML Frontmatter 中的 kardleaf_id。
 * 用于备注记录/历史版本记录直接通过 ID 找到对应笔记，避免打开设置页时扫描外部文件。
 */
val MIGRATION_13_14 = object : Migration(13, 14) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            ALTER TABLE `notes` ADD COLUMN `recordId` TEXT NOT NULL DEFAULT ''
            """.trimIndent(),
        )
        db.execSQL(
            """
            UPDATE `notes`
            SET `recordId` = `filePath`
            WHERE `recordId` = ''
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE INDEX IF NOT EXISTS `index_notes_record_id`
            ON `notes` (`recordId`)
            """.trimIndent(),
        )
    }
}
