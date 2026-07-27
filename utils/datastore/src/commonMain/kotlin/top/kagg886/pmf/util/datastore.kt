package top.kagg886.pmf.util

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import kotlinx.atomicfu.locks.reentrantLock
import kotlinx.atomicfu.locks.withLock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import okio.Path

/**
 * ================================================
 * Author:     iveou
 * Created on: 2026/7/27 09:34
 * ================================================
 */


public object Store {
    internal val instances = mutableMapOf<Path, DataStore<Preferences>>()
    internal val instancesLock = reentrantLock()

    public fun of(path: Path): DataStore<Preferences> = of(
        path = path,
        scopeFactory = { CoroutineScope(Dispatchers.IO + SupervisorJob()) },
        dataStoreFactory = { scope, dataStorePath ->
            PreferenceDataStoreFactory.createWithPath(scope = scope) { dataStorePath }
        },
    )

    internal fun of(
        path: Path,
        scopeFactory: () -> CoroutineScope,
        dataStoreFactory: (CoroutineScope, Path) -> DataStore<Preferences>,
    ): DataStore<Preferences> = instancesLock.withLock {
        instances[path] ?: run {
            val scope = scopeFactory()
            dataStoreFactory(scope, path).also { dataStore ->
                instances[path] = dataStore
                scope.coroutineContext[Job]?.invokeOnCompletion {
                    instancesLock.withLock {
                        if (instances[path] === dataStore) {
                            instances.remove(path)
                        }
                    }
                }
            }
        }
    }
}
