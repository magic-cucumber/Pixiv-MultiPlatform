package top.kagg886.pmf.ui.route.login.v2

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.buildAnnotatedString
import androidx.navigation3.runtime.NavKey
import io.github.kdroidfilter.webview.request.RequestInterceptor
import io.github.kdroidfilter.webview.request.WebRequest
import io.github.kdroidfilter.webview.request.WebRequestInterceptResult
import io.github.kdroidfilter.webview.web.LoadingState
import io.github.kdroidfilter.webview.web.WebView
import io.github.kdroidfilter.webview.web.WebViewNavigator
import io.github.kdroidfilter.webview.web.rememberWebViewNavigator
import io.github.kdroidfilter.webview.web.rememberWebViewState
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import org.koin.compose.viewmodel.koinViewModel
import org.orbitmvi.orbit.compose.collectAsState
import org.orbitmvi.orbit.compose.collectSideEffect
import top.kagg886.pixko.PixivAccountFactory
import top.kagg886.pmf.LocalNavBackStack
import top.kagg886.pmf.LocalSnackBarHost
import top.kagg886.pmf.backend.PlatformEngine
import top.kagg886.pmf.backend.currentPlatform
import top.kagg886.pmf.backend.pixiv.PixivConfig
import top.kagg886.pmf.res.*
import top.kagg886.pmf.ui.component.Loading
import top.kagg886.pmf.ui.component.guide.GuideScaffold
import top.kagg886.pmf.ui.route.main.recommend.RecommendRoute
import top.kagg886.pmf.ui.util.withClickable
import top.kagg886.pmf.ui.util.withLink
import top.kagg886.pmf.util.setText
import top.kagg886.pmf.util.stringResource

@Serializable
data class LoginRoute(val clearOldSession: Boolean = false) : NavKey {
    init {
        if (clearOldSession) {
            PixivConfig.clear()
        }
    }
}

@Composable
fun LoginScreen() {
    val stack = LocalNavBackStack.current
    val model = koinViewModel<LoginScreenViewModel>()
    val snack = LocalSnackBarHost.current

    model.collectSideEffect {
        when (it) {
            LoginSideEffect.NavigateToMain -> {
                stack[0] = RecommendRoute
            }

            is LoginSideEffect.Toast -> {
                snack.showSnackbar(it.msg)
            }
        }
    }

    val state by model.collectAsState()
    WaitLoginContent(state, model)
}

@Composable
private fun WaitLoginContent(a: LoginViewState, model: LoginScreenViewModel) {
    AnimatedContent(
        targetState = a,
        modifier = Modifier.fillMaxSize(),
    ) { state ->
        when (state) {
            LoginViewState.WaitChooseLogin -> {
                GuideScaffold(
                    title = {
                        Text(stringResource(Res.string.login_wizard))
                    },
                    subTitle = {},
                    confirmButton = {
                        Button(
                            onClick = {
                                model.selectLoginType(LoginType.BrowserLogin)
                            },
                        ) {
                            Text(stringResource(Res.string.use_browser_login))
                        }
                    },
                    skipButton = {
                        TextButton(
                            onClick = {
                                model.selectLoginType(LoginType.InputTokenLogin)
                            },
                        ) {
                            Text(stringResource(Res.string.use_token_login))
                        }
                    },
                    content = {
                        Text(
                            stringResource(Res.string.login_guide),
                        )
                    },
                )
            }

            is LoginViewState.LoginType -> {
                when (state) {
                    LoginViewState.LoginType.InputTokenLogin -> {
                        var text by remember {
                            mutableStateOf("")
                        }
                        AlertDialog(
                            onDismissRequest = {},
                            confirmButton = {
                                Button(
                                    onClick = {
                                        model.challengeRefreshToken(text)
                                    },
                                ) {
                                    Text(stringResource(Res.string.confirm))
                                }
                            },
                            dismissButton = {
                                val uri = LocalUriHandler.current
                                TextButton(
                                    onClick = {
                                        uri.openUri("https://pmf.kagg886.top/docs/main/login.html#3-%E6%88%91%E8%AF%A5%E5%A6%82%E4%BD%95%E5%AF%BC%E5%87%BA%E7%99%BB%E5%BD%95token")
                                    },
                                ) {
                                    Text(stringResource(Res.string.help))
                                }
                            },
                            title = {
                                Text(stringResource(Res.string.token_login))
                            },
                            text = {
                                TextField(
                                    value = text,
                                    onValueChange = { text = it },
                                    label = {
                                        Text(stringResource(Res.string.input_token))
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            },
                        )
                    }

                    is LoginViewState.LoginType.BrowserLogin -> {
                        when (state) {
                            LoginViewState.LoginType.BrowserLogin.ShowBrowser -> {
                                WebViewLogin(model)
                            }

                            is LoginViewState.LoginType.BrowserLogin.Loading -> {
                                val msg by state.msg.collectAsState()
                                Loading(text = msg)
                            }
                        }
                    }
                }
            }

            is LoginViewState.ProcessingUserData -> {
                Loading(text = state.msg)
            }
        }
    }
}

@Composable
private fun WebViewLogin(model: LoginScreenViewModel) {
    val auth = remember { PixivAccountFactory.newAccount(PlatformEngine) }
    val state = rememberWebViewState(auth.url) {
        customUserAgentString = "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/135.0.0.0 Mobile Safari/537.36 EdgA/135.0.0.0"
    }
    val webNav = rememberWebViewNavigator(
        requestInterceptor = object : RequestInterceptor {
            override fun onInterceptUrlRequest(
                request: WebRequest,
                navigator: WebViewNavigator,
            ): WebRequestInterceptResult {
                if (request.url.startsWith("pixiv://")) {
                    model.challengePixivLoginUrl(auth, request.url)
                    return WebRequestInterceptResult.Reject
                }
                return WebRequestInterceptResult.Allow
            }
        },
    )

    val progress = remember(state.loadingState) {
        when (state.loadingState) {
            is LoadingState.Loading -> (state.loadingState as LoadingState.Loading).progress
            else -> -1.0f
        }
    }

    Column {
        if (progress in 0.0f..<1.0f) {
            LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
        }
        WebView(
            modifier = Modifier.fillMaxSize(),
            state = state,
            navigator = webNav,
        )
    }
}
