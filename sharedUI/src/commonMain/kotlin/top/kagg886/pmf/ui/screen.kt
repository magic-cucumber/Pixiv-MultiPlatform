package top.kagg886.pmf.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.serialization.Serializable
import org.orbitmvi.orbit.compose.collectAsState
import org.orbitmvi.orbit.compose.collectSideEffect
import top.kagg886.pmf.util.nav3.SerializableNavKey


@Serializable
data object RootRoute : SerializableNavKey


@Composable
fun RootScreen(content: @Composable () -> Unit) {
    val model = viewModel<RootViewModel> {
        RootViewModel()
    }
    val state by model.collectAsState()
    model.collectSideEffect {

    }
    content()
}
