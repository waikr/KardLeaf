package me.msoul.datastore

/**
 * Sandbox shim for Quillpad's `me.msoul.datastore` preference library.
 *
 * The real library (MKey) provides DataStore-backed enum preferences. For the
 * KardLeaf Quillpad editor sandbox we do not persist preferences — we only need
 * the type surface so the original `PreferenceEnums.kt` / `AppPreferences.kt`
 * / `EditorViewModel.kt` compile unchanged. Defaults are resolved in-memory via
 * [defaultOf].
 */
interface EnumPreference {
    val key: String
    val isDefault: Boolean get() = false
}

fun key(name: String): EnumPreference = object : EnumPreference {
    override val key = name
}

inline fun <reified T> defaultOf(): T where T : Enum<T>, T : EnumPreference =
    // Prefer the constant explicitly marked isDefault; fall back to the first
    // constant for enums that declare no default (e.g. SortTagsMethod,
    // SortNavdrawerNotebooksMethod). Matches the real library's tolerant behaviour.
    enumValues<T>().firstOrNull { it.isDefault } ?: enumValues<T>().first()
