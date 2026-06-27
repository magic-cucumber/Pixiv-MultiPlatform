package top.kagg886.pmf.ui.route.main.follow

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import top.kagg886.pixko.module.user.UserLikePublicity
import top.kagg886.pmf.LocalNavBackStack
import top.kagg886.pmf.res.*
import top.kagg886.pmf.ui.component.icon.View
import top.kagg886.pmf.ui.component.icon.ViewOff
import top.kagg886.pmf.ui.util.AuthorFetchScreen
import top.kagg886.pmf.ui.util.removeLastOrNullWorkaround
import top.kagg886.pmf.util.stringResource

@Serializable
data object FollowRoute : NavKey

@Composable
fun FollowScreen() {
    val stack = LocalNavBackStack.current
    var restrict by rememberSaveable { mutableStateOf(UserLikePublicity.PUBLIC) }
    val model = koinViewModel<FollowViewModel>(key = "follow_$restrict") {
        parametersOf(restrict)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(stringResource(Res.string.my_follow))
                },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            stack.removeLastOrNullWorkaround()
                        },
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = null,
                        )
                    }
                },
                actions = {
                    val isPublic = restrict == UserLikePublicity.PUBLIC
                    IconButton(
                        onClick = {
                            restrict = if (isPublic) UserLikePublicity.PRIVATE else UserLikePublicity.PUBLIC
                        },
                    ) {
                        Icon(
                            imageVector = if (isPublic) View else ViewOff,
                            contentDescription = stringResource(
                                if (isPublic) Res.string.public else Res.string.private,
                            ),
                        )
                    }
                },
            )
        },
    ) {
        Box(Modifier.padding(it).fillMaxSize()) {
            AuthorFetchScreen(model)
        }
    }
}
