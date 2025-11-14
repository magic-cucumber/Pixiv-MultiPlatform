package top.kagg886.pmf.ui.route.main.detail.author.tabs

import androidx.compose.runtime.Composable
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import top.kagg886.pixko.module.user.UserInfo
import top.kagg886.pixko.module.user.getUserLikeNovel
import top.kagg886.pixko.module.user.getUserLikeNovelNext
import top.kagg886.pmf.ui.util.NovelFetchScreen
import top.kagg886.pmf.ui.util.NovelFetchViewModel
import top.kagg886.pmf.ui.util.flowOf
import top.kagg886.pmf.ui.util.next

@Composable
fun AuthorNovelBookmark(user: UserInfo) {
    val model = koinViewModel<AuthorNovelBookmarkViewModel>(key = "user_novel_bookmark_${user.user.id}") {
        parametersOf(user.user.id)
    }
    NovelFetchScreen(model)
}

class AuthorNovelBookmarkViewModel(val user: Int) : NovelFetchViewModel() {
    override fun source() = flowOf(20) { params ->
        params.next(
            { client.getUserLikeNovel(user) },
            { ctx -> client.getUserLikeNovelNext(ctx) },
            { ctx -> ctx.novels },
        )
    }
}
