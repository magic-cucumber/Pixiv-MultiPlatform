package top.kagg886.pmf.ui.component.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.staggeredgrid.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.paging.Pager
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.itemKey
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import top.kagg886.pmf.LocalNavController
import top.kagg886.pmf.database.account.entity.*
import top.kagg886.pmf.i18n.*
import top.kagg886.pmf.ui.component.ContextualFlowRow
import top.kagg886.pmf.ui.component.LoadingIconButton
import top.kagg886.pmf.ui.component.LoadingIconButtonState
import top.kagg886.pmf.ui.component.ProgressableImage
import top.kagg886.pmf.ui.screen.logger.LoggerRoute
import top.kagg886.pmf.ui.util.placeholder
import top.kagg886.pmf.util.TraceEffect
import top.kagg886.pmf.util.d
import top.kagg886.pmf.util.i

private val NovelItemHeight = 152.dp
private val NovelTagRowHeight = 24.dp

private data class NovelScrollSnapshot(
    val firstVisibleIndex: Int,
    val lastVisibleIndex: Int,
    val totalItemsCount: Int,
    val firstVisibleOffset: Int,
    val isScrollInProgress: Boolean,
)

@Composable
fun NovelFetchScreen(
    pager: Pager<Int, NovelCacheDisplayed>,
    columns: StaggeredGridCells,
    modifier: Modifier = Modifier,
    state: LazyStaggeredGridState = rememberLazyStaggeredGridState(),
    itemPadding: PaddingValues = PaddingValues(4.dp),
    onNovelItemClicked: suspend (NovelCacheDisplayed) -> Unit = {},
    onLikeItemClicked: suspend (NovelCacheDisplayed, Boolean) -> Unit = { _, _ -> },
    onLikeItemLongClicked: suspend (NovelCacheDisplayed) -> Unit = {},
) {
    val nav = LocalNavController.current
    BaseFetchScreen(
        pager = pager,
        scrollState = state,
        modifier = modifier,
        errorContent = { onRetry ->
            ErrorContent(
                onRetry = onRetry,
                onViewLogClicked = { nav.navigate(LoggerRoute) },
                title = { Text(stringResource(Lang.string.novel_fetch_load_failed_title)) },
                summary = { Text(stringResource(Lang.string.novel_fetch_load_failed_summary)) },
                retryText = { Text(stringResource(Lang.string.novel_fetch_retry)) },
            )
        },
        successContent = { items, state ->
            NovelListContent(
                items = items,
                columns = columns,
                state = state,
                itemPadding = itemPadding,
                onNovelItemClicked = onNovelItemClicked,
                onLikeItemClicked = onLikeItemClicked,
                onLikeItemLongClicked = onLikeItemLongClicked,
            )
        },
    )
}

