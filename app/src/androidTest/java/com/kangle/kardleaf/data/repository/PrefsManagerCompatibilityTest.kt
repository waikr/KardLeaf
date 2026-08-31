package com.kangle.kardleaf.data.repository

import android.content.Context
import android.content.SharedPreferences
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PrefsManagerCompatibilityTest {
    @Test
    fun defaultsLegacyMigrationAndDelegatedWritesStayCompatible() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val current = context.getSharedPreferences("kardleaf_prefs", Context.MODE_PRIVATE)
        val legacy = context.getSharedPreferences("keepnotes_prefs", Context.MODE_PRIVATE)
        val currentBackup = current.all.toMap()
        val legacyBackup = legacy.all.toMap()

        try {
            current.edit().clear().commit()
            legacy.edit().clear().commit()

            PrefsManager(context).run {
                assertEquals(PrefsManager.EditorKernel.QUILLPAD_STYLE, getEditorKernel())
                assertEquals(PrefsManager.DEFAULT_HISTORY_VERSION_LIMIT, getHistoryVersionLimit())
                assertEquals(PrefsManager.DEFAULT_TRASH_FOLDER_NAME, getTrashFolderName())
                assertEquals(PrefsManager.DEFAULT_DRAWER_EDGE_WIDTH_DP, getDrawerEdgeWidthDp())
                assertEquals(PrefsManager.DEFAULT_APP_LANGUAGE, getAppLanguage())
                assertTrue(isNoteSidePanelsEnabled())
                assertFalse(isModifiedDateOnCardsVisible())
                assertTrue(getHiddenFolderPaths().contains(PrefsManager.DEFAULT_IMAGE_FOLDER))
                assertTrue(getHiddenFolderPaths().contains(PrefsManager.DEFAULT_QUICK_NOTE_FOLDER_NAME))
                assertFalse(getHiddenFolderPaths().contains(PrefsManager.LEGACY_TASK_FOLDER_NAME))
            }

            legacy.edit()
                .putString("editor_kernel", PrefsManager.EditorKernel.CODEMIRROR_LIVE_PREVIEW.name)
                .putInt("history_version_limit", 42)
                .putString("trash_folder_name", ".legacy-trash")
                .putInt("drawer_edge_width_dp", 73)
                .putString("app_language", "en")
                .putBoolean("note_side_panels_enabled", false)
                .commit()
            current.edit().clear().commit()

            PrefsManager(context).run {
                assertEquals(PrefsManager.EditorKernel.CODEMIRROR_LIVE_PREVIEW, getEditorKernel())
                assertEquals(42, getHistoryVersionLimit())
                assertEquals(".legacy-trash", getTrashFolderName())
                assertEquals(73, getDrawerEdgeWidthDp())
                assertEquals("en", getAppLanguage())
                assertFalse(isNoteSidePanelsEnabled())

                saveEditorKernel(PrefsManager.EditorKernel.QUILLPAD_STYLE)
                saveHistoryVersionLimit(64)
                saveModifiedDateOnCardsVisible(true)
            }

            PrefsManager(context).run {
                assertEquals(PrefsManager.EditorKernel.QUILLPAD_STYLE, getEditorKernel())
                assertEquals(64, getHistoryVersionLimit())
                assertTrue(isModifiedDateOnCardsVisible())
            }
            assertEquals(PrefsManager.EditorKernel.QUILLPAD_STYLE.name, current.getString("editor_kernel", null))
            assertEquals(64, current.getInt("history_version_limit", -1))
            assertTrue(current.getBoolean("show_modified_date_on_cards", false))
        } finally {
            current.restore(currentBackup)
            legacy.restore(legacyBackup)
        }
    }

    @Test
    fun legacyTopToolbarAddsKernelAtItsDefaultPosition() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val current = context.getSharedPreferences("kardleaf_prefs", Context.MODE_PRIVATE)
        val legacy = context.getSharedPreferences("keepnotes_prefs", Context.MODE_PRIVATE)
        val currentBackup = current.all.toMap()
        val legacyBackup = legacy.all.toMap()
        val oldOrder = listOf(
            PrefsManager.EditorTopToolbarItemId.SEARCH,
            PrefsManager.EditorTopToolbarItemId.EDIT,
            PrefsManager.EditorTopToolbarItemId.OUTLINE,
            PrefsManager.EditorTopToolbarItemId.REMARKS,
            PrefsManager.EditorTopToolbarItemId.HISTORY,
            PrefsManager.EditorTopToolbarItemId.MINDMAP,
            PrefsManager.EditorTopToolbarItemId.PRIVACY,
            PrefsManager.EditorTopToolbarItemId.ARCHIVE,
            PrefsManager.EditorTopToolbarItemId.DELETE,
            PrefsManager.EditorTopToolbarItemId.MORE,
            PrefsManager.EditorTopToolbarItemId.LABEL,
        )
        val oldMoreItems = setOf(
            PrefsManager.EditorTopToolbarItemId.HISTORY,
            PrefsManager.EditorTopToolbarItemId.MINDMAP,
            PrefsManager.EditorTopToolbarItemId.PRIVACY,
            PrefsManager.EditorTopToolbarItemId.ARCHIVE,
            PrefsManager.EditorTopToolbarItemId.DELETE,
        )

        try {
            current.edit().clear().commit()
            legacy.edit().clear().commit()
            current.edit()
                .putBoolean("editor_top_toolbar_defaults_v2_migrated", true)
                .putBoolean("editor_top_toolbar_more_default_migrated", true)
                .putString("editor_top_toolbar_item_order", oldOrder.joinToString(",") { it.name })
                .putStringSet("editor_top_toolbar_more_items", oldMoreItems.map { it.name }.toSet())
                .putStringSet("editor_top_toolbar_hidden_items", setOf(PrefsManager.EditorTopToolbarItemId.LABEL.name))
                .commit()

            val prefsManager = PrefsManager(context)
            val order = prefsManager.getEditorTopToolbarItemOrder()
            val moreItems = prefsManager.getEditorTopToolbarMoreItems()

            assertEquals(2, order.indexOf(PrefsManager.EditorTopToolbarItemId.KERNEL))
            assertEquals(
                PrefsManager.EditorTopToolbarItemId.KERNEL,
                order.first { it in moreItems },
            )
            assertEquals(order.joinToString(",") { it.name }, current.getString("editor_top_toolbar_item_order", null))
        } finally {
            current.restore(currentBackup)
            legacy.restore(legacyBackup)
        }
    }

    private fun SharedPreferences.restore(values: Map<String, *>) {
        val editor = edit().clear()
        values.forEach { (key, value) ->
            when (value) {
                is Boolean -> editor.putBoolean(key, value)
                is Float -> editor.putFloat(key, value)
                is Int -> editor.putInt(key, value)
                is Long -> editor.putLong(key, value)
                is String -> editor.putString(key, value)
                is Set<*> -> editor.putStringSet(key, value.filterIsInstance<String>().toSet())
            }
        }
        editor.commit()
    }
}
