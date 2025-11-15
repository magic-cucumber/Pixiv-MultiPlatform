package top.kagg886.pmf.ui.route.main.detail.author.tabs

import androidx.compose.runtime.Composable
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import top.kagg886.pixko.module.user.UserInfo
import top.kagg886.pixko.module.user.getUserLikeIllust
import top.kagg886.pixko.module.user.getUserLikeIllustNext
import top.kagg886.pmf.ui.util.IllustFetchScreen
import top.kagg886.pmf.ui.util.IllustFetchViewModel
import top.kagg886.pmf.ui.util.flowOf
import top.kagg886.pmf.ui.util.next

@Composable
fun AuthorIllustBookmark(user: UserInfo) {
    val model = koinViewModel<AuthorIllustBookmarkViewModel>(key = "user_illust_bookmark_${user.user.id}") {
        parametersOf(user.user.id)
    }
    IllustFetchScreen(model)
}

class AuthorIllustBookmarkViewModel(val user: Int) : IllustFetchViewModel() {
    override fun source() = flowOf(30) { params ->
        params.next(
            { client.getUserLikeIllust(user) },
            { ctx -> client.getUserLikeIllustNext(ctx) },
            { ctx -> ctx.illusts },
        )
    }
}
