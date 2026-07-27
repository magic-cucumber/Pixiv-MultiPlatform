package top.kagg886.pmf.util

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import kotlin.reflect.KClass
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn

/**
 * ================================================
 * Author:     iveou
 * Created on: 2026/7/27 10:44
 * ================================================
 */

/**
 * Creates a read-only configuration [StateFlow] backed by this preferences [DataStore].
 *
 * The initial preference is read before this function returns. Consequently, a collector never
 * observes [default] and then immediately the persisted value. The returned flow is owned by
 * [scope] and is cancelled with it.
 *
 * Supported [clazz] values are [Boolean], [Int], [Long], [Float], [Double], [String], and
 * [Set] (as a `Set<String>`).
 */
public suspend fun <T : Any> DataStore<Preferences>.flow(
    scope: CoroutineScope,
    key: String,
    clazz: KClass<T>,
    default: () -> T,
): StateFlow<T> {
    val preferenceKey = preferenceKey(key, clazz)
    val initialValue = data.first()[preferenceKey] ?: default()
    initialValue.requireSupport()
    return data
        .map { preferences -> preferences[preferenceKey] ?: default() }
        .stateIn(scope, SharingStarted.Eagerly, initialValue)
}

/** Updates one supported preference atomically. */
public suspend fun <T : Any> DataStore<Preferences>.set(name: String, value: T) {
    value.requireSupport()
    @Suppress("UNCHECKED_CAST")
    val clazz = if (value is Set<*>) Set::class else value::class
    val preferenceKey = preferenceKey(name, clazz as KClass<T>)
    edit { preferences -> preferences[preferenceKey] = value }
}

private fun Any.requireSupport() {
    require(this !is Set<*> || this.all { it is String }) {
        "Only Set<String> is supported by Preferences"
    }
}

@Suppress("UNCHECKED_CAST")
private fun <T : Any> preferenceKey(key: String, clazz: KClass<T>): Preferences.Key<T> =
    when (clazz) {
        Boolean::class -> booleanPreferencesKey(key)
        Int::class -> intPreferencesKey(key)
        Long::class -> longPreferencesKey(key)
        Float::class -> floatPreferencesKey(key)
        Double::class -> doublePreferencesKey(key)
        String::class -> stringPreferencesKey(key)
        Set::class -> stringSetPreferencesKey(key)
        else -> throw IllegalArgumentException("Unsupported preference type: ${clazz.qualifiedName}")
    } as Preferences.Key<T>