@Composable
private fun NovelListContent(
    items: LazyPagingItems<NovelCacheDisplayed>,
    columns: StaggeredGridCells,
    state: LazyStaggeredGridState,
    itemPadding: PaddingValues,
    onNovelItemClicked: suspend (NovelCacheDisplayed) -> Unit,
    onLikeItemClicked: suspend (NovelCacheDisplayed, Boolean) -> Unit,
    onLikeItemLongClicked: suspend (NovelCacheDisplayed) -> Unit,
) {
    val layoutDirection = LocalLayoutDirection.current
    val horizontalSpacing =
        (itemPadding.calculateLeftPadding(layoutDirection) + itemPadding.calculateRightPadding(layoutDirection)) / 2
    val verticalSpacing = (itemPadding.calculateTopPadding() + itemPadding.calculateBottomPadding()) / 2
    val refreshState = items.loadState.refresh.describeForUiLog()
    val appendState = items.loadState.append.describeForUiLog()

    TraceEffect(
        "NovelListContent",
        items.itemCount,
        refreshState,
        appendState,
    ) {
        i(
            message = "Novel list content state changed (itemCount=${items.itemCount}, " +
                "refresh=$refreshState, append=$appendState)",
        )
    }

    TraceEffect("NovelListMount", state, items) {
        val visibleItems = state.layoutInfo.visibleItemsInfo
        val firstVisibleIndex = visibleItems.firstOrNull()?.index ?: -1
        val lastVisibleIndex = visibleItems.lastOrNull()?.index ?: -1
        d(
            message = "Novel list content mounted (itemCount=${items.itemCount}, " +
                "visibleRange=$firstVisibleIndex..$lastVisibleIndex, " +
                "totalItems=${state.layoutInfo.totalItemsCount}, " +
                "firstVisibleOffset=${state.firstVisibleItemScrollOffset})",
        )
    }

    TraceEffect("NovelListScroll", state, items) {
        snapshotFlow {
            val visibleItems = state.layoutInfo.visibleItemsInfo
            NovelScrollSnapshot(
                firstVisibleIndex = visibleItems.firstOrNull()?.index ?: -1,
                lastVisibleIndex = visibleItems.lastOrNull()?.index ?: -1,
                totalItemsCount = state.layoutInfo.totalItemsCount,
                firstVisibleOffset = state.firstVisibleItemScrollOffset,
                isScrollInProgress = state.isScrollInProgress,
            )
        }.distinctUntilChanged { previous, current ->
            previous.firstVisibleIndex == current.firstVisibleIndex &&
                previous.lastVisibleIndex == current.lastVisibleIndex &&
                previous.totalItemsCount == current.totalItemsCount &&
                previous.isScrollInProgress == current.isScrollInProgress
        }.collect { snapshot ->
            d(
                message = "Novel list scroll snapshot changed (visibleRange=${snapshot.firstVisibleIndex}..${snapshot.lastVisibleIndex}, " +
                    "totalItems=${snapshot.totalItemsCount}, firstVisibleOffset=${snapshot.firstVisibleOffset}, " +
                    "scrollInProgress=${snapshot.isScrollInProgress})",
            )
        }
    }

    Column(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxWidth().weight(1f)) {
            LazyVerticalStaggeredGrid(
                columns = columns,
                state = state,
                modifier = Modifier.fillMaxSize(),
                contentPadding = itemPadding,
                verticalItemSpacing = verticalSpacing,
                horizontalArrangement = Arrangement.spacedBy(horizontalSpacing),
            ) {
                items(
                    count = items.itemCount,
                    key = items.itemKey { novel -> "novel-flow-${novel.flowId}" },
                ) { index ->
                    val novel = items[index]
                    if (novel == null) {
                        Box(
                            Modifier
                                .animateItem()
                                .fillMaxWidth()
                                .height(NovelItemHeight)
                                .placeholder(visible = true, shape = CardDefaults.shape),
                        )
                    } else {
                        NovelListItem(
                            modifier = Modifier.animateItem(),
                            novel = novel,
                            onClick = onNovelItemClicked,
                            onLikeClicked = onLikeItemClicked,
                            onLikeLongClicked = onLikeItemLongClicked,
                        )
                    }
                }

                item(span = StaggeredGridItemSpan.FullLine) {
                    PagingAppendFooter(loadState = items.loadState.append) {
                        Text(stringResource(Lang.string.common_page_no_more_data))
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun NovelListItem(
    modifier: Modifier = Modifier,
    novel: NovelCacheDisplayed,
    onClick: suspend (NovelCacheDisplayed) -> Unit,
    onLikeClicked: suspend (NovelCacheDisplayed, Boolean) -> Unit,
    onLikeLongClicked: suspend (NovelCacheDisplayed) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var likeLoading by remember(novel.flowId) { mutableStateOf(false) }
    Card(
        onClick = { scope.launch { onClick(novel) } },
        modifier = modifier.fillMaxWidth().height(NovelItemHeight),
    ) {
        ListItem(
            modifier = Modifier.fillMaxSize(),
            headlineContent = {
                Text(
                    text = novel.title,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.titleMedium,
                )
            },
            supportingContent = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = listOfNotNull(
                            novel.author.name,
                            novel.series?.title?.takeIf(String::isNotBlank),
                        ).joinToString(" · "),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodySmall,
                    )
                    ContextualFlowRow(
                        items = novel.tags,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(NovelTagRowHeight),
                        maxLines = 1,
                        overflowContent = { count, _ ->
                            TooltipBox(
                                positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above,4.dp),
                                tooltip = {
                                    RichTooltip {
                                        FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                            for (tag in novel.tags.takeLast(count)) {
                                                NovelTag(tag.translatedName ?: tag.name)
                                            }
                                        }
                                    }
                                },
                                state = rememberTooltipState()
                            ) {
                                NovelTag(text = "+$count")
                            }
                        }
                    ) { tag ->
                        NovelTag(text = tag.translatedName ?: tag.name)
                    }
                }
            },
            leadingContent = {
                ProgressableImage(
                    model = novel.imageUrls.content,
                    contentDescription = novel.title,
                    modifier = Modifier.size(width = 84.dp, height = 124.dp),
                    contentScale = ContentScale.Crop,
                )
            },
            trailingContent = {
                LoadingIconButton(
                    state = if (likeLoading) {
                        LoadingIconButtonState.Loading
                    } else {
                        LoadingIconButtonState.NotLoading(novel.isBookmarked)
                    },
                    colors = IconButtonDefaults.iconButtonColors(
                        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f),
                        contentColor = MaterialTheme.colorScheme.onSurface,
                    ),
                    onClick = {
                        if (likeLoading) return@LoadingIconButton
                        scope.launch {
                            likeLoading = true
                            try {
                                onLikeClicked(novel, !novel.isBookmarked)
                            } finally {
                                likeLoading = false
                            }
                        }
                    },
                    onLongClick = { scope.launch { onLikeLongClicked(novel) } },
                ) { buttonState ->
                    val bookmarked =
                        (buttonState as? LoadingIconButtonState.NotLoading<*>)?.state as? Boolean == true
                    Icon(
                        imageVector = if (bookmarked) Icons.Default.Favorite else Icons.Outlined.FavoriteBorder,
                        contentDescription = stringResource(
                            if (bookmarked) Lang.string.novel_fetch_unlike else Lang.string.novel_fetch_like,
                        ),
                        tint = if (bookmarked) Color.Red else LocalContentColor.current,
                    )
                }
            },
        )
    }
}

