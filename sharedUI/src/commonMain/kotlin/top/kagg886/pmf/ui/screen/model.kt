package top.kagg886.pmf.ui.screen

import androidx.lifecycle.ViewModel
import co.touchlab.kermit.Severity.*
import co.touchlab.kermit.Logger as KermitLogger
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.memory.MemoryCache
import coil3.network.ktor3.KtorNetworkFetcherFactory
import coil3.serviceLoaderEnabled
import io.ktor.client.HttpClient
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import org.orbitmvi.orbit.OrbitContainer
import org.orbitmvi.orbit.OrbitContainerHost
import org.orbitmvi.orbit.viewmodel.orbitContainer
import top.kagg886.pmf.database.common.AppCommonDatabase
import top.kagg886.pmf.database.common.create
import top.kagg886.pmf.database.util.DatabaseLogWriter
import top.kagg886.pmf.logger.Logger
import top.kagg886.pmf.util.cachePath
import top.kagg886.pmf.util.createPlatformEngine
import top.kagg886.pmf.util.databasePath
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days

/**
 * ================================================
 * Author:     iveou
 * Created on: 2026/7/27 09:32
 * ================================================
 */

@Logger
class RootViewModel(
    platformContext: PlatformContext,
) : ViewModel(), OrbitContainerHost<RootViewModelState, RootViewModelState, RootViewModelEffect> {
    override val container: OrbitContainer<RootViewModelState, RootViewModelState, RootViewModelEffect> =
        orbitContainer(RootViewModelState.Loading) {
            logger.i { "Starting root page model initialization" }
            try {
                logger.d { "Preparing to create the common database and install the persistent log writer" }
                val database = AppCommonDatabase.create(databasePath / "common.db")
                val logDao = database.logDao()
                logDao.clearBeforeTime(Clock.System.now().minus(1.days))
                KermitLogger.addLogWriter(DatabaseLogWriter(logDao))
                logger.d { "Common database initialized; preparing the shared image loader" }
                initializeImageLoader(platformContext)
                logger.i { "Persistent log writer and image loader initialized successfully; setting state to LoadSuccess" }
                reduce {
                    RootViewModelState.LoadSuccess
                }
            } catch (e: Exception) {
                logger.e(e) { "Root page model initialization failed; setting state to Error" }
                reduce {
                    RootViewModelState.Error
                }
            }
        }
}

private fun initializeImageLoader(context: PlatformContext) {
    SingletonImageLoader.setSafe {
        ImageLoader.Builder(context)
            .serviceLoaderEnabled(false)
            .memoryCache {
                MemoryCache.Builder()
                    .maxSizePercent(context)
                    .build()
            }
            .logger(
                object : coil3.util.Logger {
                    override var minLevel: coil3.util.Logger.Level
                        get() = when (KermitLogger.config.minSeverity) {
                            Verbose -> coil3.util.Logger.Level.Verbose
                            Debug -> coil3.util.Logger.Level.Debug
                            Info -> coil3.util.Logger.Level.Info
                            Warn -> coil3.util.Logger.Level.Warn
                            Error -> coil3.util.Logger.Level.Error
                            Assert -> coil3.util.Logger.Level.Error
                        }
                        set(_) {
                            throw UnsupportedOperationException("coil logger will be hosted by kermit.")
                        }

                    override fun log(
                        tag: String,
                        level: coil3.util.Logger.Level,
                        message: String?,
                        throwable: Throwable?
                    ) {
                        KermitLogger.log(
                            severity = when (level) {
                                coil3.util.Logger.Level.Verbose -> Verbose
                                coil3.util.Logger.Level.Debug -> Debug
                                coil3.util.Logger.Level.Info -> Info
                                coil3.util.Logger.Level.Warn -> Warn
                                coil3.util.Logger.Level.Error -> Error
                            },
                            tag = "Coil - $tag",
                            message = message ?: "",
                            throwable = throwable,
                        )
                    }
                }
            )
            .diskCache {
                DiskCache.Builder()
                    .directory(cachePath.resolve("image"))
                    .build()
            }
            .components {
                add(
                    KtorNetworkFetcherFactory(
                        httpClient = {
                            HttpClient(createPlatformEngine()) {
                                defaultRequest {
                                    header(HttpHeaders.Referrer, "https://www.pixiv.net/")
                                }
                            }
                        },
                    ),
                )
            }
            .build()
    }
}


sealed interface RootViewModelState {
    data object Loading : RootViewModelState
    data object LoadSuccess : RootViewModelState
    data object Error : RootViewModelState
}

sealed interface RootViewModelEffect {
}
