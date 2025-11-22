package top.kagg886.pmf.ui.route.main.series.novel

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.DrawerDefaults
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.PermanentDrawerSheet
import androidx.compose.material3.PermanentNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavKey
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import org.orbitmvi.orbit.compose.collectAsState
import org.orbitmvi.orbit.compose.collectSideEffect
import top.kagg886.pixko.module.novel.SeriesDetail
import top.kagg886.pmf.LocalNavBackStack
import top.kagg886.pmf.LocalSnackBarHost
import top.kagg886.pmf.openBrowser
import top.kagg886.pmf.res.*
import top.kagg886.pmf.ui.component.ErrorPage
import top.kagg886.pmf.ui.component.Loading
import top.kagg886.pmf.ui.component.SupportRTLModalNavigationDrawer
import top.kagg886.pmf.ui.util.AuthorCard
import top.kagg886.pmf.ui.util.NovelFetchScreen
import top.kagg886.pmf.ui.util.NovelFetchViewModel
import top.kagg886.pmf.ui.util.removeLastOrNullWorkaround
import top.kagg886.pmf.ui.util.useWideScreenMode
import top.kagg886.pmf.util.stringResource

@Serializable
data class NovelSeriesRoute(val id: Int) : NavKey

@Composable
fun NovelSeriesScreen(route: NovelSeriesRoute) {
    val id = route.id
    val model = koinViewModel<NovelSeriesScreenModel>(key = "novel_series_$id") {
        parametersOf(id)
    }
    val snack = LocalSnackBarHost.current
    model.collectSideEffect {
        when (it) {
            is NovelSeriesScreenSideEffect.Toast -> {
                snack.showSnackbar(it.msg)
            }
        }
    }
    val state by model.collectAsState()
    NovelSeriesScreenContent(id, state, model)
}

@Composable
private fun NovelSeriesScreenContent(id: Int, state: NovelSeriesScreenState, model: NovelSeriesScreenModel) {
    when (state) {
        NovelSeriesScreenState.Loading -> Loading()
        is NovelSeriesScreenState.LoadingFailed -> ErrorPage(text = state.msg) {
            model.reload()
        }

        is NovelSeriesScreenState.LoadingSuccess -> {
            val novelModel = koinViewModel<NovelSeriesFetchModel>(key = "novel_$id") {
                parametersOf(id)
            }

            if (useWideScreenMode) {
                WideNovelSeriesScreenContent(
                    id,
                    state.info,
                    state.itemInViewLater,
                    novelModel,
                    onFavoriteButtonClick = {
                        val job = if (it) model.followUser(false) else model.unFollowUser()
                        job.join()
                    },
                    onFavoritePrivateButtonClick = {
                        val job = model.followUser(true)
                        job.join()
                    },
                ) {
                    if (it) model.addViewLater() else model.removeViewLater()
                }
                return
            }
            NovelSeriesScreenContent(
                id,
                state.info,
                state.itemInViewLater,
                novelModel,
                onFavoriteButtonClick = {
                    val job = if (it) model.followUser(false) else model.unFollowUser()
                    job.join()
                },
                onFavoritePrivateButtonClick = {
                    val job = model.followUser(true)
                    job.join()
                },
            ) {
                if (it) model.addViewLater() else model.removeViewLater()
            }
        }
    }
}

@Composable
private fun WideNovelSeriesScreenContent(
    id: Int,
    info: SeriesDetail,
    itemInViewLater: Boolean,
    model: NovelFetchViewModel,
    onFavoriteButtonClick: suspend (Boolean) -> Unit,
    onFavoritePrivateButtonClick: suspend () -> Unit,
    onViewLaterBtnClick: (Boolean) -> Unit,
) {
    PermanentNavigationDrawer(
        drawerContent = {
            NovelSeriesScreenDrawerContent(
                id,
                true,
                info,
                itemInViewLater,
                onFavoriteButtonClick,
                onFavoritePrivateButtonClick,
                onViewLaterBtnClick,
            )
        },
    ) {
        NovelFetchScreen(model)
    }
}

