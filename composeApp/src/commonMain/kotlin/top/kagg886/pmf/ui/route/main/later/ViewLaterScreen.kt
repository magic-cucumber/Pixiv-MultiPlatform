package top.kagg886.pmf.ui.route.main.later

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.paging.compose.collectAsLazyPagingItems
import coil3.compose.AsyncImage
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement
import org.koin.compose.viewmodel.koinViewModel
import org.orbitmvi.orbit.compose.collectAsState
import org.orbitmvi.orbit.compose.collectSideEffect
import top.kagg886.pixko.module.illust.Illust
import top.kagg886.pixko.module.illust.get
import top.kagg886.pixko.module.novel.Novel
import top.kagg886.pixko.module.novel.SeriesDetail
import top.kagg886.pixko.module.user.UserInfo
import top.kagg886.pmf.LocalNavBackStack
import top.kagg886.pmf.LocalSnackBarHost
import top.kagg886.pmf.backend.AppConfig
import top.kagg886.pmf.backend.database.dao.WatchLaterItem
import top.kagg886.pmf.backend.database.dao.WatchLaterType
import top.kagg886.pmf.res.Res
import top.kagg886.pmf.res.load_failed
import top.kagg886.pmf.res.no_more_data
import top.kagg886.pmf.res.page_is_empty
import top.kagg886.pmf.ui.component.ErrorPage
import top.kagg886.pmf.ui.component.Loading
import top.kagg886.pmf.ui.route.main.detail.author.AuthorRoute
import top.kagg886.pmf.ui.route.main.detail.illust.IllustDetailRoute
import top.kagg886.pmf.ui.route.main.detail.novel.NovelDetailRoute
import top.kagg886.pmf.ui.route.main.series.novel.NovelSeriesRoute
import top.kagg886.pmf.util.stringResource

/**
 * ================================================
 * Author:     886kagg
 * Created on: 2025/11/22 23:26
 * ================================================
 */

@Serializable
data object ViewLaterRoute

@Composable
fun ViewLaterScreen() {
    val model = koinViewModel<ViewLaterModel>()
    val state by model.collectAsState()
    val snackbar = LocalSnackBarHost.current

    model.collectSideEffect { effect ->
        when (effect) {
            is ViewLaterSideEffect.Toast -> {
                snackbar.showSnackbar(effect.message)
            }
        }
    }

    ViewLaterContent(model, state)
}

