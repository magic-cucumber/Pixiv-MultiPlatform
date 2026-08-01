package top.kagg886.pmf.ui.screen

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.serialization.Serializable
import org.jetbrains.compose.resources.stringResource
import org.orbitmvi.orbit.compose.collectAsState
import org.orbitmvi.orbit.compose.collectSideEffect
import top.kagg886.pmf.i18n.Lang
import top.kagg886.pmf.i18n.root_error_summary
import top.kagg886.pmf.i18n.root_error_title
import top.kagg886.pmf.i18n.logger_open
import top.kagg886.pmf.LocalNavController
import top.kagg886.pmf.ui.component.EmptyScreen
import top.kagg886.pmf.ui.screen.logger.list.LoggerListRoute
import top.kagg886.pmf.util.nav3.SerializableNavKey


@Serializable
data object RootRoute : SerializableNavKey


@Composable
fun RootScreen(content: @Composable () -> Unit) {
    val model = viewModel<RootViewModel> {
        RootViewModel()
    }
    val state by model.collectAsState()
    val nav = LocalNavController.current
    model.collectSideEffect {

    }
    when (state) {
        RootViewModelState.Loading -> Unit
        RootViewModelState.Error -> EmptyScreen(
            icon = {
                Icon(
                    imageVector = Icons.Outlined.ErrorOutline,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp)
                )
            },
            title = { Text(stringResource(Lang.string.root_error_title)) },
            summary = { Text(stringResource(Lang.string.root_error_summary)) },
            actions = {
                Button(onClick = { nav.navigate(LoggerListRoute) }) {
                    Text(stringResource(Lang.string.logger_open))
                }
            },
        )

        RootViewModelState.LoadSuccess -> content()
    }
}
