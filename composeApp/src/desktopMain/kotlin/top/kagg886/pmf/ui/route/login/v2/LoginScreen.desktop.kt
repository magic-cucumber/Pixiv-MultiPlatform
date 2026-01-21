package top.kagg886.pmf.ui.route.login.v2

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import co.touchlab.kermit.Logger
import io.github.kdroidfilter.webview.web.*
import io.github.kdroidfilter.webview.wry.WryWebViewPanel
import io.github.kdroidfilter.webview.wry.setLogEnabled
import top.kagg886.pmf.util.logger

@Composable
internal actual fun PlatformWebView(
    state: WebViewState,
    modifier: Modifier,
    navigator: WebViewNavigator,
) = WebView(
    state,
    modifier,
    navigator,
    factory = ::defaultWebViewFactory,
)

fun defaultWebViewFactory(param: WebViewFactoryParam): NativeWebView = when (val content = param.state.content) {
    is WebContent.Url -> NativeWebView(
        initialUrl = content.url,
        customUserAgent = param.userAgent ?: param.state.webSettings.customUserAgentString,
        bridgeLogger = { Logger.withTag("ComposeNativeLogger - Panel").d(it) },
    )

    else -> NativeWebView("about:blank", param.userAgent ?: param.state.webSettings.customUserAgentString)
}

@Composable
internal actual fun WebviewPlatformInstall() {
    DisposableEffect(Unit) {
        val local = WryWebViewPanel.NATIVE_LOGGER
        val log = logger.withTag("ComposeNativeLogger - Native")

        setLogEnabled(true)
        WryWebViewPanel.LOG_ENABLED = true
        WryWebViewPanel.NATIVE_LOGGER = {
            log.d(it)
        }

        onDispose {
            setLogEnabled(false)
            WryWebViewPanel.LOG_ENABLED = false
            WryWebViewPanel.NATIVE_LOGGER = local
        }
    }
}
