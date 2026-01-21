package top.kagg886.pmf.ui.route.login.v2

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.github.kdroidfilter.webview.web.WebView
import io.github.kdroidfilter.webview.web.WebViewNavigator
import io.github.kdroidfilter.webview.web.WebViewState

@Composable
internal actual fun WebviewPlatformInstall() {
}

@Composable
internal actual fun PlatformWebView(
    state: WebViewState,
    modifier: Modifier,
    navigator: WebViewNavigator,
) = WebView(state, modifier, navigator)
