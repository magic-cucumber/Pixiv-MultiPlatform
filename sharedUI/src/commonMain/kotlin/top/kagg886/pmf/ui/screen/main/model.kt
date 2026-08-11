package top.kagg886.pmf.ui.screen.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logging
import io.ktor.http.HttpHeaders
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.serialization.json.Json
import org.orbitmvi.orbit.OrbitContainer
import org.orbitmvi.orbit.OrbitContainerHost
import org.orbitmvi.orbit.viewmodel.orbitContainer
import top.kagg886.pixko.PixivAccount
import top.kagg886.pixko.PixivAccountFactory
import top.kagg886.pixko.TokenStorage
import top.kagg886.pixko.TokenType
import top.kagg886.pixko.TokenType.*
import top.kagg886.pixko.module.user.SimpleMeProfile
import top.kagg886.pixko.module.user.getCurrentUserSimpleProfile
import top.kagg886.pmf.database.account.AppAccountDatabase
import top.kagg886.pmf.database.account.create
import top.kagg886.pmf.logger.Logger
import top.kagg886.pmf.util.*

/**
 * ================================================
 * Author:     iveou
 * Created on: 2026/7/30 11:55
 * ================================================
 */
@Logger
class MainViewModel : ViewModel(),
    OrbitContainerHost<MainViewModelState, MainViewModelState, MainViewModelSideEffect> {
    private val login = Store.of(preferencePath / "login-properties.preferences_pb")
    override val container: OrbitContainer<MainViewModelState, MainViewModelState, MainViewModelSideEffect> =
        orbitContainer(MainViewModelState.Loading) {
            logger.i { "Starting main page model initialization" }
            val info = login.flow(viewModelScope, "profile") { "" }.map { Json.decodeFromString<SimpleMeProfile>(it) }
                .stateIn(viewModelScope)

            val factory = PixivAccountFactory.newAccountFromConfig(createPlatformEngine()) {
                storage = object : TokenStorage {
                    override suspend fun getToken(type: TokenType): String? = when (type) {
                        ACCESS -> login.get("access_token") { "" }
                        REFRESH -> login.get("refresh_token") { "" }
                        EXPIRE_TIME -> login.get("expire_time") { "" }
                    }

                    override suspend fun setToken(type: TokenType, token: String) = when (type) {
                        ACCESS -> login.set("access_token", token)
                        REFRESH -> login.set("refresh_token", token)
                        EXPIRE_TIME -> login.set("expire_time", token)
                    }
                }

                config = {
                    install(Logging) {
                        logger = object : io.ktor.client.plugins.logging.Logger {
                            override fun log(message: String) {
                                co.touchlab.kermit.Logger.withTag("Ktor").d(message)
                            }

                        }
                        level = LogLevel.BODY
                        sanitizeHeader { it == HttpHeaders.Authorization }
                    }
                }
            }

            intent {
                logger.d { "Reading the current user profile and saving it to local preferences" }
                val profile = factory.getCurrentUserSimpleProfile()
                login.set("profile", Json.encodeToString(profile))
                logger.i { "Current user profile saved successfully" }
            }

            try {
                logger.d { "Preparing to create the account database" }
                val database = AppAccountDatabase.create(databasePath / "account-${info.value.userId}.db")

                logger.i { "Account database initialized successfully; setting state to LoadSuccess" }
                reduce {
                    MainViewModelState.LoadSuccess(
                        client = factory,
                        profile = info,
                        database = database,
                    )
                }
            } catch (e: Exception) {
                logger.e(e) { "Account database initialization failed; setting state to LoadError" }
                reduce {
                    MainViewModelState.LoadError
                }
            }
        }
}


sealed interface MainViewModelState {
    data object Loading : MainViewModelState
    data class LoadSuccess(
        val client: PixivAccount,
        val profile: StateFlow<SimpleMeProfile>,
        val database: AppAccountDatabase
    ) :
        MainViewModelState

    data object LoadError : MainViewModelState
}

sealed interface MainViewModelSideEffect {
    data object NavigateToLogin : MainViewModelSideEffect
}
