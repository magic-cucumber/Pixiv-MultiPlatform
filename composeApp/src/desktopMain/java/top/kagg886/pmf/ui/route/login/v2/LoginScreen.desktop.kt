package top.kagg886.pmf.ui.route.login.v2

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.io.PrintStream
import top.kagg886.pmf.util.logger

@Composable
internal actual fun WebviewPlatformInstall() {
    DisposableEffect(Unit) {
        val origin = System.err
        System.setErr(PrintStream2KermitLogger(ByteArrayOutputStream()))

        onDispose {
            System.setProperty("composewebview.wry.log", null)
            System.setErr(origin)
        }
    }
}

private class PrintStream2KermitLogger(out: OutputStream) : PrintStream(out) {
    private val log = logger.withTag("ComposeNativeWebview")
    override fun println(x: String?) {
        x?.let { log.d(it) }
    }
}
