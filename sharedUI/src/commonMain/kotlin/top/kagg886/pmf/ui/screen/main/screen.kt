package top.kagg886.pmf.ui.screen.main

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.serialization.Serializable
import org.jetbrains.compose.resources.stringResource
import org.orbitmvi.orbit.compose.collectAsState
import org.orbitmvi.orbit.compose.collectSideEffect
import top.kagg886.pixko.PixivAccount
import top.kagg886.pmf.LocalNavController
import top.kagg886.pmf.i18n.Lang
import top.kagg886.pmf.i18n.main_error_summary
import top.kagg886.pmf.i18n.main_error_title
import top.kagg886.pmf.ui.component.EmptyScreen
import top.kagg886.pmf.ui.screen.login.LoginRoute
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

    when (state.value) {
        MainViewModelState.Loading -> Unit
        MainViewModelState.LoadError -> EmptyScreen(
            icon = {
                Icon(
                    imageVector = Icons.Outlined.ErrorOutline,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp)
                )
            },
            title = { Text(stringResource(Lang.string.main_error_title)) },
            summary = { Text(stringResource(Lang.string.main_error_summary)) }
        )

        is MainViewModelState.LoadSuccess -> content()
    }
}
