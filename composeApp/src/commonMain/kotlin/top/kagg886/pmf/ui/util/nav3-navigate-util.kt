package top.kagg886.pmf.ui.util

import androidx.compose.runtime.Composable
import androidx.compose.ui.window.DialogProperties
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.scene.DialogSceneStrategy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.koin.compose.navigation3.EntryProviderInstaller
import org.koin.core.annotation.KoinExperimentalAPI
import org.koin.core.annotation.KoinInternalApi
import org.koin.core.definition.KoinDefinition
import org.koin.core.module.KoinDslMarker
import org.koin.core.module.Module
import org.koin.core.module._singleInstanceFactory
import org.koin.core.qualifier.named
import org.koin.core.scope.Scope

/**
 * ================================================
 * Author:     886kagg
 * Created on: 2025/11/15 20:04
 * ================================================
 */

// 单线程确保并发安全
private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default.limitedParallelism(1))

@KoinExperimentalAPI
@KoinDslMarker
@OptIn(KoinInternalApi::class)
inline fun <reified T : Any> Module.dialog(
    metadata: DialogProperties = DialogProperties(),
    noinline definition: @Composable Scope.(T) -> Unit,
): KoinDefinition<EntryProviderInstaller> {
    val def = _singleInstanceFactory<EntryProviderInstaller>(named<T>(), {
        val scope = this {
            entry<T>(
                metadata = DialogSceneStrategy.dialog(metadata),
                content = { t -> definition(scope, t) },
            )
        }
    })
    indexPrimaryType(def)
    return KoinDefinition(this, def)
}

fun <T : NavKey> NavBackStack<T>.removeLastOrNullWorkaround() {
    val stack = this
    scope.launch {
        if (stack.size > 1) {
            stack.removeLastOrNull()
        }
    }
}
