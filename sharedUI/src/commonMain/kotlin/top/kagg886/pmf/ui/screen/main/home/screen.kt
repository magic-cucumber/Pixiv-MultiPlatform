package top.kagg886.pmf.ui.screen.main.home

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.serialization.Serializable
import org.orbitmvi.orbit.compose.collectAsState
import top.kagg886.pixko.module.illust.IllustResult
import top.kagg886.pixko.module.illust.getRecommendIllust
import top.kagg886.pixko.module.illust.getRecommendIllustNext
import top.kagg886.pixko.module.novel.NovelResult
import top.kagg886.pixko.module.novel.getRecommendNovel
import top.kagg886.pixko.module.novel.getRecommendNovelNext
import top.kagg886.pmf.ui.component.screen.IllustFetchScreen
import top.kagg886.pmf.ui.component.screen.NovelFetchScreen
import top.kagg886.pmf.ui.repository.IllustNextUrlRepo
import top.kagg886.pmf.ui.repository.NovelNextUrlRepo
import top.kagg886.pmf.ui.screen.main.MainViewModel
import top.kagg886.pmf.ui.screen.main.MainViewModelState
import top.kagg886.pmf.util.nav3.SerializableNavKey
import top.kagg886.pmf.util.nav3.viewModel
import kotlin.time.Duration.Companion.seconds

/**
 * ================================================
 * Author:     iveou
 * Created on: 2026/7/29 15:22
 * ================================================
 */

@Serializable
data object HomeRoute: SerializableNavKey


@Composable
fun HomeScreen() {
    val model = viewModel<MainViewModel>()
    val state by model.collectAsState()

    when (val state = state) {
        is MainViewModelState.LoadSuccess -> {
            /* Illustration flow example retained for comparison.
            val repo = remember(state.database) {
                object : IllustNextUrlRepo(state.database,"recommend") {
                    override suspend fun requestInitial(): IllustResult {
                        return state.client.getRecommendIllust()
                    }

                    override suspend fun requestNext(nextUrl: String): IllustResult {
                        val resp =
                            state.client.getRecommendIllustNext(IllustResult(nextUrl = nextUrl, illusts = emptyList()))
                                ?: return IllustResult(nextUrl = null, illusts = emptyList())

                        return resp
                    }

                }
            }

            IllustFetchScreen(
                pager = repo.pager,
                columns = StaggeredGridCells.Adaptive(265.dp),
                modifier = Modifier.fillMaxSize(),
                onLikeItemClicked = {a,b-> delay(3.seconds) }
            )
            */

            val novelRepo = remember(state.database) {
                object : NovelNextUrlRepo(state.database, "novel:recommend") {
                    override suspend fun requestInitial(): NovelResult {
                        return state.client.getRecommendNovel()
                    }

                    override suspend fun requestNext(nextUrl: String): NovelResult {
                        return state.client.getRecommendNovelNext(
                            NovelResult(nextUrl = nextUrl, novels = emptyList()),
                        ) ?: NovelResult(nextUrl = null, novels = emptyList())
                    }
                }
            }

            NovelFetchScreen(
                pager = novelRepo.pager,
                columns = StaggeredGridCells.Adaptive(420.dp),
                modifier = Modifier.fillMaxSize(),
                onLikeItemClicked = { _, _ -> delay(3.seconds) },
            )
        }

        else -> Unit
    }


}
