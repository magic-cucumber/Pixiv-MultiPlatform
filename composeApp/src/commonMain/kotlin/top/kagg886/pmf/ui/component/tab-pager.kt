package top.kagg886.pmf.ui.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.SecondaryScrollableTabRow
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.zIndex
import kotlin.math.max
import kotlin.math.min
import kotlinx.coroutines.launch
import top.kagg886.pmf.ui.util.KeyListenerFromGlobalPipe

@Composable
@Deprecated("use TabContainer instead", replaceWith = ReplaceWith("TabContainer"))
fun <T> TabContainer(
    modifier: Modifier = Modifier,
    state: MutableState<Int>,
    tab: List<T>,
    scrollable: Boolean = false,
    page: @Composable (Int) -> Unit,
) {
    TabContainer(
        modifier = modifier,
        tab = tab,
        current = tab[state.value],
        onCurrentChange = { state.value = tab.indexOf(it) },
        scrollable = scrollable,
        page = { page(tab.indexOf(it)) },
    )
}

/**
 * # 上面是Tab，下面是Pager的封装
 * @param tab 标题列表
 * @param tabTitle 标题显示的composable
 * @param current 当前选中的tab index
 * @param onCurrentChange tab切换的回调
 */
@Composable
fun <T> TabContainer(
    modifier: Modifier = Modifier,
    tab: List<T>,
    tabTitle: @Composable (T) -> Unit = { Text(it.toString()) },
    current: T = tab[0],
    onCurrentChange: (T) -> Unit,
    scrollable: Boolean = false,
    page: @Composable (T) -> Unit,
) {
    val started by rememberUpdatedState(current)

    val currentIndex = remember(current, tab) {
        tab.indexOf(current)
    }

    val pagerState = rememberPagerState(tab.indexOf(started)) { tab.size }
    LaunchedEffect(pagerState.currentPage) {
        onCurrentChange(tab[pagerState.currentPage])
    }
    val scope = rememberCoroutineScope()
    Column(modifier) {
        val content = @Composable {
            for (i in tab.indices) {
                Tab(
                    selected = pagerState.currentPage == i,
                    onClick = {
                        scope.launch {
                            pagerState.animateScrollToPage(i)
                        }
                    },
                    text = {
                        tabTitle(tab[i])
                    },
                )
            }
        }
        if (scrollable) {
            SecondaryScrollableTabRow(
                selectedTabIndex = currentIndex,
                modifier = Modifier.fillMaxWidth().zIndex(2f),
                divider = {},
                tabs = content,
            )
        } else {
            SecondaryTabRow(
                selectedTabIndex = currentIndex,
                modifier = Modifier.fillMaxWidth().zIndex(2f),
                tabs = content,
            )
        }

        KeyListenerFromGlobalPipe {
            if (it.type != KeyEventType.KeyUp) return@KeyListenerFromGlobalPipe
            when (it.key) {
                Key.DirectionRight -> {
                    pagerState.animateScrollToPage(
                        min(
                            pagerState.currentPage + 1,
                            pagerState.pageCount - 1,
                        ),
                    )
                }

                Key.DirectionLeft -> {
                    pagerState.animateScrollToPage(max(pagerState.currentPage - 1, 0))
                }
            }
        }

        HorizontalPager(
            state = pagerState,
        ) {
            page(tab[it])
        }
    }
}
