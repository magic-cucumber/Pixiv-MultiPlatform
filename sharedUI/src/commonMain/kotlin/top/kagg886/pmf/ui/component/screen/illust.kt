package top.kagg886.pmf.ui.component.screen

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandIn
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.paging.LoadState
import androidx.paging.Pager
import androidx.paging.PagingData
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import coil3.compose.AsyncImagePainter
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import top.kagg886.pmf.LocalNavController
import top.kagg886.pmf.database.account.entity.AuthorDisplayed
import top.kagg886.pmf.database.account.entity.IllustCacheDisplayed
import top.kagg886.pmf.database.account.entity.ImageUrlsCache
import top.kagg886.pmf.i18n.Lang
import top.kagg886.pmf.i18n.illust_fetch_like
import top.kagg886.pmf.i18n.illust_fetch_load_failed_summary
import top.kagg886.pmf.i18n.illust_fetch_load_failed_title
import top.kagg886.pmf.i18n.illust_fetch_retry
import top.kagg886.pmf.i18n.illust_fetch_unlike
import top.kagg886.pmf.i18n.logger_open
import top.kagg886.pmf.ui.component.EmptyScreen
import top.kagg886.pmf.ui.component.LoadingIconButton
import top.kagg886.pmf.ui.component.LoadingIconButtonState
import top.kagg886.pmf.ui.component.ProgressableImage
import top.kagg886.pmf.ui.component.scroll.VerticalScrollbar
import top.kagg886.pmf.ui.component.scroll.rememberScrollbarAdapter
import top.kagg886.pmf.ui.screen.logger.LoggerRoute
import top.kagg886.pmf.ui.util.placeholder

/**
 * ================================================
 * Author:     iveou
 * Created on: 2026/8/10 14:54
 * ================================================
 */

private enum class IllustFetchScreenState {
    Loading,
    Success,
    Error,
}

@Composable
fun IllustFetchScreen(
    pager: Pager<Int, IllustCacheDisplayed>,
    columns: StaggeredGridCells,
    modifier: Modifier = Modifier,
    state: LazyStaggeredGridState = rememberLazyStaggeredGridState(),
    itemPadding: PaddingValues = PaddingValues(4.dp),
    onIllustItemClicked: suspend (IllustCacheDisplayed) -> Unit = {},
    onLikeItemClicked: suspend (IllustCacheDisplayed, Boolean) -> Unit = { _, _ -> },
    onLikeItemLongClicked: suspend (IllustCacheDisplayed) -> Unit = {},
) {
    val items = pager.flow.collectAsLazyPagingItems()
    val screenState = when (items.itemCount) {
        0 if items.loadState.refresh is LoadState.Loading -> IllustFetchScreenState.Loading
        0 if items.loadState.refresh is LoadState.Error -> IllustFetchScreenState.Error
        else -> IllustFetchScreenState.Success
    }
    val nav = LocalNavController.current
    IllustFetchContent(
        screenState = screenState,
        items = items,
        columns = columns,
        state = state,
        itemPadding = itemPadding,
        modifier = modifier,
        onIllustItemClicked = onIllustItemClicked,
        onLikeItemClicked = onLikeItemClicked,
        onLikeItemLongClicked = onLikeItemLongClicked,
        onViewLogClicked = { nav.navigate(LoggerRoute) },
    )
}

