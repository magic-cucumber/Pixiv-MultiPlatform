package top.kagg886.pmf.ui.util

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelStoreOwner
import org.koin.compose.viewmodel.koinViewModel

/**
 * ================================================
 * Author:     886kagg
 * Created on: 2026/1/22 13:44
 * ================================================
 */

val LocalGlobalViewModelStoreOwner = staticCompositionLocalOf<ViewModelStoreOwner> {
    error("not provided")
}

@Composable
inline fun <reified T : ViewModel> globalViewModel() = koinViewModel<T>(
    viewModelStoreOwner = LocalGlobalViewModelStoreOwner.current,
)
