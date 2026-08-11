package top.kagg886.pmf.ui.component.screen

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandIn
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.paging.LoadState
import androidx.paging.Pager
import androidx.paging.PagingData
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import top.kagg886.pmf.LocalNavController
import top.kagg886.pmf.database.account.entity.AuthorDisplayed
import top.kagg886.pmf.database.account.entity.ImageUrlsCache
import top.kagg886.pmf.database.account.entity.NovelCacheDisplayed
import top.kagg886.pmf.database.account.entity.NovelSeriesCache
import top.kagg886.pmf.database.account.entity.TagCache
import top.kagg886.pmf.i18n.Lang
import top.kagg886.pmf.i18n.logger_open
import top.kagg886.pmf.i18n.novel_fetch_like
import top.kagg886.pmf.i18n.novel_fetch_load_failed_summary
import top.kagg886.pmf.i18n.novel_fetch_load_failed_title
import top.kagg886.pmf.i18n.novel_fetch_retry
import top.kagg886.pmf.i18n.novel_fetch_unlike
import top.kagg886.pmf.ui.component.EmptyScreen
import top.kagg886.pmf.ui.component.LoadingIconButton
import top.kagg886.pmf.ui.component.LoadingIconButtonState
import top.kagg886.pmf.ui.component.ProgressableImage
import top.kagg886.pmf.ui.component.scroll.VerticalScrollbar
import top.kagg886.pmf.ui.component.scroll.rememberScrollbarAdapter
import top.kagg886.pmf.ui.screen.logger.LoggerRoute
import top.kagg886.pmf.ui.util.placeholder

private val NovelItemHeight = 152.dp

private enum class NovelFetchScreenState {
    Loading,
    Success,
    Error,
}

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
    val items = pager.flow.collectAsLazyPagingItems()
    val screenState = when (items.itemCount) {
        0 if items.loadState.refresh is LoadState.Loading -> NovelFetchScreenState.Loading
        0 if items.loadState.refresh is LoadState.Error -> NovelFetchScreenState.Error
        else -> NovelFetchScreenState.Success
    }
    val nav = LocalNavController.current
    NovelFetchContent(
        screenState = screenState,
        items = items,
        columns = columns,
        state = state,
        itemPadding = itemPadding,
        modifier = modifier,
        onNovelItemClicked = onNovelItemClicked,
        onLikeItemClicked = onLikeItemClicked,
        onLikeItemLongClicked = onLikeItemLongClicked,
        onViewLogClicked = { nav.navigate(LoggerRoute) },
    )
}

@Composable
private fun NovelFetchContent(
    screenState: NovelFetchScreenState,
    items: LazyPagingItems<NovelCacheDisplayed>,
    columns: StaggeredGridCells,
    state: LazyStaggeredGridState,
    itemPadding: PaddingValues,
    modifier: Modifier = Modifier,
    onNovelItemClicked: suspend (NovelCacheDisplayed) -> Unit = {},
    onLikeItemClicked: suspend (NovelCacheDisplayed, Boolean) -> Unit = { _, _ -> },
    onLikeItemLongClicked: suspend (NovelCacheDisplayed) -> Unit = {},
    onViewLogClicked: () -> Unit = {},
) {
    AnimatedContent(
        targetState = screenState,
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
        transitionSpec = {
            if (initialState == NovelFetchScreenState.Loading && targetState == NovelFetchScreenState.Success) {
                (fadeIn() + expandIn(expandFrom = Alignment.Center)) togetherWith
                    (fadeOut() + shrinkOut(shrinkTowards = Alignment.Center))
            } else {
                fadeIn() togetherWith fadeOut()
            }
        },
    ) { currentState ->
        when (currentState) {
            NovelFetchScreenState.Loading -> LoadingContent()
            NovelFetchScreenState.Error -> ErrorContent(items::retry, onViewLogClicked)
            NovelFetchScreenState.Success -> NovelListContent(
                items = items,
                columns = columns,
                state = state,
                itemPadding = itemPadding,
                onNovelItemClicked = onNovelItemClicked,
                onLikeItemClicked = onLikeItemClicked,
                onLikeItemLongClicked = onLikeItemLongClicked,
            )
        }
    }
}

@Composable
private fun LoadingContent() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

