package top.kagg886.pmf.ui.route.main.detail.novel

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import top.kagg886.pmf.LocalNavBackStack
import top.kagg886.pmf.res.*
import top.kagg886.pmf.ui.util.NovelFetchScreen
import top.kagg886.pmf.ui.util.removeLastOrNullWorkaround
import top.kagg886.pmf.util.stringResource

@Serializable
data class NovelSimilarRoute(val id: Long) : NavKey

@Composable
fun NovelSimilarScreen(route: NovelSimilarRoute) {
    val id = route.id
    val similarModel = koinViewModel<NovelSimilarViewModel>(key = "similar_novel_$id") {
        parametersOf(id)
    }
    val stack = LocalNavBackStack.current
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.find_similar_novel)) },
                navigationIcon = {
                    IconButton(onClick = { stack.removeLastOrNullWorkaround() }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = null,
                        )
                    }
                },
            )
        },
    ) {
        Box(Modifier.padding(it)) {
            NovelFetchScreen(similarModel)
        }
    }
}
