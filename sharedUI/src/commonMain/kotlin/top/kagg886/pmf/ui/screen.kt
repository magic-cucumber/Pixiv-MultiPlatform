package top.kagg886.pmf.ui

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
    val model = viewModel<RootViewModel> {
        RootViewModel()
    }
    val state by model.collectAsState()
    model.collectSideEffect {

    }
    content()
}