@Composable
private fun IllustFetchContent(
    screenState: IllustFetchScreenState,
    items: LazyPagingItems<IllustCacheDisplayed>,
    columns: StaggeredGridCells,
    state: LazyStaggeredGridState,
    itemPadding: PaddingValues,
    modifier: Modifier = Modifier,
    onIllustItemClicked: suspend (IllustCacheDisplayed) -> Unit = {},
    onLikeItemClicked: suspend (IllustCacheDisplayed, Boolean) -> Unit = { _, _ -> },
    onLikeItemLongClicked: suspend (IllustCacheDisplayed) -> Unit = {},
    onViewLogClicked: () -> Unit = {},
) {
    AnimatedContent(
        targetState = screenState,
        modifier = modifier,
        transitionSpec = {
            if (initialState == IllustFetchScreenState.Loading && targetState == IllustFetchScreenState.Success) {
                (fadeIn() + expandIn(expandFrom = Alignment.Center)) togetherWith
                        (fadeOut() + shrinkOut(shrinkTowards = Alignment.Center))
            } else {
                fadeIn() togetherWith fadeOut()
            }
        },
    ) { currentState ->
        when (currentState) {
            IllustFetchScreenState.Loading -> LoadingContent()

            IllustFetchScreenState.Error -> ErrorContent(
                onRetry = items::retry,
                onViewLogClicked = onViewLogClicked,
            )

            IllustFetchScreenState.Success -> IllustGridContent(
                items = items,
                columns = columns,
                state = state,
                itemPadding = itemPadding,
                onIllustItemClicked = onIllustItemClicked,
                onLikeItemClicked = onLikeItemClicked,
                onLikeItemLongClicked = onLikeItemLongClicked,
            )
        }
    }
}

@Composable
private fun LoadingContent() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
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
        title = { Text(stringResource(Lang.string.illust_fetch_load_failed_title)) },
        summary = { Text(stringResource(Lang.string.illust_fetch_load_failed_summary)) },
        actions = {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                OutlinedButton(onClick = onViewLogClicked) {
                    Text(stringResource(Lang.string.logger_open))
                }
                Button(onClick = onRetry) {
                    Text(stringResource(Lang.string.illust_fetch_retry))
                }
            }
        },
    )
}

@Composable
private fun IllustGridContent(
    items: LazyPagingItems<IllustCacheDisplayed>,
    columns: StaggeredGridCells,
    state: LazyStaggeredGridState,
    itemPadding: PaddingValues,
    onIllustItemClicked: suspend (IllustCacheDisplayed) -> Unit,
    onLikeItemClicked: suspend (IllustCacheDisplayed, Boolean) -> Unit,
    onLikeItemLongClicked: suspend (IllustCacheDisplayed) -> Unit,
) {
    val layoutDirection = LocalLayoutDirection.current
    val horizontalSpacing =
        (itemPadding.calculateLeftPadding(layoutDirection) + itemPadding.calculateRightPadding(layoutDirection)) / 2
    val verticalSpacing = (itemPadding.calculateTopPadding() + itemPadding.calculateBottomPadding()) / 2

    Column(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        ) {
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
                        items.peek(index)?.let { "illust-${it.illustId}" } ?: "placeholder-$index"
                    },
                ) { index ->
                    val illust = items[index]
                    if (illust == null) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(1f)
                                .placeholder(visible = true, shape = CardDefaults.shape),
                        )
                    } else {
                        IllustGridItem(
                            illust = illust,
                            onClick = onIllustItemClicked,
                            onLikeClicked = onLikeItemClicked,
                            onLikeLongClicked = onLikeItemLongClicked,
                        )
                    }
                }
            }

            VerticalScrollbar(
                adapter = rememberScrollbarAdapter(state),
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxHeight(),
            )
        }

        AnimatedVisibility(visible = items.loadState.refresh is LoadState.Loading) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun IllustGridItem(
    illust: IllustCacheDisplayed,
    onClick: suspend (IllustCacheDisplayed) -> Unit,
    onLikeClicked: suspend (IllustCacheDisplayed, Boolean) -> Unit,
    onLikeLongClicked: suspend (IllustCacheDisplayed) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var imageLoaded by remember(illust.illustId) { mutableStateOf(false) }
    var likeLoading by remember(illust.illustId) { mutableStateOf(false) }

    Card(onClick = { scope.launch { onClick(illust) } },) {
        Box {
            ProgressableImage(
                model = illust.imageUrls.content,
                contentDescription = illust.title,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(illust.width.toFloat() / illust.height.coerceAtLeast(1)),
                onState = { imageState ->
                    imageLoaded = imageState is AsyncImagePainter.State.Success
                },
                contentScale = ContentScale.Crop,
            )

            this@Card.AnimatedVisibility(
                visible = imageLoaded,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(4.dp),
                enter = fadeIn(),
                exit = fadeOut(),
            ) {
                LoadingIconButton(
                    state = if (likeLoading) {
                        LoadingIconButtonState.Loading
                    } else {
                        LoadingIconButtonState.NotLoading(illust.isBookmarked)
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
                                onLikeClicked(illust, !illust.isBookmarked)
                            } finally {
                                likeLoading = false
                            }
                        }
                    },
                    onLongClick = {
                        scope.launch { onLikeLongClicked(illust) }
                    },
                ) { buttonState ->
                    val bookmarked = when (buttonState) {
                        is LoadingIconButtonState.NotLoading<*> -> buttonState.state as? Boolean == true
                        else -> false
                    }
                    Icon(
                        imageVector = Icons.Default.Favorite,
                        contentDescription = stringResource(
                            if (bookmarked) Lang.string.illust_fetch_unlike else Lang.string.illust_fetch_like,
                        ),
                        tint = if (bookmarked) Color.Red else LocalContentColor.current,
                    )
                }
            }
        }
    }
}

