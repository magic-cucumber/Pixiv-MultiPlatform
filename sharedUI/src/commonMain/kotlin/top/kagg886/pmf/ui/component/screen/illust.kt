package top.kagg886.pmf.ui.component.screen

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.paging.Pager
import top.kagg886.pmf.database.account.entity.IllustCacheDisplayed
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
    Card(Modifier.placeholder(true, shape = CardDefaults.shape, background = CardDefaults.cardColors().containerColor, highlight = CardDefaults.cardColors().contentColor)) {
        Box(Modifier.size(200.dp,120.dp), contentAlignment = Alignment.Center) {
            Text("Hello, World!")
        }
    }
}
