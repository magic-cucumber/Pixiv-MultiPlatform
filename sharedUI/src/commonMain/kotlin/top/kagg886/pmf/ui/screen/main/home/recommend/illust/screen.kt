package top.kagg886.pmf.ui.screen.main.home.recommend.illust

import androidx.compose.runtime.Composable
import kotlinx.serialization.Serializable
import top.kagg886.pmf.util.nav3.SerializableNavKey
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import org.orbitmvi.orbit.compose.collectAsState
import org.orbitmvi.orbit.compose.collectSideEffect
import top.kagg886.pmf.database.account.entity.IllustCacheDisplayed
import top.kagg886.pmf.ui.component.screen.IllustFetchScreen
import top.kagg886.pmf.ui.component.screen.LoadingContent
import top.kagg886.pmf.ui.screen.main.MainViewModel
import top.kagg886.pmf.ui.screen.main.MainViewModelState
import top.kagg886.pmf.ui.screen.main.home.HomeViewModel
import top.kagg886.pmf.ui.screen.main.home.HomeViewModelEffect
import top.kagg886.pmf.util.TraceEffect
import top.kagg886.pmf.util.i
import top.kagg886.pmf.util.nav3.viewModel

@Serializable
data object RecommendIllustRoute : SerializableNavKey

@Composable
fun RecommendIllustScreen() {
    val main = viewModel<MainViewModel>()
    val mainState by main.collectAsState()
    val accountState = mainState as MainViewModelState.LoadSuccess
    val model = viewModel<RecommendIllustViewModel> {
        RecommendIllustViewModel(accountState.database, accountState.client)
    }
    val home = viewModel<HomeViewModel>()
    val state by model.collectAsState()
    val gridState = rememberLazyStaggeredGridState()
    val scope = rememberCoroutineScope()

    home.collectSideEffect { effect ->
        when (effect) {
            HomeViewModelEffect.FABClicked -> {
                if (gridState.canScrollBackward) {
                    scope.launch { gridState.animateScrollToItem(0) }
                } else {
                    model.refresh()
                }
            }
            HomeViewModelEffect.RefreshRequested -> model.refresh()
            is HomeViewModelEffect.NavigateTo -> Unit
        }
    }

    DisposableEffect(home) {
        onDispose {
            home.endRefresh()
            home.setCanBackToTop(false)
        }
    }

    TraceEffect("RecommendIllustScroll", gridState) {
        snapshotFlow { gridState.canScrollBackward }.distinctUntilChanged().collect { canBackToTop ->
            i("Recommended illustrations scroll state changed (canBackToTop=$canBackToTop)")
            model.reportCanBackToTop(canBackToTop)
        }
    }

    when (val current = state) {
        RecommendIllustState.Loading -> LoadingContent()
        is RecommendIllustState.LoadSuccess -> {
            val items = current.repo.pager.flow.collectAsLazyPagingItems()
            model.collectSideEffect { effect ->
                when (effect) {
                    RecommendIllustSideEffect.Refresh -> items.refresh()
                    is RecommendIllustSideEffect.RefreshingChanged -> {
                        if (effect.value) home.startRefresh() else home.endRefresh()
                    }
                    is RecommendIllustSideEffect.CanBackToTopChanged -> home.setCanBackToTop(effect.value)
                }
            }
            val refreshState = items.loadState.refresh
            TraceEffect("RecommendIllustRefresh", refreshState) {
                i("Recommended illustrations refresh state changed (state=$refreshState)")
                model.reportRefreshing(refreshState is LoadState.Loading)
            }
            RecommendIllustContent(
                items = items,
                gridState = gridState,
                onLikeItemClicked = { illust, liked ->
                    if (liked) model.processBookmark(illust).join()
                    else model.processUnbookmark(illust).join()
                },
            )
        }
    }
}

@Composable
private fun RecommendIllustContent(
    items: LazyPagingItems<IllustCacheDisplayed>,
    gridState: LazyStaggeredGridState,
    onLikeItemClicked: suspend (IllustCacheDisplayed, Boolean) -> Unit,
) {
    IllustFetchScreen(
        items = items,
        state = gridState,
        columns = StaggeredGridCells.Adaptive(265.dp),
        modifier = Modifier.fillMaxSize(),
        onLikeItemClicked = onLikeItemClicked,
    )
}
