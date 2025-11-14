package top.kagg886.pmf.ui.util

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import io.ktor.http.Url
import top.kagg886.pmf.res.*
import top.kagg886.pmf.ui.route.main.detail.author.AuthorRoute
import top.kagg886.pmf.ui.route.main.detail.illust.IllustDetailPreFetchRoute
import top.kagg886.pmf.ui.route.main.detail.novel.NovelDetailRoute
import top.kagg886.pmf.util.stringResource

@Composable
fun rememberSupportPixivNavigateUriHandler(stack: NavBackStack<NavKey>): UriHandler {
    val origin = LocalUriHandler.current
    var wantToOpenLink by remember {
        mutableStateOf("")
    }

    val showLink by remember {
        derivedStateOf {
            wantToOpenLink.isNotBlank()
        }
    }

    if (showLink) {
        AlertDialog(
            onDismissRequest = {
                wantToOpenLink = ""
            },
            title = {
                Text(stringResource(Res.string.jump_browser_tips))
            },
            text = {
                Text(stringResource(Res.string.jump_browser_tips_question, wantToOpenLink))
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        origin.openUri(wantToOpenLink)
                    },
                ) {
                    Text(stringResource(Res.string.yes))
                }
            },
        )
    }

    return remember(origin) {
        object : UriHandler {
            override fun openUri(url: String) {
                val unit = kotlin.runCatching {
                    if (url.contains("pixiv.net")) {
                        val uri = Url(url.trim())
                        when {
                            uri.encodedPath.startsWith("/users/") -> stack += AuthorRoute(uri.encodedPath.split("/")[2].toInt())
                            uri.encodedPath.startsWith("/novel/show.php") -> stack += NovelDetailRoute(uri.encodedPath.split("=")[1].toLong())
                            uri.encodedPath.startsWith("/artworks/") -> stack += IllustDetailPreFetchRoute(uri.encodedPath.split("/")[2].toLong())
                        }
                        return@runCatching true
                    }
                    if (url.startsWith("pixiv://")) {
                        val uri = Url(url.trim())
                        when (uri.host) {
                            "novels" -> stack += AuthorRoute(uri.encodedPath.substring(1).toInt())
                            "illusts" -> stack += IllustDetailPreFetchRoute(uri.encodedPath.substring(1).toLong())
                            "users" -> stack += AuthorRoute(uri.encodedPath.substring(1).toInt())
                        }
                        return@runCatching true
                    }
                    false
                }
                if (unit.isFailure || !unit.getOrThrow()) {
                    wantToOpenLink = url.trim()
                }
            }
        }
    }
}
