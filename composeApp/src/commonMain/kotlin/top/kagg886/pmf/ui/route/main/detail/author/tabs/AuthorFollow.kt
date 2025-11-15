package top.kagg886.pmf.ui.route.main.detail.author.tabs

import androidx.compose.runtime.Composable
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import top.kagg886.pixko.module.user.UserInfo
import top.kagg886.pixko.module.user.getFollowingList
import top.kagg886.pmf.ui.util.AuthorFetchScreen
import top.kagg886.pmf.ui.util.AuthorFetchViewModel
import top.kagg886.pmf.ui.util.flowOf
import top.kagg886.pmf.ui.util.page

@Composable
fun AuthorFollow(user: UserInfo) {
    val model = koinViewModel<AuthorFollowViewModel>(key = "${user.user.id}") {
        parametersOf(user.user.id)
    }
    AuthorFetchScreen(model)
}

class AuthorFollowViewModel(val user: Int) : AuthorFetchViewModel() {
    override fun source() = flowOf(20) { params ->
        params.page { i -> client.getFollowingList(user) { page = i } }
    }
}
