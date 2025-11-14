package top.kagg886.pmf.ui.route.main.history

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSerializable
import androidx.compose.ui.Modifier
import org.koin.compose.viewmodel.koinViewModel
import org.orbitmvi.orbit.compose.collectSideEffect
import top.kagg886.pmf.LocalSnackBarHost
import top.kagg886.pmf.res.*
import top.kagg886.pmf.ui.component.TabContainer
import top.kagg886.pmf.ui.util.IllustFetchScreen
import top.kagg886.pmf.ui.util.IllustFetchSideEffect
import top.kagg886.pmf.ui.util.NovelFetchScreen
import top.kagg886.pmf.ui.util.NovelFetchSideEffect
import top.kagg886.pmf.util.stringResource

@Composable
fun HistoryScreen() {
    TabContainer(
        modifier = Modifier.fillMaxSize(),
        state = rememberSerializable { mutableIntStateOf(0) },
        tab = listOf(stringResource(Res.string.illust), stringResource(Res.string.novel)),
    ) {
        val snackbarHostState = LocalSnackBarHost.current
        when (it) {
            0 -> {
                val model = koinViewModel<HistoryIllustViewModel>()
                model.collectSideEffect { effect ->
                    when (effect) {
                        is IllustFetchSideEffect.Toast -> {
                            snackbarHostState.showSnackbar(effect.msg)
                        }
                    }
                }
                IllustFetchScreen(model)
            }

            1 -> {
                val model = koinViewModel<HistoryNovelViewModel>()
                model.collectSideEffect { effect ->
                    when (effect) {
                        is NovelFetchSideEffect.Toast -> {
                            snackbarHostState.showSnackbar(effect.msg)
                        }
                    }
                }
                NovelFetchScreen(model)
            }
        }
    }
}
