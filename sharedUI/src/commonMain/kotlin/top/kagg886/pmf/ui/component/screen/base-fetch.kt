package top.kagg886.pmf.ui.component.screen

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.paging.LoadState
import androidx.paging.Pager
import androidx.paging.PagingData
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import kotlinx.coroutines.flow.flowOf
import org.jetbrains.compose.resources.stringResource
import top.kagg886.pmf.i18n.Lang
import top.kagg886.pmf.i18n.common_page_empty_summary
import top.kagg886.pmf.i18n.common_page_empty_title
import top.kagg886.pmf.i18n.common_page_no_more_data
import top.kagg886.pmf.i18n.common_page_refresh
import top.kagg886.pmf.ui.component.scroll.VerticalScrollbar
import top.kagg886.pmf.ui.component.scroll.rememberScrollbarAdapter
import top.kagg886.pmf.ui.util.createContentAnim

private enum class FetchScreenState {
    Loading,
    Empty,
    Success,
    Error,
}

@Composable
internal fun <Key : Any, Item : Any, ScrollState : Any> BaseFetchScreen(
    pager: Pager<Key, Item>,
    scrollState: ScrollState,
    errorContent: @Composable (onRetry: () -> Unit) -> Unit,
    successContent: @Composable (items: LazyPagingItems<Item>, scrollState: ScrollState) -> Unit,
    modifier: Modifier = Modifier,
    loadingContent: @Composable () -> Unit = { LoadingContent() },
    emptyContent: @Composable (onRefresh: () -> Unit) -> Unit = { onRefresh -> EmptyContent(onRefresh) },
) {
    val items = pager.flow.collectAsLazyPagingItems()
    val screenState = when (items.itemCount) {
        0 if items.loadState.refresh is LoadState.Loading -> FetchScreenState.Loading
        0 if items.loadState.refresh is LoadState.Error -> FetchScreenState.Error
        0 -> FetchScreenState.Empty
        else -> FetchScreenState.Success
    }

    BaseFetchContent(
        screenState = screenState,
        items = items,
        scrollState = scrollState,
        errorContent = errorContent,
        successContent = successContent,
        modifier = modifier,
        loadingContent = loadingContent,
        emptyContent = emptyContent,
    )
}

@Composable
private fun <Item : Any, ScrollState : Any> BaseFetchContent(
    screenState: FetchScreenState,
    items: LazyPagingItems<Item>,
    scrollState: ScrollState,
    errorContent: @Composable (onRetry: () -> Unit) -> Unit,
    successContent: @Composable (items: LazyPagingItems<Item>, scrollState: ScrollState) -> Unit,
    modifier: Modifier = Modifier,
    loadingContent: @Composable () -> Unit = { LoadingContent() },
    emptyContent: @Composable (onRefresh: () -> Unit) -> Unit = { onRefresh ->
        EmptyContent(onRefresh)
    },
) {

    AnimatedContent(
        targetState = screenState,
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
        transitionSpec = createContentAnim {
            initialState == FetchScreenState.Loading && targetState == FetchScreenState.Success
        },
    ) { currentState ->
        when (currentState) {
            FetchScreenState.Loading -> loadingContent()
            FetchScreenState.Error -> errorContent(items::retry)
            FetchScreenState.Empty -> emptyContent(items::refresh)
            FetchScreenState.Success -> Box(Modifier.fillMaxSize()) {
                successContent(items, scrollState)
                VerticalScrollbar(
                    adapter = rememberScrollbarAdapter(scrollState),
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .fillMaxHeight(),
                )
            }
        }
    }
}

@Composable
internal fun PagingAppendFooter(
    loadState: LoadState,
    noMoreContent: @Composable () -> Unit,
) {
    AnimatedContent(
        targetState = loadState,
        transitionSpec = { fadeIn() togetherWith fadeOut() },
    ) { appendState ->
        when (appendState) {
            LoadState.Loading -> LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            is LoadState.NotLoading -> if (appendState.endOfPaginationReached) {
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) {
                    noMoreContent()
                }
            }

            is LoadState.Error -> Unit
        }
    }
}

@Composable
private fun BaseFetchPreview(state: FetchScreenState) {
    val items = flowOf(
        PagingData.from(
            if (state == FetchScreenState.Success) {
                listOf("Preview item 1", "Preview item 2", "Preview item 3")
            } else {
                emptyList()
            },
        ),
    ).collectAsLazyPagingItems()
    val scrollState = rememberLazyListState()

    MaterialTheme {
        BaseFetchContent(
            screenState = state,
            items = items,
            scrollState = scrollState,
            errorContent = { onRetry ->
                ErrorContent(
                    onRetry = onRetry,
                    onViewLogClicked = {},
                    title = { Text(stringResource(Lang.string.common_page_empty_title)) },
                    summary = { Text(stringResource(Lang.string.common_page_empty_summary)) },
                    retryText = { Text(stringResource(Lang.string.common_page_refresh)) },
                )
            },
            successContent = { pagingItems, state ->
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    state = state,
                ) {
                    items(
                        count = pagingItems.itemCount,
                        key = { index -> "preview-$index" },
                    ) { index ->
                        pagingItems[index]?.let { item ->
                            Text(
                                text = item,
                                modifier = Modifier.fillMaxWidth().padding(16.dp),
                            )
                        }
                    }
                    item {
                        PagingAppendFooter(loadState = pagingItems.loadState.append) {
                            Text(stringResource(Lang.string.common_page_no_more_data))
                        }
                    }
                }
            },
        )
    }
}

@Preview(name = "Loading")
@Composable
private fun BaseFetchScreenLoadingPreview() {
    BaseFetchPreview(FetchScreenState.Loading)
}

@Preview(name = "Empty")
@Composable
private fun BaseFetchScreenEmptyPreview() {
    BaseFetchPreview(FetchScreenState.Empty)
}

@Preview(name = "Error")
@Composable
private fun BaseFetchScreenErrorPreview() {
    BaseFetchPreview(FetchScreenState.Error)
}

@Preview(name = "Success", showBackground = true)
@Composable
private fun BaseFetchScreenSuccessPreview() {
    BaseFetchPreview(FetchScreenState.Success)
}
