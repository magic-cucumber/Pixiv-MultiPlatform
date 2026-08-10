package top.kagg886.pmf.ui.component.screen

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.paging.Pager
import top.kagg886.pmf.database.account.entity.IllustCacheDisplayed

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

}
