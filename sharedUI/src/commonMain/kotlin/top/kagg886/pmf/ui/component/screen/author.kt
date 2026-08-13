package top.kagg886.pmf.ui.component.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material.icons.outlined.PersonRemove
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.paging.Pager
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.itemKey
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import top.kagg886.pmf.LocalNavController
import top.kagg886.pmf.database.account.entity.UserDisplayed
import top.kagg886.pmf.database.account.entity.ImageUrlsCache
import top.kagg886.pmf.i18n.Lang
import top.kagg886.pmf.i18n.author_fetch_follow
import top.kagg886.pmf.i18n.author_fetch_load_failed_summary
import top.kagg886.pmf.i18n.author_fetch_load_failed_title
import top.kagg886.pmf.i18n.author_fetch_retry
import top.kagg886.pmf.i18n.author_fetch_unfollow
import top.kagg886.pmf.i18n.common_page_no_more_data
import top.kagg886.pmf.ui.component.LoadingIconButton
import top.kagg886.pmf.ui.component.LoadingIconButtonState
import top.kagg886.pmf.ui.component.ProgressableImage
import top.kagg886.pmf.ui.screen.logger.LoggerRoute
import top.kagg886.pmf.ui.util.placeholder

private val AuthorItemHeight = 112.dp

@Composable
fun AuthorFetchScreen(
    pager: Pager<Int, UserDisplayed>,
    columns: StaggeredGridCells,
    modifier: Modifier = Modifier,
    state: LazyStaggeredGridState = rememberLazyStaggeredGridState(),
    itemPadding: PaddingValues = PaddingValues(4.dp),
    onAuthorItemClicked: suspend (UserDisplayed) -> Unit = {},
    onFollowItemClicked: suspend (UserDisplayed, Boolean) -> Unit = { _, _ -> },
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
                title = { Text(stringResource(Lang.string.author_fetch_load_failed_title)) },
                summary = { Text(stringResource(Lang.string.author_fetch_load_failed_summary)) },
                retryText = { Text(stringResource(Lang.string.author_fetch_retry)) },
            )
        },
        successContent = { items, state ->
            AuthorListContent(
                items = items,
                columns = columns,
                state = state,
                itemPadding = itemPadding,
                onAuthorItemClicked = onAuthorItemClicked,
                onFollowItemClicked = onFollowItemClicked,
            )
        },
    )
}

@Composable
private fun AuthorListContent(
    items: LazyPagingItems<UserDisplayed>,
    columns: StaggeredGridCells,
    state: LazyStaggeredGridState,
    itemPadding: PaddingValues,
    onAuthorItemClicked: suspend (UserDisplayed) -> Unit,
    onFollowItemClicked: suspend (UserDisplayed, Boolean) -> Unit,
) {
    val layoutDirection = LocalLayoutDirection.current
    val horizontalSpacing =
        (itemPadding.calculateStartPadding(layoutDirection) + itemPadding.calculateEndPadding(layoutDirection)) / 2
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
                    key = items.itemKey { author -> "author-flow-${author.flowId}" },
                ) { index ->
                    val author = items[index]
                    if (author == null) {
                        Box(
                            Modifier
                                .animateItem()
                                .fillMaxWidth()
                                .height(AuthorItemHeight)
                                .placeholder(visible = true, shape = CardDefaults.shape),
                        )
                    } else {
                        AuthorListItem(
                            modifier = Modifier.animateItem(),
                            author = author,
                            onClick = onAuthorItemClicked,
                            onFollowClicked = onFollowItemClicked,
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
private fun AuthorListItem(
    modifier: Modifier = Modifier,
    author: UserDisplayed,
    onClick: suspend (UserDisplayed) -> Unit,
    onFollowClicked: suspend (UserDisplayed, Boolean) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var followLoading by remember(author.flowId) { mutableStateOf(false) }
    val followed = author.isFollowed == true

    Card(
        onClick = { scope.launch { onClick(author) } },
        modifier = modifier.fillMaxWidth().height(AuthorItemHeight),
    ) {
        ListItem(
            modifier = Modifier.fillMaxSize(),
            headlineContent = {
                Text(
                    text = author.name,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.titleMedium,
                )
            },
            supportingContent = {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = "@${author.account}",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodySmall,
                    )
                    author.comment?.takeIf(String::isNotBlank)?.let { comment ->
                        Text(
                            text = comment,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            },
            leadingContent = {
                ProgressableImage(
                    model = author.profileImageUrls.content,
                    contentDescription = author.name,
                    modifier = Modifier.size(64.dp).clip(CircleShape),
                    contentScale = ContentScale.Crop,
                )
            },
            trailingContent = {
                LoadingIconButton(
                    state = if (followLoading) {
                        LoadingIconButtonState.Loading
                    } else {
                        LoadingIconButtonState.NotLoading(followed)
                    },
                    colors = IconButtonDefaults.iconButtonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    ),
                    onClick = {
                        if (followLoading) return@LoadingIconButton
                        scope.launch {
                            followLoading = true
                            try {
                                onFollowClicked(author, !followed)
                            } finally {
                                followLoading = false
                            }
                        }
                    },
                ) { buttonState ->
                    val isFollowed =
                        (buttonState as? LoadingIconButtonState.NotLoading<*>)?.state as? Boolean == true
                    Icon(
                        imageVector = if (isFollowed) Icons.Outlined.PersonRemove else Icons.Outlined.PersonAdd,
                        contentDescription = stringResource(
                            if (isFollowed) Lang.string.author_fetch_unfollow else Lang.string.author_fetch_follow,
                        ),
                    )
                }
            },
        )
    }
}

private fun previewAuthor(id: Long, followed: Boolean = false) = UserDisplayed(
    userId = id,
    name = "Author $id",
    account = "author_$id",
    profileImageUrlsId = "user:$id:profile",
    isFollowed = followed,
    comment = "Creates illustrations and stories.",
    profileImageUrls = ImageUrlsCache(id = "user:$id:profile", medium = ""),
)

@Preview(name = "Author item", showBackground = true)
@Composable
private fun AuthorListItemPreview() {
    MaterialTheme {
        AuthorListItem(
            author = previewAuthor(1L, followed = true),
            onClick = {},
            onFollowClicked = { _, _ -> },
        )
    }
}
