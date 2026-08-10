package top.kagg886.pmf.ui.screen.login

import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.serialization.Serializable
import org.jetbrains.compose.resources.stringResource
import org.orbitmvi.orbit.compose.collectAsState
import org.orbitmvi.orbit.compose.collectSideEffect
import top.kagg886.pmf.LocalNavController
import top.kagg886.pmf.ui.screen.main.MainRoute
import top.kagg886.pmf.i18n.Lang
import top.kagg886.pmf.i18n.login_browser_title
import top.kagg886.pmf.i18n.login_retry
import top.kagg886.pmf.i18n.login_verification_failed
import top.kagg886.pmf.util.nav3.SerializableNavKey
import top.kagg886.wvbridge.LoadingState
import top.kagg886.wvbridge.WebView
import top.kagg886.wvbridge.config.WebViewConfig
import top.kagg886.wvbridge.config.WebViewPlatformConfig
import top.kagg886.wvbridge.interceptor.InterceptorHandler
import top.kagg886.wvbridge.rememberWebViewController

/**
 * ================================================
 * Author:     iveou
 * Created on: 2026/7/27
 * ================================================
 */

@Serializable
data object LoginRoute : SerializableNavKey

@Composable
fun LoginScreen() {
    val model = viewModel<LoginViewModel> { LoginViewModel() }
    val nav = LocalNavController.current
    val state by model.collectAsState()

    model.collectSideEffect {
        when (it) {
            LoginViewModelEffect.NavigateToMain -> {
                nav.update {
                    pop()
                    push(MainRoute)
                }
            }
        }
    }


    AnimatedContent(targetState = state, label = "login state") { targetState ->
        when (targetState) {
            is LoginViewModelState.BrowserLogin -> BrowserLoginContent(
                state = targetState,
                onPixivLoginUrl = model::challenge,
            )

            LoginViewModelState.VerificationFailed -> VerificationFailedContent(
                onRetry = model::retryBrowserLogin,
            )

            is LoginViewModelState.Verifying -> VerifyingContent(targetState)
        }
    }
}

@Composable
private fun BrowserLoginContent(
    state: LoginViewModelState.BrowserLogin,
    onPixivLoginUrl: (String) -> Unit,
) {
    val controller = rememberWebViewController(
        url = state.verification.url,
        config = WebViewConfig(
            userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/147.0.7727.56 Safari/537.36",
            platform = createWebViewPlatformConfig()
        )
    )
    DisposableEffect(controller) {
        val registry = controller.interceptor.registerNavigationInterceptor { url ->
            if (url.startsWith("pixiv://account/login?code=")) {
                onPixivLoginUrl(url)
                InterceptorHandler.Result.Rejected
            } else {
                InterceptorHandler.Result.Allowed
            }
        }
        onDispose(registry::close)
    }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(stringResource(Lang.string.login_browser_title)) },
        )
        val progress = (controller.loadingState as? LoadingState.Loading)?.progress
        if (progress != null) {
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        WebView(controller = controller, modifier = Modifier.fillMaxSize())
    }
}

@Composable
private fun VerifyingContent(state: LoginViewModelState.Verifying) {
    val progress by state.progress.collectAsStateWithLifecycle(true)
    val message by state.message.collectAsStateWithLifecycle("")
    val messageStyle = MaterialTheme.typography.bodyLarge
    val messageHeight = with(LocalDensity.current) { messageStyle.lineHeight.toDp() }
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            AnimatedContent(
                targetState = progress,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "verifying indicator",
            ) { loading ->
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) {
                    if (loading) {
                        CircularProgressIndicator(modifier = Modifier.size(40.0.dp))
                    } else {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            modifier = Modifier.size(40.0.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
            AnimatedContent(
                targetState = message,
                transitionSpec = {
                    (slideInVertically { it } + fadeIn()) togetherWith (slideOutVertically { -it } + fadeOut())
                },
                label = "verifying message",
            ) { text ->
                Box(
                    modifier = Modifier.fillMaxWidth().requiredHeight(messageHeight),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(text, style = messageStyle)
                }
            }
        }
    }
}

@Composable
private fun VerificationFailedContent(onRetry: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(stringResource(Lang.string.login_verification_failed))
            Button(onClick = onRetry) {
                Text(stringResource(Lang.string.login_retry))
            }
        }
    }
}

expect fun createWebViewPlatformConfig(): WebViewPlatformConfig
