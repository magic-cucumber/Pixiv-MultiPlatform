package top.kagg886.pmf.ui.route.login.v2

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable
import org.koin.compose.viewmodel.koinViewModel
import org.orbitmvi.orbit.compose.collectAsState
import org.orbitmvi.orbit.compose.collectSideEffect
import top.kagg886.pixko.PixivAccountFactory
import top.kagg886.pmf.LocalNavBackStack
import top.kagg886.pmf.LocalSnackBarHost
import top.kagg886.pmf.backend.PlatformEngine
import top.kagg886.pmf.backend.pixiv.PixivConfig
import top.kagg886.pmf.openBrowser
import top.kagg886.pmf.res.*
import top.kagg886.pmf.ui.component.Loading
import top.kagg886.pmf.ui.component.guide.GuideScaffold
import top.kagg886.pmf.ui.component.icon.Help
import top.kagg886.pmf.ui.route.main.recommend.RecommendRoute
import top.kagg886.pmf.util.logger
import top.kagg886.pmf.util.stringResource
import top.kagg886.wvbridge.LoadingState
import top.kagg886.wvbridge.WebView
import top.kagg886.wvbridge.config.WebViewConfig
import top.kagg886.wvbridge.config.WebViewPlatformConfig
import top.kagg886.wvbridge.interceptor.InterceptorHandler
import top.kagg886.wvbridge.rememberWebViewController

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
                            onDismissRequest = {
                                model.clearLoginType()
                            },
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

expect fun defaultPlatformConfig(): WebViewPlatformConfig

@Composable
private fun WebViewLogin(model: LoginScreenViewModel) {
    val auth = remember { PixivAccountFactory.newAccount(PlatformEngine) }
    val controller = rememberWebViewController(
        url = auth.url,
        config = WebViewConfig(
            userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/147.0.7727.56 Safari/537.36",
            platform = defaultPlatformConfig(),
        ),
    )
    DisposableEffect(controller) {
        val registry = controller.interceptor.registerNavigationInterceptor auth@{ url ->
            logger.i("webview intercept url: $url")
            if (url.startsWith("pixiv://")) {
                model.challengePixivLoginUrl(auth, url)
                return@auth InterceptorHandler.Result.Rejected
            }
            return@auth InterceptorHandler.Result.Allowed
        }
        onDispose {
            registry.close()
        }
    }

    val progress by remember {
        derivedStateOf {
            (controller.loadingState as? LoadingState.Loading)?.progress ?: -1f
        }
    }

    Column {
        TopAppBar(
            title = {
                Text(stringResource(Res.string.use_browser_login))
            },
            navigationIcon = {
                IconButton(onClick = { model.clearLoginType() }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, null)
                }
            },
            actions = {
                IconButton(
                    onClick = {
                        openBrowser("https://pmf.kagg886.top/docs/main/login.html#%E4%BD%BF%E7%94%A8%E5%B5%8C%E5%85%A5%E5%BC%8F%E6%B5%8F%E8%A7%88%E5%99%A8%E7%99%BB%E5%BD%95")
                    },
                ) {
                    Icon(Help, null)
                }
            },
        )
        if (progress in 0.0f..<1.0f) {
            LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
        }
        WebView(
            controller = controller,
            modifier = Modifier.fillMaxSize(),
        )
    }
}
