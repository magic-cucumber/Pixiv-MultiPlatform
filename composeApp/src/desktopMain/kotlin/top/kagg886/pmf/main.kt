package top.kagg886.pmf

import androidx.compose.runtime.*
import androidx.compose.ui.window.*
import androidx.navigation3.runtime.NavKey
import co.touchlab.kermit.Logger
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import java.io.IOException
import java.net.ServerSocket
import javax.swing.JOptionPane
import kotlin.system.exitProcess
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.jetbrains.compose.resources.painterResource
import top.kagg886.pmf.res.Res
import top.kagg886.pmf.res.kotlin
import top.kagg886.pmf.res.multiapp_not_allowed
import top.kagg886.pmf.res.warning
import top.kagg886.pmf.ui.route.crash.CrashApp
import top.kagg886.pmf.ui.route.welcome.WelcomeRoute
import top.kagg886.pmf.util.getString


fun launch(start: () -> NavKey) {
    setupEnv()
    SingletonImageLoader.setSafe {
        ImageLoader.Builder(PlatformContext.INSTANCE).applyCustomConfig().build()
    }

    var lastException by mutableStateOf<Throwable?>(null)

    application(exitProcessOnExit = false) {
        LaunchedEffect(Unit) {
            Thread.setDefaultUncaughtExceptionHandler { _, ex ->
                lastException = ex
                exitApplication()
            }
        }
        CompositionLocalProvider(
            LocalWindowExceptionHandlerFactory provides WindowExceptionHandlerFactory { window ->
                WindowExceptionHandler { ex ->
                    lastException = ex
                    exitApplication()
                }
            },
            LocalKeyStateFlow provides remember { MutableSharedFlow() },
        ) {
            CompositionLocalProvider {
                val flow = LocalKeyStateFlow.current as MutableSharedFlow
                val scope = rememberCoroutineScope()
                Window(
                    onCloseRequest = ::exitApplication,
                    title = BuildConfig.APP_NAME,
                    icon = painterResource(Res.drawable.kotlin),
                    onKeyEvent = {
                        scope.launch {
                            flow.emit(it)
                        }
                        true
                    },
                ) {
                    App(start())
                }
            }
        }
    }
    Logger.e("App exit with exception", lastException)
    if (lastException != null) {
        singleWindowApplication {
            CrashApp(throwable = lastException!!.stackTraceToString())
        }
        exitProcess(1)
    }
    exitProcess(0)
}

private const val SOCKET_LOCK = 32043

@OptIn(DelicateCoroutinesApi::class)
fun main() {
    try {
        val lockSocket = ServerSocket(SOCKET_LOCK)
        lockSocket.setReuseAddress(false)
        Runtime.getRuntime().addShutdownHook(Thread {
            lockSocket.close()
        })
    } catch (e: IOException) {
        println(e)
        runBlocking(Dispatchers.Main) {
            JOptionPane.showMessageDialog(
                null,
                getString(Res.string.multiapp_not_allowed,"$SOCKET_LOCK"),
                getString(Res.string.warning),
                JOptionPane.WARNING_MESSAGE
            );
            exitProcess(0)
        }
    }
    launch { WelcomeRoute }
}