@Composable
private fun ViewLaterContent(model: ViewLaterModel, state: ViewLaterState) {
    Box(Modifier.fillMaxSize()) {
        val lazyColumnState = rememberLazyListState()
        val scope = rememberCoroutineScope()

        when (state) {
            is ViewLaterState.Loading -> {
                Loading()
            }

            is ViewLaterState.Error -> {
                ErrorPage(text = stringResource(Res.string.load_failed)) {
                    // TODO: retry
                }
            }

            is ViewLaterState.Success -> {
                val data = state.list.collectAsLazyPagingItems()
                when {
                    !data.loadState.isIdle && data.itemCount == 0 -> Loading()
                    else -> {
                        if (data.itemCount == 0 && data.loadState.isIdle) {
                            ErrorPage(text = stringResource(Res.string.page_is_empty)) {
                                data.retry()
                            }
                            return@Box
                        }
                        LazyColumn(
                            modifier = Modifier.fillMaxSize().padding(5.dp),
                            state = lazyColumnState,
                        ) {
                            items(data.itemCount, key = { data.peek(it)!!.id!! }) { i ->
                                val item = data[i]!!
                                when (item.type) {
                                    WatchLaterType.ILLUST -> {
                                        IllustWatchLaterItem(
                                            item = item,
                                            model = model,
                                            modifier = Modifier.padding(5.dp),
                                        )
                                    }

                                    WatchLaterType.NOVEL -> {
                                        NovelWatchLaterItem(
                                            item = item,
                                            model = model,
                                            modifier = Modifier.padding(5.dp),
                                        )
                                    }

                                    WatchLaterType.AUTHOR -> {
                                        AuthorWatchLaterItem(
                                            item = item,
                                            model = model,
                                            modifier = Modifier.padding(5.dp),
                                        )
                                    }

                                    WatchLaterType.SERIES -> {
                                        SeriesWatchLaterItem(
                                            item = item,
                                            model = model,
                                            modifier = Modifier.padding(5.dp),
                                        )
                                    }
                                }
                            }

                            item {
                                if (!data.loadState.isIdle) {
                                    Loading()
                                } else {
                                    Text(
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier.fillMaxWidth(),
                                        text = stringResource(Res.string.no_more_data),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        var fabExpanded by remember { mutableStateOf(false) }

        Column(
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
            horizontalAlignment = Alignment.End,
        ) {
            AnimatedVisibility(
                visible = fabExpanded,
                enter = fadeIn() + expandVertically(expandFrom = Alignment.Bottom),
                exit = fadeOut() + shrinkVertically(shrinkTowards = Alignment.Bottom),
            ) {
                Column(horizontalAlignment = Alignment.End) {
                    SmallFloatingActionButton(
                        onClick = {
                            scope.launch {
                                lazyColumnState.animateScrollToItem(0)
                            }
                            fabExpanded = false
                        },
                        modifier = Modifier.padding(bottom = 8.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowUp,
                            contentDescription = "back to top",
                        )
                    }
                }
            }

            FloatingActionButton(
                onClick = { fabExpanded = !fabExpanded },
            ) {
                Icon(
                    imageVector = Icons.Default.Menu,
                    contentDescription = "menu",
                )
            }
        }
    }
}

private val WatchLaterItem.illust: Illust
    get() {
        check(this.type == WatchLaterType.ILLUST) {
            "This item is not an illust: ${this.type}"
        }
        return Json.decodeFromJsonElement(this.metadata)
    }

private val WatchLaterItem.novel: Novel
    get() {
        check(this.type == WatchLaterType.NOVEL) {
            "This item is not a novel: ${this.type}"
        }
        return Json.decodeFromJsonElement(this.metadata)
    }

private val WatchLaterItem.author: UserInfo
    get() {
        check(this.type == WatchLaterType.AUTHOR) {
            "This item is not an author: ${this.type}"
        }
        return Json.decodeFromJsonElement(this.metadata)
    }

private val WatchLaterItem.series: SeriesDetail
    get() {
        check(this.type == WatchLaterType.SERIES) {
            "This item is not a series: ${this.type}"
        }
        return Json.decodeFromJsonElement(this.metadata)
    }

@Composable
private fun IllustWatchLaterItem(
    item: WatchLaterItem,
    model: ViewLaterModel,
    modifier: Modifier = Modifier,
) {
    val stack = LocalNavBackStack.current
    OutlinedCard(
        modifier = modifier,
        onClick = {
            stack += IllustDetailRoute(item.illust)
            if (AppConfig.watchLaterRemoveWhenClick) {
                model.deleteItem(item, true)
            }
        },
    ) {
        Row(
            modifier = Modifier.padding(5.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AsyncImage(
                model = item.illust.contentImages.get()!![0],
                modifier = Modifier.size(75.dp, 120.dp).clip(CardDefaults.shape),
                contentDescription = null,
                contentScale = ContentScale.Inside,
            )
            ListItem(
                overlineContent = {
                    Text(item.illust.id.toString())
                },
                headlineContent = {
                    Text(item.illust.title, maxLines = 1)
                },
                trailingContent = {
                    IconButton(
                        onClick = {
                            model.deleteItem(item)
                        },
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = null,
                        )
                    }
                },
            )
        }
    }
}

@Composable
private fun NovelWatchLaterItem(
    item: WatchLaterItem,
    model: ViewLaterModel,
    modifier: Modifier = Modifier,
) {
    val stack = LocalNavBackStack.current
    OutlinedCard(
        modifier = modifier,
        onClick = {
            stack += NovelDetailRoute(item.novel.id.toLong())
            if (AppConfig.watchLaterRemoveWhenClick) {
                model.deleteItem(item, true)
            }
        },
    ) {
        Row(
            modifier = Modifier.padding(5.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AsyncImage(
                model = item.novel.imageUrls.content,
                modifier = Modifier.size(75.dp, 120.dp).clip(CardDefaults.shape),
                contentDescription = null,
                contentScale = ContentScale.Inside,
            )
            ListItem(
                overlineContent = {
                    Text(item.novel.id.toString())
                },
                headlineContent = {
                    Text(item.novel.title, maxLines = 1)
                },
                trailingContent = {
                    IconButton(
                        onClick = {
                            model.deleteItem(item)
                        },
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = null,
                        )
                    }
                },
            )
        }
    }
}

@Composable
private fun AuthorWatchLaterItem(
    item: WatchLaterItem,
    model: ViewLaterModel,
    modifier: Modifier = Modifier,
) {
    val stack = LocalNavBackStack.current
    OutlinedCard(
        modifier = modifier,
        onClick = {
            stack += AuthorRoute(item.author.user.id)
            if (AppConfig.watchLaterRemoveWhenClick) {
                model.deleteItem(item, true)
            }
        },
    ) {
        Row(
            modifier = Modifier.padding(5.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AsyncImage(
                model = item.author.user.profileImageUrls.content,
                modifier = Modifier.size(75.dp, 120.dp).clip(CardDefaults.shape),
                contentDescription = null,
                contentScale = ContentScale.Crop,
            )
            ListItem(
                overlineContent = {
                    Text(item.author.user.id.toString())
                },
                headlineContent = {
                    Text(item.author.user.name, maxLines = 1)
                },
                supportingContent = {
                    Text(item.author.user.account, maxLines = 1)
                },
                trailingContent = {
                    IconButton(
                        onClick = {
                            model.deleteItem(item)
                        },
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = null,
                        )
                    }
                },
            )
        }
    }
}

@Composable
private fun SeriesWatchLaterItem(
    item: WatchLaterItem,
    model: ViewLaterModel,
    modifier: Modifier = Modifier,
) {
    val stack = LocalNavBackStack.current
    OutlinedCard(
        modifier = modifier,
        onClick = {
            stack += NovelSeriesRoute(item.payload.toInt())
            if (AppConfig.watchLaterRemoveWhenClick) {
                model.deleteItem(item, true)
            }
        },
    ) {
        Row(
            modifier = Modifier.padding(5.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier.size(75.dp, 120.dp)
                    .clip(CardDefaults.shape)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Default.DateRange,
                    contentDescription = null,
                    modifier = Modifier.size(40.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            ListItem(
                overlineContent = {
                    Text(item.series.id.toString())
                },
                headlineContent = {
                    Text(item.series.title, maxLines = 1)
                },
                trailingContent = {
                    IconButton(
                        onClick = {
                            model.deleteItem(item)
                        },
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = null,
                        )
                    }
                },
            )
        }
    }
}