@Composable
private fun ErrorContent(onRetry: () -> Unit, onViewLogClicked: () -> Unit) {
    EmptyScreen(
        icon = {
            Icon(
                imageVector = Icons.Outlined.ErrorOutline,
                contentDescription = null,
                modifier = Modifier.padding(8.dp),
            )
        },
        title = { Text(stringResource(Lang.string.novel_fetch_load_failed_title)) },
        summary = { Text(stringResource(Lang.string.novel_fetch_load_failed_summary)) },
        actions = {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                OutlinedButton(onClick = onViewLogClicked) {
                    Text(stringResource(Lang.string.logger_open))
                }
                Button(onClick = onRetry) {
                    Text(stringResource(Lang.string.novel_fetch_retry))
                }
            }
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
                    key = { index ->
                        items.peek(index)?.let { "novel-${it.novelId}" } ?: "novel-placeholder-$index"
                    },
                ) { index ->
                    val novel = items[index]
                    if (novel == null) {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(NovelItemHeight)
                                .placeholder(visible = true, shape = CardDefaults.shape),
                        )
                    } else {
                        NovelListItem(
                            novel = novel,
                            onClick = onNovelItemClicked,
                            onLikeClicked = onLikeItemClicked,
                            onLikeLongClicked = onLikeItemLongClicked,
                        )
                    }
                }
            }
            VerticalScrollbar(
                adapter = rememberScrollbarAdapter(state),
                modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
            )
        }
        AnimatedVisibility(visible = items.loadState.refresh is LoadState.Loading) {
            LinearProgressIndicator(Modifier.fillMaxWidth())
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun NovelListItem(
    novel: NovelCacheDisplayed,
    onClick: suspend (NovelCacheDisplayed) -> Unit,
    onLikeClicked: suspend (NovelCacheDisplayed, Boolean) -> Unit,
    onLikeLongClicked: suspend (NovelCacheDisplayed) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var likeLoading by remember(novel.novelId) { mutableStateOf(false) }

    Card(
        onClick = { scope.launch { onClick(novel) } },
        modifier = Modifier.fillMaxWidth().height(NovelItemHeight),
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
                    FlowRow(
                        modifier = Modifier.fillMaxWidth().clipToBounds(),
                        maxLines = 1,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        novel.tags.forEach { tag ->
                            Text(
                                text = tag.translatedName ?: tag.name,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                                    .padding(horizontal = 4.dp, vertical = 2.dp),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.labelSmall,
                            )
                        }
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

private fun previewNovel(id: Long, bookmarked: Boolean = false) = NovelCacheDisplayed(
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

@Preview(name = "Loading")
@Composable
private fun NovelFetchContentLoadingPreview() {
    val items = flowOf(PagingData.from(emptyList<NovelCacheDisplayed>())).collectAsLazyPagingItems()
    MaterialTheme {
        NovelFetchContent(
            screenState = NovelFetchScreenState.Loading,
            items = items,
            columns = StaggeredGridCells.Fixed(1),
            state = rememberLazyStaggeredGridState(),
            itemPadding = PaddingValues(4.dp),
        )
    }
}

@Preview(name = "Error")
@Composable
private fun NovelFetchContentErrorPreview() {
    val items = flowOf(PagingData.from(emptyList<NovelCacheDisplayed>())).collectAsLazyPagingItems()
    MaterialTheme {
        NovelFetchContent(
            screenState = NovelFetchScreenState.Error,
            items = items,
            columns = StaggeredGridCells.Fixed(1),
            state = rememberLazyStaggeredGridState(),
            itemPadding = PaddingValues(4.dp),
        )
    }
}

@Preview(name = "Success", showBackground = true)
@Composable
private fun NovelFetchContentSuccessPreview() {
    val items = flowOf(
        PagingData.from(
            listOf(
                previewNovel(1L, bookmarked = true),
                previewNovel(2L),
            ),
        ),
    ).collectAsLazyPagingItems()
    MaterialTheme {
        NovelFetchContent(
            screenState = NovelFetchScreenState.Success,
            items = items,
            columns = StaggeredGridCells.Fixed(1),
            state = rememberLazyStaggeredGridState(),
            itemPadding = PaddingValues(4.dp),
        )
    }
}
