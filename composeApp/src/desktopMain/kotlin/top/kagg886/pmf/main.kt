package top.kagg886.pmf

import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.window.*
import androidx.navigation3.runtime.NavKey
import co.touchlab.kermit.Logger
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.io.PrintStream
import java.nio.channels.FileChannel
import java.nio.channels.FileLock
import java.nio.file.StandardOpenOption
import javax.swing.JOptionPane
import korlibs.time.seconds
import kotlin.math.roundToInt
import kotlin.system.exitProcess
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.sample
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.jetbrains.compose.resources.painterResource
import top.kagg886.pmf.backend.DesktopWindowConfig
import top.kagg886.pmf.backend.dataPath
import top.kagg886.pmf.res.Res
import top.kagg886.pmf.res.kotlin
import top.kagg886.pmf.res.multiapp_hint
import top.kagg886.pmf.res.multiapp_not_allowed
import top.kagg886.pmf.res.warning
import top.kagg886.pmf.ui.route.crash.CrashApp
import top.kagg886.pmf.ui.route.welcome.WelcomeRoute
import top.kagg886.pmf.util.absolutePath
import top.kagg886.pmf.util.createNewFile
import top.kagg886.pmf.util.exists
import top.kagg886.pmf.util.getString
import top.kagg886.pmf.util.logger
import top.kagg886.pmf.util.mkdirs
import top.kagg886.pmf.util.parentFile
import top.kagg886.pmf.util.writeString

fun launch(start: () -> NavKey) {
    setupEnv()
    stdErrToLogger()
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
                    logger.e("uncaught error occured", ex)
                    lastException = ex
                    exitApplication()
                }
            },
            LocalKeyStateFlow provides remember { MutableSharedFlow() },
        ) {
            CompositionLocalProvider {
                val density = LocalDensity.current
                val flow = LocalKeyStateFlow.current as MutableSharedFlow
                val scope = rememberCoroutineScope()
                val state = rememberWindowState(
                    position = with(DesktopWindowConfig) {
                        val x = x
                        val y = y
                        when {
                            x != null && y != null -> WindowPosition.Absolute(
                                x = with(density) { x.toDp() },
                                y = with(density) { y.toDp() },
                            )

                            else -> WindowPosition.PlatformDefault
                        }
                    },
                    size = with(density) {
                        DpSize(DesktopWindowConfig.w.toDp(), DesktopWindowConfig.h.toDp())
                    },
                )

                LaunchedEffect(state) {
                    @OptIn(FlowPreview::class)
                    snapshotFlow { state.position }
                        .distinctUntilChanged()
                        .sample(1.seconds)
                        .collect { position ->
                            if (position is WindowPosition.PlatformDefault) {
                                DesktopWindowConfig.x = null
                                DesktopWindowConfig.y = null
                                return@collect
                            }
                            if (position is WindowPosition.Absolute) {
                                DesktopWindowConfig.x =
                                    with(density) { position.x.toPx().roundToInt() }
                                DesktopWindowConfig.y =
                                    with(density) { position.y.toPx().roundToInt() }
                            }
                        }
                }

                LaunchedEffect(state) {
                    @OptIn(FlowPreview::class)
                    snapshotFlow { state.size }
                        .distinctUntilChanged()
                        .sample(1.seconds)
                        .collect { position ->
                            DesktopWindowConfig.w =
                                with(density) { position.width.toPx().roundToInt() }
                            DesktopWindowConfig.h =
                                with(density) { position.height.toPx().roundToInt() }
                        }
                }

                Window(
                    state = state,
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

private fun stdErrToLogger() {
    val delegate = System.err
    val logger = Logger.withTag("stderr")
    val buffer = ByteArrayOutputStream()

    val proxy = object : OutputStream() {
        override fun write(b: Int) {
            delegate.write(b)
            buffer.write(b)
        }

        override fun flush() {
            delegate.flush()
            // exclude \n
            val string = with(buffer.toString(Charsets.UTF_8)) {
                if (endsWith("\r\n")) return@with dropLast(2)
                if (endsWith("\n")) return@with dropLast(1)
                return@with this
            }
            buffer.reset()

            if (string.isBlank()) {
                return
            }
            logger.w(string)
        }
    }
    System.setErr(PrintStream(proxy, true))
}

@OptIn(DelicateCoroutinesApi::class)
fun main() {
    val file = dataPath.resolve("pmf.lock")
    if (!file.exists()) {
        file.absolutePath().parentFile()?.mkdirs()
        file.createNewFile()
        file.writeString(runBlocking { getString(Res.string.multiapp_hint) })
    }

    val lock: FileLock? = runCatching {
        FileChannel.open(file.toNioPath(), StandardOpenOption.CREATE, StandardOpenOption.WRITE)
            .tryLock()
    }.getOrNull()

    if (lock == null) {
        runBlocking(Dispatchers.Main) {
            JOptionPane.showMessageDialog(
                null,
                getString(Res.string.multiapp_not_allowed),
                getString(Res.string.warning),
                JOptionPane.WARNING_MESSAGE,
            )
            exitProcess(0)
        }
    }

    Runtime.getRuntime().addShutdownHook(
        Thread {
            lock.release()
        },
    )

    launch { WelcomeRoute }
}
