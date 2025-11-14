package top.kagg886.pmf.ui.route.main.search.v2

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import org.orbitmvi.orbit.compose.collectAsState
import org.orbitmvi.orbit.compose.collectSideEffect
import top.kagg886.pixko.module.search.SearchSort
import top.kagg886.pixko.module.search.SearchTarget
import top.kagg886.pmf.LocalNavBackStack
import top.kagg886.pmf.res.*
import top.kagg886.pmf.ui.component.TabContainer
import top.kagg886.pmf.ui.util.AuthorFetchScreen
import top.kagg886.pmf.ui.util.IllustFetchScreen
import top.kagg886.pmf.ui.util.NovelFetchScreen
import top.kagg886.pmf.util.stringResource

@Serializable
data class SearchResultRoute(
    val keyword: List<String>,
    val sort: SearchSort,
    val target: SearchTarget,
) : NavKey

@Composable
fun SearchResultScreen(route: SearchResultRoute) {
    val (keyword, sort, target) = route
    val model = koinViewModel<SearchResultViewModel> { parametersOf(keyword, sort, target) }
    val state by model.collectAsState()
    val stack = LocalNavBackStack.current
    val snackbarHostState = remember { SnackbarHostState() }

    model.collectSideEffect { sideEffect ->
        when (sideEffect) {
            is SearchResultSideEffect.Toast -> {
                snackbarHostState.showSnackbar(sideEffect.message)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(
                            Res.string.search_result_for,
                            state.keyword.joinToString(" "),
                        ),
                        maxLines = 1,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { stack.removeLastOrNull() }) {
                        Icon(Icons.AutoMirrored.Default.ArrowBack, null)
                    }
                },
            )
        },
    ) { paddingValues ->
        val data = buildMap<String, (@Composable () -> Unit)> {
            state.illustRepo?.let {
                put(stringResource(Res.string.illust), { IllustFetchScreen(it) })
            }
            state.novelRepo?.let {
                put(stringResource(Res.string.novel), { NovelFetchScreen(it) })
            }
            state.authorRepo?.let {
                put(stringResource(Res.string.user), { AuthorFetchScreen(it) })
            }
        }

        var tab by rememberSaveable {
            mutableStateOf(
                data.keys.first(),
            )
        }

        TabContainer(
            modifier = Modifier.padding(paddingValues),
            tab = data.keys.toList(),
            tabTitle = { Text(it) },
            current = tab,
            onCurrentChange = { tab = it },
        ) {
            data[it]?.invoke()
        }
    }
}