@Composable
private fun NovelTag(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 4.dp, vertical = 2.dp)
            .then(modifier),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        style = MaterialTheme.typography.labelSmall,
    )
}

private fun previewNovel(id: Long, bookmarked: Boolean = false) = NovelCacheDisplayed(
    flowId = id,
    novelId = id,
    title = "Novel $id",
    caption = "",
    imageUrlsId = "novel:$id:cover",
    authorId = 1L,
    createTime = 0L,
    textLength = 12000,
    seriesId = 1L,
    isBookmarked = bookmarked,
    totalBookmarks = 42,
    totalView = 100,
    totalComments = 3,
    isAI = false,
    isR18 = false,
    isR18G = false,
    author = AuthorDisplayed(
        userId = 1L,
        name = "author",
        account = "account",
        profileImageUrlsId = "profile",
        profileImageUrls = ImageUrlsCache(id = "profile", medium = ""),
    ),
    imageUrls = ImageUrlsCache(id = "novel:$id:cover", medium = ""),
    tags = listOf(
        TagCache(id = "fantasy", name = "fantasy", translatedName = "奇幻"),
        TagCache(id = "adventure", name = "adventure", translatedName = "冒险"),
        TagCache(id = "magic", name = "magic", translatedName = "魔法"),
        TagCache(id = "mystery", name = "mystery", translatedName = "悬疑"),
        TagCache(id = "friendship", name = "friendship", translatedName = "友情"),
        TagCache(id = "long-tag", name = "long-tag", translatedName = "较长的标签"),
    ),
    series = NovelSeriesCache(
        id = 1L,
        title = "Sample series",
        caption = null,
        contentCount = null,
        totalCharacterCount = null,
        userId = 1L,
    ),
)

@OptIn(ExperimentalLayoutApi::class)
@Preview(name = "Novel item", showBackground = true)
@Composable
private fun NovelListItemPreview() {
    MaterialTheme {
        NovelListItem(
            novel = previewNovel(1L, bookmarked = true),
            onClick = {},
            onLikeClicked = { _, _ -> },
            onLikeLongClicked = {},
        )
    }
}
