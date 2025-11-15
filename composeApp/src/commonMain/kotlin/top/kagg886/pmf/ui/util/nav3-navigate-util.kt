package top.kagg886.pmf.ui.util

import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * ================================================
 * Author:     886kagg
 * Created on: 2025/11/15 20:04
 * ================================================
 */

// 单线程确保并发安全
private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default.limitedParallelism(1))

fun <T : NavKey> NavBackStack<T>.removeLastOrNullWorkaround() {
    val stack = this
    scope.launch {
        if (stack.size > 1) {
            stack.removeLastOrNull()
        }
    }
}
