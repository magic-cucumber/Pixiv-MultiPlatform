package top.kagg886.pmf.ui.route.main.detail.author.tabs

import androidx.compose.runtime.Composable
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import org.orbitmvi.orbit.compose.collectSideEffect
import top.kagg886.pixko.module.user.UserInfo
import top.kagg886.pixko.module.user.getUserIllust
import top.kagg886.pmf.LocalNavBackStack
import top.kagg886.pmf.LocalSnackBarHost
import top.kagg886.pmf.ui.util.IllustFetchScreen
import top.kagg886.pmf.ui.util.IllustFetchSideEffect
import top.kagg886.pmf.ui.util.IllustFetchViewModel
import top.kagg886.pmf.ui.util.flowOf
import top.kagg886.pmf.ui.util.page

@Composable
fun AuthorIllust(user: UserInfo) {
    val model = koinViewModel<AuthorIllustViewModel>(key = "user_illust_${user.user.id}") {
        parametersOf(user.user.id)
    }
    val stack = LocalNavBackStack.current
    val snackbarHostState = LocalSnackBarHost.current
    model.collectSideEffect { effect ->
        when (effect) {
            is IllustFetchSideEffect.Toast -> {
                snackbarHostState.showSnackbar(effect.msg)
            }

            is IllustFetchSideEffect.NavigateIllustDetail -> {
                stack += effect.route
            }
        }
    }
    IllustFetchScreen(model)
}

class AuthorIllustViewModel(val user: Int) : IllustFetchViewModel() {
    override fun source() = flowOf(30) { params ->
        params.page { i -> client.getUserIllust(user.toLong(), i) }
    }
}
