package top.kagg886.pmf.ui.route.main.download

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.key.Key.Companion.R
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.koin.koinScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import coil3.compose.AsyncImage
import kotlinx.coroutines.launch
import org.orbitmvi.orbit.compose.collectAsState
import top.kagg886.pixko.module.illust.get
import top.kagg886.pmf.backend.Platform
import top.kagg886.pmf.backend.currentPlatform
import top.kagg886.pmf.backend.database.dao.DownloadItemType
import top.kagg886.pmf.backend.database.dao.illust
import top.kagg886.pmf.backend.database.dao.novel
import top.kagg886.pmf.res.*
import top.kagg886.pmf.ui.component.ErrorPage
import top.kagg886.pmf.ui.component.Loading
import top.kagg886.pmf.ui.component.icon.Download
import top.kagg886.pmf.ui.component.icon.Save
import top.kagg886.pmf.ui.route.main.detail.illust.IllustDetailScreen
import top.kagg886.pmf.ui.route.main.detail.novel.NovelDetailScreen
import top.kagg886.pmf.ui.util.collectAsLazyPagingItems
import top.kagg886.pmf.util.stringResource

class DownloadScreen : Screen {
    @Composable
    override fun Content() {
        val model = koinScreenModel<DownloadScreenModel>()
        val state by model.collectAsState()
        DownloadContent(
            model,
            state,
        )
    }

    @Composable
    private fun DownloadContent(model: DownloadScreenModel, state: DownloadScreenState) {
        when (state) {
            DownloadScreenState.Loading -> {
                Loading()
            }

            is DownloadScreenState.Loaded -> {
                val data = state.data.collectAsLazyPagingItems()
                val scope = rememberCoroutineScope()
                when {
                    !data.loadState.isIdle && data.itemCount == 0 -> Loading()
                    else -> {
                        if (data.itemCount == 0 && data.loadState.isIdle) {
                            ErrorPage(text = stringResource(Res.string.page_is_empty)) {
                                data.retry()
                            }
                            return
                        }
                        Box(Modifier.fillMaxSize()) {
                            val lazyColumnState = rememberLazyListState()
                            LazyColumn(modifier = Modifier.fillMaxSize().padding(5.dp), state = lazyColumnState) {
                                items(data.itemCount, key = { data.peek(it)!!.id }) { i ->
                                    val item = data[i]!!
                                    when (item.meta) {
                                        DownloadItemType.ILLUST -> {
                                            IllustDownloadItem(
                                                item = item,
                                                model = model,
                                                modifier = Modifier.padding(5.dp),
                                            )
                                        }

                                        DownloadItemType.NOVEL -> {
                                            NovelDownloadItem(
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

                            var searchDialog by remember {
                                mutableStateOf(false)
                            }
                            var fabExpanded by remember {
                                mutableStateOf(false)
                            }

                            Column(
                                modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
                                horizontalAlignment = Alignment.End,
                            ) {
                                AnimatedVisibility(
                                    visible = fabExpanded,
                                    enter = fadeIn() + expandVertically(expandFrom = Alignment.Bottom),
                                    exit = fadeOut() + shrinkVertically(shrinkTowards = Alignment.Bottom),
                                ) {
                                    Column(
                                        horizontalAlignment = Alignment.End,
                                    ) {
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
                                                contentDescription = "back to top"
                                            )
                                        }

                                        SmallFloatingActionButton(
                                            onClick = {
                                                searchDialog = true
                                                fabExpanded = false
                                            },
                                            modifier = Modifier.padding(bottom = 8.dp),
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Search,
                                                contentDescription = "search"
                                            )
                                        }
                                    }
                                }

                                // 主 FAB
                                FloatingActionButton(
                                    onClick = { fabExpanded = !fabExpanded },
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Menu,
                                        contentDescription = "menu"
                                    )
                                }
                            }

                            if (searchDialog) {
                                AlertDialog(
                                    onDismissRequest = { searchDialog = false },
                                    title = {

                                    },
                                    confirmButton = {

                                    }
                                )
                            }
                        }
                    }
                }

            }
        }
    }

    @Composable
    private fun IllustDownloadItem(
        item: top.kagg886.pmf.backend.database.dao.DownloadItem,
        model: DownloadScreenModel,
        modifier: Modifier = Modifier,
    ) {
        val nav = LocalNavigator.currentOrThrow
        OutlinedCard(
            modifier = modifier,
            onClick = { nav.push(IllustDetailScreen(item.illust)) },
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
                        when {
                            item.progress == -1f && !item.success -> {
                                IconButton(
                                    onClick = {
                                        model.startIllustDownload(item.illust)
                                    },
                                ) {
                                    Icon(
                                        imageVector = Download,
                                        contentDescription = null,
                                    )
                                }
                            }

                            item.progress == -1f && item.success -> {
                                Row {
                                    IconButton(
                                        onClick = {
                                            model.startIllustDownloadOr(item) {
                                                model.saveToExternalFile(item)
                                            }
                                        },
                                    ) {
                                        Icon(
                                            imageVector = Save,
                                            contentDescription = null,
                                        )
                                    }
                                    if (currentPlatform !is Platform.Desktop) {
                                        IconButton(
                                            onClick = {
                                                model.startIllustDownloadOr(item) {
                                                    model.shareFile(item)
                                                }
                                            },
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Share,
                                                contentDescription = null,
                                            )
                                        }
                                    }
                                }
                            }

                            else -> CircularProgressIndicator()
                        }
                    },
                    supportingContent = {
                        if (item.success) {
                            Text(stringResource(Res.string.download_success))
                            return@ListItem
                        }
                        if (item.progress != -1f) {
                            LinearProgressIndicator(
                                modifier = Modifier.fillMaxWidth(0.8f),
                                progress = { item.progress },
                            )
                        }
                    },
                )
            }
        }
    }

    @Composable
    private fun NovelDownloadItem(
        item: top.kagg886.pmf.backend.database.dao.DownloadItem,
        model: DownloadScreenModel,
        modifier: Modifier = Modifier,
    ) {
        val nav = LocalNavigator.currentOrThrow
        OutlinedCard(
            modifier = modifier,
            onClick = { nav.push(NovelDetailScreen(item.novel.id.toLong())) },
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
                        when {
                            item.progress == -1f && !item.success -> {
                                IconButton(
                                    onClick = {
                                        model.startNovelDownload(item.novel)
                                    },
                                ) {
                                    Icon(
                                        imageVector = Download,
                                        contentDescription = null,
                                    )
                                }
                            }

                            item.progress == -1f && item.success -> {
                                Row {
                                    IconButton(
                                        onClick = {
                                            model.startNovelDownloadOr(item) {
                                                model.saveToExternalFile(item)
                                            }
                                        },
                                    ) {
                                        Icon(
                                            imageVector = Save,
                                            contentDescription = null,
                                        )
                                    }
                                    if (currentPlatform !is Platform.Desktop) {
                                        IconButton(
                                            onClick = {
                                                model.startNovelDownloadOr(item) {
                                                    model.shareFile(item)
                                                }
                                            },
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Share,
                                                contentDescription = null,
                                            )
                                        }
                                    }
                                }
                            }

                            else -> CircularProgressIndicator()
                        }
                    },
                    supportingContent = {
                        if (item.success) {
                            Text(stringResource(Res.string.download_success))
                            return@ListItem
                        }
                        if (item.progress != -1f) {
                            LinearProgressIndicator(
                                modifier = Modifier.fillMaxWidth(0.8f),
                                progress = { item.progress },
                            )
                        }
                    },
                )
            }
        }
    }
}