@Composable
private fun NovelSeriesScreenContent(
    id: Int,
    info: SeriesDetail,
    inViewLater: Boolean,
    model: NovelFetchViewModel,
    onFavoriteButtonClick: suspend (Boolean) -> Unit,
    onFavoritePrivateButtonClick: suspend () -> Unit,
    onViewLaterBtnClick: (Boolean) -> Unit,
) {
    val state = rememberDrawerState(DrawerValue.Open)
    SupportRTLModalNavigationDrawer(
        drawerContent = {
            NovelSeriesScreenDrawerContent(
                id,
                false,
                info,
                inViewLater,
                onFavoriteButtonClick,
                onFavoritePrivateButtonClick,
                onViewLaterBtnClick,
            )
        },
        drawerState = state,
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Text(stringResource(Res.string.novel_series))
                    },
                    navigationIcon = {
                        val scope = rememberCoroutineScope()
                        IconButton(
                            onClick = {
                                scope.launch {
                                    state.open()
                                }
                            },
                        ) {
                            Icon(
                                Icons.Default.Menu,
                                null,
                            )
                        }
                    },
                    actions = {
                        NovelSeriesScreenActions(
                            id,
                            true,
                            inViewLater,
                            onViewLaterBtnClick,
                        )
                    },
                )
            },
        ) {
            Box(Modifier.padding(it)) {
                NovelFetchScreen(model)
            }
        }
    }
}

@Composable
private fun NovelSeriesScreenActions(
    id: Int,
    showActionButton: Boolean,
    inViewLater: Boolean,
    onViewLaterBtnClick: (Boolean) -> Unit,
) {
    if (!showActionButton) {
        return
    }
    var expanded by remember { mutableStateOf(false) }
    IconButton(
        onClick = {
            expanded = true
        },
    ) {
        Icon(Icons.Default.Menu, null)
    }
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = { expanded = false },
    ) {
        DropdownMenuItem(
            text = { Text(stringResource(Res.string.open_in_browser)) },
            onClick = {
                openBrowser("https://www.pixiv.net/novel/series/$id")
                expanded = false
            },
        )

        if (inViewLater) {
            DropdownMenuItem(
                text = { Text(stringResource(Res.string.remove_watch_later)) },
                onClick = {
                    onViewLaterBtnClick(false)
                    expanded = false
                },
            )
        } else {
            DropdownMenuItem(
                text = { Text(stringResource(Res.string.add_watch_later)) },
                onClick = {
                    onViewLaterBtnClick(true)
                    expanded = false
                },
            )
        }
    }
}

@Composable
private fun NovelSeriesScreenDrawerContent(
    id: Int,
    showActionButton: Boolean,
    info: SeriesDetail,
    inViewLater: Boolean,
    onFavoriteButtonClick: suspend (Boolean) -> Unit,
    onFavoritePrivateButtonClick: suspend () -> Unit,
    onViewLaterBtnClick: (Boolean) -> Unit,
) {
    PermanentDrawerSheet {
        Column(Modifier.width(DrawerDefaults.MaximumDrawerWidth)) {
            TopAppBar(
                title = {
                    Text(
                        text = info.title,
                        style = MaterialTheme.typography.titleMedium,
                    )
                },
                navigationIcon = {
                    val stack = LocalNavBackStack.current
                    IconButton(onClick = { stack.removeLastOrNullWorkaround() }) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            null,
                        )
                    }
                },
                actions = {
                    NovelSeriesScreenActions(
                        id,
                        showActionButton,
                        inViewLater,
                        onViewLaterBtnClick,
                    )
                },
            )

            LazyColumn {
                item {
                    OutlinedCard(modifier = Modifier.padding(horizontal = 8.dp)) {
                        ListItem(
                            headlineContent = {
                                Text(info.caption)
                            },
                        )
                    }
                }

                item {
                    Spacer(Modifier.height(16.dp))
                    AuthorCard(
                        modifier = Modifier.align(Alignment.CenterHorizontally).fillMaxWidth()
                            .padding(horizontal = 8.dp),
                        info.user,
                        onFavoriteClick = onFavoriteButtonClick,
                        onFavoritePrivateClick = onFavoritePrivateButtonClick,
                    )
                }

                item {
                    Spacer(Modifier.height(16.dp))
                    OutlinedCard(modifier = Modifier.padding(horizontal = 8.dp)) {
                        ListItem(
                            overlineContent = {
                                Text(stringResource(Res.string.novel_count))
                            },
                            headlineContent = {
                                Text(info.pageCount.toString())
                            },
                        )
                    }
                }
                item {
                    Spacer(Modifier.height(16.dp))
                    OutlinedCard(modifier = Modifier.padding(horizontal = 8.dp)) {
                        ListItem(
                            overlineContent = {
                                Text(stringResource(Res.string.word_count))
                            },
                            headlineContent = {
                                Text(info.totalCharacterCount.toString())
                            },
                        )
                    }
                }
                item {
                    Spacer(Modifier.height(16.dp))
                }
            }
        }
    }
}
