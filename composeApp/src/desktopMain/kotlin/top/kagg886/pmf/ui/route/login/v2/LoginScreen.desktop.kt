package top.kagg886.pmf.ui.route.login.v2

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import co.touchlab.kermit.Logger
import io.github.kdroidfilter.webview.web.*
import io.github.kdroidfilter.webview.wry.Rgba
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
        bridgeLogger = { Logger.withTag("ComposeNativeLogger - Panel").d(it) },
        backgroundColor = Color.Transparent.toRgba(),
    )

    else -> NativeWebView("about:blank", backgroundColor = Color.Transparent.toRgba())
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

private fun Color.toRgba(): Rgba {
    val argb: Int = this.toArgb() // 0xAARRGGBB (sRGB)
    val a: UByte = ((argb ushr 24) and 0xFF).toUByte()
    val r: UByte = ((argb ushr 16) and 0xFF).toUByte()
    val g: UByte = ((argb ushr 8) and 0xFF).toUByte()
    val b: UByte = (argb and 0xFF).toUByte()
    return Rgba(r = r, g = g, b = b, a = a)
}
