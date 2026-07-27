package top.kagg886.pmf.fronted

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.serialization.Serializable
import org.orbitmvi.orbit.compose.collectAsState
import org.orbitmvi.orbit.compose.collectSideEffect
import top.kagg886.pmf.util.device.LayoutType
import top.kagg886.pmf.util.device.Platform
import top.kagg886.pmf.util.nav3.SerializableNavKey


@Serializable
data object RootRoute : SerializableNavKey


@Composable
fun RootScreen(content: @Composable () -> Unit) {
    val model = viewModel<RootViewModel>()
    val state by model.collectAsState()

    when (val state = state) {
        RootViewModelState.Loading -> return
        is RootViewModelState.LoadSuccess -> {

            model.collectSideEffect {
                when(it) {
                    is RootViewModelEffect.Toast -> {
                        val result = state.snack.showSnackbar(it.action.message,it.action.actionLabel)
                        if (result == SnackbarResult.ActionPerformed) it.action.onAction?.invoke()
                    }
                }
            }

            Box(Modifier.fillMaxSize()) {
                content()

                SnackbarHost(
                    hostState = state.snack,
                    modifier = Modifier
                        .padding(16.dp)
                        .align(
                            alignment = when (Platform.LayoutType) {
                                LayoutType.Phone -> Alignment.BottomCenter
                                else -> Alignment.BottomEnd
                            }
                        ),
                )
            }
        }
    }
}
