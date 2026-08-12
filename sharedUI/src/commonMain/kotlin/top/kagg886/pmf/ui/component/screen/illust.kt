package top.kagg886.pmf.ui.component.screen

import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.staggeredgrid.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.paging.Pager
import androidx.paging.compose.LazyPagingItems
import coil3.compose.AsyncImagePainter
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import top.kagg886.pmf.LocalNavController
import top.kagg886.pmf.database.account.entity.AuthorDisplayed
import top.kagg886.pmf.database.account.entity.IllustCacheDisplayed
import top.kagg886.pmf.database.account.entity.ImageUrlsCache
import top.kagg886.pmf.i18n.*
import top.kagg886.pmf.ui.component.LoadingIconButton
import top.kagg886.pmf.ui.component.LoadingIconButtonState
import top.kagg886.pmf.ui.component.ProgressableImage
import top.kagg886.pmf.ui.screen.logger.LoggerRoute
import top.kagg886.pmf.ui.util.placeholder

/**
 * ================================================
 * Author:     iveou
 * Created on: 2026/8/10 14:54
 * ================================================
 */

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
    val nav = LocalNavController.current
    BaseFetchScreen(
        pager = pager,
        scrollState = state,
        modifier = modifier,
        errorContent = { onRetry ->
            ErrorContent(
                onRetry = onRetry,
                onViewLogClicked = { nav.navigate(LoggerRoute) },
                title = { Text(stringResource(Lang.string.illust_fetch_load_failed_title)) },
                summary = { Text(stringResource(Lang.string.illust_fetch_load_failed_summary)) },
                retryText = { Text(stringResource(Lang.string.illust_fetch_retry)) },
            )
        },
        successContent = { items, state ->
            IllustGridContent(
                items = items,
                columns = columns,
                state = state,
                itemPadding = itemPadding,
                onIllustItemClicked = onIllustItemClicked,
                onLikeItemClicked = onLikeItemClicked,
                onLikeItemLongClicked = onLikeItemLongClicked,
            )
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
                        items.peek(index)?.let { "illust-${it.illustId}-$index" } ?: "placeholder-$index"
                    },
                ) { index ->
                    val illust = items[index]
                    if (illust == null) {
                        Box(
                            modifier = Modifier
                                .animateItem()
                                .fillMaxWidth()
                                .aspectRatio(1f)
                                .placeholder(visible = true, shape = CardDefaults.shape),
                        )
                    } else {
                        IllustGridItem(
                            modifier = Modifier.animateItem(),
                            illust = illust,
                            onClick = onIllustItemClicked,
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

@Composable
private fun IllustGridItem(
    modifier: Modifier = Modifier,
    illust: IllustCacheDisplayed,
    onClick: suspend (IllustCacheDisplayed) -> Unit,
    onLikeClicked: suspend (IllustCacheDisplayed, Boolean) -> Unit,
    onLikeLongClicked: suspend (IllustCacheDisplayed) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var imageLoaded by remember(illust.illustId) { mutableStateOf(false) }
    var likeLoading by remember(illust.illustId) { mutableStateOf(false) }

    Card(modifier = modifier, onClick = { scope.launch { onClick(illust) } }) {
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
                        imageVector = if (bookmarked) Icons.Default.Favorite else Icons.Outlined.FavoriteBorder,
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

@Preview(name = "Illust item", showBackground = true)
@Composable
private fun IllustGridItemPreview() {
    MaterialTheme {
        IllustGridItem(
            illust = previewIllust(1L, bookmarked = true, width = 600, height = 800),
            onClick = {},
            onLikeClicked = { _, _ -> },
            onLikeLongClicked = {},
        )
    }
}
