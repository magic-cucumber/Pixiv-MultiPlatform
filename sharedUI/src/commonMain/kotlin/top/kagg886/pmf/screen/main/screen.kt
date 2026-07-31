package top.kagg886.pmf.screen.main

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.serialization.Serializable
import org.orbitmvi.orbit.compose.collectAsState
import org.orbitmvi.orbit.compose.collectSideEffect
import top.kagg886.pixko.PixivAccount
import top.kagg886.pmf.LocalNavController
import top.kagg886.pmf.screen.login.LoginRoute
import top.kagg886.pmf.util.nav3.SerializableNavKey

/**
 * ================================================
 * Author:     iveou
 * Created on: 2026/7/29 15:19
 * ================================================
 */

@Serializable
data object MainRoute : SerializableNavKey


@Composable
fun MainScreen(content: @Composable () -> Unit) {
    val model = viewModel<MainViewModel> { MainViewModel() }
    val state = model.collectAsState()
    val nav = LocalNavController.current

    model.collectSideEffect {
        when (it) {
            MainViewModelSideEffect.NavigateToLogin -> {
                nav.removeBackStack(MainRoute)
                nav.navigate(LoginRoute)
            }
        }
    }

    content()
}