private fun previewIllust(
    id: Long,
    bookmarked: Boolean = false,
    width: Int = 600,
    height: Int = 800,
) = IllustCacheDisplayed(
    illustId = id,
    title = "Illust $id",
    caption = "",
    type = "illust",
    authorId = 1L,
    createTime = 0L,
    pageCount = 1,
    width = width,
    height = height,
    sanityLevel = 0,
    xRestrict = 0,
    totalView = 0,
    totalBookmarks = 0,
    isBookmarked = bookmarked,
    illustAiType = 0,
    imageUrlsId = "img$id",
    author = AuthorDisplayed(
        userId = 1L,
        name = "author",
        account = "account",
        profileImageUrlsId = "profile",
        profileImageUrls = ImageUrlsCache(id = "profile"),
    ),
    imageUrls = ImageUrlsCache(id = "img$id", medium = ""),
    tags = emptyList(),
    metaPages = emptyList(),
)

@Preview(name = "Loading")
@Composable
private fun IllustFetchContentLoadingPreview() {
    val items = flowOf(PagingData.from(emptyList<IllustCacheDisplayed>())).collectAsLazyPagingItems()
    MaterialTheme {
        IllustFetchContent(
            screenState = IllustFetchScreenState.Loading,
            items = items,
            columns = StaggeredGridCells.Fixed(2),
            state = rememberLazyStaggeredGridState(),
            itemPadding = PaddingValues(4.dp),
        )
    }
}

@Preview(name = "Error")
@Composable
private fun IllustFetchContentErrorPreview() {
    val items = flowOf(PagingData.from(emptyList<IllustCacheDisplayed>())).collectAsLazyPagingItems()
    MaterialTheme {
        IllustFetchContent(
            screenState = IllustFetchScreenState.Error,
            items = items,
            columns = StaggeredGridCells.Fixed(2),
            state = rememberLazyStaggeredGridState(),
            itemPadding = PaddingValues(4.dp),
        )
    }
}

@Preview(name = "Success", showBackground = true)
@Composable
private fun IllustFetchContentSuccessPreview() {
    val items = flowOf(
        PagingData.from(
            listOf(
                previewIllust(1L, bookmarked = true, width = 600, height = 800),
                previewIllust(2L, width = 800, height = 600),
                previewIllust(3L, width = 600, height = 900),
            ),
        ),
    ).collectAsLazyPagingItems()
    MaterialTheme {
        IllustFetchContent(
            screenState = IllustFetchScreenState.Success,
            items = items,
            columns = StaggeredGridCells.Fixed(2),
            state = rememberLazyStaggeredGridState(),
            itemPadding = PaddingValues(4.dp),
        )
    }
}
