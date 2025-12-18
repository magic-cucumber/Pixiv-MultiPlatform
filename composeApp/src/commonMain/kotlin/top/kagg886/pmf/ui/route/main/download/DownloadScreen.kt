package top.kagg886.pmf.ui.route.main.download

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.paging.compose.collectAsLazyPagingItems
import coil3.compose.AsyncImage
import kotlin.random.Random
import kotlin.time.Clock
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel
import org.orbitmvi.orbit.compose.collectAsState
import top.kagg886.pixko.module.illust.get
import top.kagg886.pmf.LocalNavBackStack
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
import top.kagg886.pmf.ui.route.main.detail.illust.IllustDetailRoute
import top.kagg886.pmf.ui.route.main.detail.novel.NovelDetailRoute
import top.kagg886.pmf.util.logger
import top.kagg886.pmf.util.stringResource

@Composable
fun DownloadScreen() {
    val model = koinViewModel<DownloadScreenModel>()
    val state by model.collectAsState()
    DownloadContent(model, state)
}

@Composable
private fun DownloadContent(model: DownloadScreenModel, state: DownloadScreenState) {
    Box(Modifier.fillMaxSize()) {
        val lazyColumnState = rememberLazyListState()
        val scope = rememberCoroutineScope()

        run container@{
            when (state) {
                is DownloadScreenState.Loading -> {
                    Loading()
                }

                is DownloadScreenState.Loaded -> {
                    val data = state.data.collectAsLazyPagingItems()
                    when {
                        !data.loadState.isIdle && data.itemCount == 0 -> Loading()

                        else -> {
                            if (data.itemCount == 0 && data.loadState.isIdle) {
                                ErrorPage(text = stringResource(Res.string.page_is_empty)) {
                                    data.retry()
                                }
                                return@container
                            }
                            LazyColumn(modifier = Modifier.fillMaxSize().padding(5.dp), state = lazyColumnState) {
                                items(data.itemCount, key = {
                                    data.peek(it)?.id ?: Random(Clock.System.now().toEpochMilliseconds()).nextInt().apply {
                                        logger.w("download item is null. this can't happened. data.itemCount = ${data.itemCount}, data.length()${data.itemSnapshotList.size}")
                                    }
                                }) { i ->
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
                        }
                    }
                }
            }
        }

        var searchDialog by remember {
            mutableStateOf(false)
        }
        var fabExpanded by remember {
            mutableStateOf(false)
        }

        if (searchDialog) {
            var keyword by remember { mutableStateOf(state.keyword) }
            var selectedType by remember { mutableStateOf(state.type) }
            var searchInData by remember { mutableStateOf(state.searchInData) }

            AlertDialog(
                onDismissRequest = { searchDialog = false },
                title = {
                    Text(stringResource(Res.string.search))
                },
                text = {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        // 搜索类型选择 - 按钮组
                        Text(
                            text = "搜索类型",
                            style = MaterialTheme.typography.labelLarge,
                        )
                        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                            SegmentedButton(
                                selected = selectedType == null,
                                onClick = { selectedType = null },
                                shape = SegmentedButtonDefaults.itemShape(0, 3),
                                label = { Text(stringResource(Res.string.all)) },
                            )
                            SegmentedButton(
                                selected = selectedType == DownloadItemType.ILLUST,
                                onClick = { selectedType = DownloadItemType.ILLUST },
                                shape = SegmentedButtonDefaults.itemShape(1, 3),
                                label = { Text(stringResource(Res.string.illust)) },
                            )
                            SegmentedButton(
                                selected = selectedType == DownloadItemType.NOVEL,
                                onClick = { selectedType = DownloadItemType.NOVEL },
                                shape = SegmentedButtonDefaults.itemShape(2, 3),
                                label = { Text(stringResource(Res.string.novel)) },
                            )
                        }

                        // 关键词输入框
                        OutlinedTextField(
                            value = keyword,
                            onValueChange = { keyword = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text(stringResource(Res.string.keyword)) },
                            placeholder = { Text(stringResource(Res.string.please_input_keyword)) },
                            singleLine = true,
                        )

                        // 搜索元数据选项
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(
                                checked = searchInData,
                                onCheckedChange = { searchInData = it },
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(Res.string.search_metadata))
                        }
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            model.search(
                                keyWord = keyword,
                                searchInData = searchInData,
                                type = selectedType,
                            )
                            searchDialog = false
                        },
                    ) {
                        Text(stringResource(Res.string.confirm))
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = { searchDialog = false },
                    ) {
                        Text(stringResource(Res.string.cancel))
                    }
                },
            )
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

                    SmallFloatingActionButton(
                        onClick = {
                            searchDialog = true
                            fabExpanded = false
                        },
                        modifier = Modifier.padding(bottom = 8.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "search",
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

@Composable
private fun IllustDownloadItem(
    item: top.kagg886.pmf.backend.database.dao.DownloadItem,
    model: DownloadScreenModel,
    modifier: Modifier = Modifier,
) {
    val stack = LocalNavBackStack.current
    OutlinedCard(
        modifier = modifier,
        onClick = { stack += IllustDetailRoute(item.illust) },
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
                    when (item.progress) {
                        -1f if !item.success -> {
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

                        -1f if item.success -> {
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
    val stack = LocalNavBackStack.current
    OutlinedCard(
        modifier = modifier,
        onClick = { stack += NovelDetailRoute(item.novel.id.toLong()) },
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
                    when (item.progress) {
                        -1f if !item.success -> {
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

                        -1f if item.success -> {
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
