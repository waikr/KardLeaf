package org.qosp.notes.preferences

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import me.msoul.datastore.EnumPreference
import me.msoul.datastore.defaultOf

/**
 * Sandbox stub of Quillpad's [PreferenceRepository].
 *
 * The original pulls preferences from a DataStore + FlowSharedPreferences. The
 * sandbox does not persist preferences, so this returns a fixed default
 * [AppPreferences] and resolves individual enum defaults via [defaultOf].
 *
 * The public surface matches what [org.qosp.notes.ui.editor.EditorViewModel]
 * calls: [getAll] and [get].
 */
class PreferenceRepository {

    fun getAll(): Flow<AppPreferences> = flowOf(AppPreferences())

    inline fun <reified T> get(): Flow<T> where T : Enum<T>, T : EnumPreference = flowOf(defaultOf())
}
