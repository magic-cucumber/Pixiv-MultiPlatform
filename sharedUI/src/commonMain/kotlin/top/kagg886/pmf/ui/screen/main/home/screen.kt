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
import top.kagg886.pixko.module.user.RelatedUserResult
import top.kagg886.pixko.module.user.followUser
import top.kagg886.pixko.module.user.getRelatedUser
import top.kagg886.pixko.module.user.getRelatedUserNext
import top.kagg886.pixko.module.user.unFollowUser
import top.kagg886.pmf.ui.component.screen.AuthorFetchScreen
import top.kagg886.pmf.ui.component.screen.IllustFetchScreen
import top.kagg886.pmf.ui.component.screen.NovelFetchScreen
import top.kagg886.pmf.ui.repository.AuthorNextUrlRepo
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
//            //Illustration flow example retained for comparison.
//            val repo = remember(state.database) {
//                object : IllustNextUrlRepo(state.database,"recommend") {
//                    override suspend fun requestInitial(): IllustResult {
//                        delay(5.seconds)
//                        return state.client.getRecommendIllust()
//                    }
//
//                    override suspend fun requestNext(nextUrl: String): IllustResult {
//                        delay(5.seconds)
//                        val resp =
//                            state.client.getRecommendIllustNext(IllustResult(nextUrl = nextUrl, illusts = emptyList()))
//                                ?: return IllustResult(nextUrl = null, illusts = emptyList())
//
//                        return resp
//                    }
//
//                }
//            }
//
//            IllustFetchScreen(
//                pager = repo.pager,
//                columns = StaggeredGridCells.Adaptive(265.dp),
//                modifier = Modifier.fillMaxSize(),
//                onLikeItemClicked = {a,b-> delay(3.seconds) }
//            )

            //Novel flow example retained for comparison.
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



//            val relatedId = 115863841
//            val authorRepo = remember(state.database, relatedId) {
//                object : AuthorNextUrlRepo(state.database, "author:related:${relatedId}") {
//                    override suspend fun requestInitial() = run {
//                        val result = state.client.getRelatedUser(relatedId.toLong())
//                        loadedPage(nextRequest = result.next_url, users = result.user_previews)
//                    }
//
//                    override suspend fun requestNext(nextUrl: String) = run {
//                        val result = state.client.getRelatedUserNext(RelatedUserResult(next_url = nextUrl))
//                            ?: return@run loadedPage(nextRequest = null, users = emptyList())
//                        loadedPage(nextRequest = result.next_url, users = result.user_previews)
//                    }
//                }
//            }
//
//            AuthorFetchScreen(
//                pager = authorRepo.pager,
//                columns = StaggeredGridCells.Adaptive(360.dp),
//                modifier = Modifier.fillMaxSize(),
//                onFollowItemClicked = { _, _ -> delay(3.seconds)},
//            )
        }

        else -> Unit
    }


}
