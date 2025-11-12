package top.kagg886.pmf.ui.route.main.space

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.navigation3.runtime.NavKey
import cafe.adriel.voyager.core.model.ScreenModel
import kotlinx.serialization.Serializable
import org.koin.compose.viewmodel.koinViewModel
import org.orbitmvi.orbit.compose.collectSideEffect
import top.kagg886.pmf.LocalSnackBarHost
import top.kagg886.pmf.NavigationItem
import top.kagg886.pmf.composeWithAppBar
import top.kagg886.pmf.res.*
import top.kagg886.pmf.ui.component.TabContainer
import top.kagg886.pmf.ui.util.IllustFetchScreen
import top.kagg886.pmf.ui.util.IllustFetchSideEffect
import top.kagg886.pmf.util.stringResource

@Serializable
data object SpaceRoute : NavKey

@Composable
fun SpaceScreen() = NavigationItem.SPACE.composeWithAppBar {
    val page = remember {
        object : ScreenModel {
            val page = mutableIntStateOf(0)
        }
    }
    val index by page.page
    val tab = listOf(Res.string.follow, Res.string.latest)
    TabContainer(
        modifier = Modifier.fillMaxSize(),
        tab = tab,
        tabTitle = { Text(stringResource(it)) },
        current = tab[index],
        onCurrentChange = { page.page.value = tab.indexOf(it) },
    ) {
        when (it) {
            Res.string.follow -> {
                val model = koinViewModel<SpaceIllustViewModel>()
                val snackbarHostState = LocalSnackBarHost.current
                model.collectSideEffect { effect ->
                    when (effect) {
                        is IllustFetchSideEffect.Toast -> {
                            snackbarHostState.showSnackbar(effect.msg)
                        }
                    }
                }
                IllustFetchScreen(model)
            }

            Res.string.latest -> {
                val model = koinViewModel<NewestIllustViewModel>()
                val snackbarHostState = LocalSnackBarHost.current
                model.collectSideEffect { effect ->
                    when (effect) {
                        is IllustFetchSideEffect.Toast -> {
                            snackbarHostState.showSnackbar(effect.msg)
                        }
                    }
                }
                IllustFetchScreen(model)
            }
        }
    }
}
