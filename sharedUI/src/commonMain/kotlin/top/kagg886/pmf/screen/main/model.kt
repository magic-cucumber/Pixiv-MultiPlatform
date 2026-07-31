package top.kagg886.pmf.screen.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.ktor.client.engine.*
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
import top.kagg886.pmf.util.*

/**
 * ================================================
 * Author:     iveou
 * Created on: 2026/7/30 11:55
 * ================================================
 */
class MainViewModel : ViewModel(),
    OrbitContainerHost<MainViewModelState, MainViewModelState, MainViewModelSideEffect> {
    private val login = Store.of(preferencePath / "login-properties.preferences_pb")
    override val container: OrbitContainer<MainViewModelState, MainViewModelState, MainViewModelSideEffect> =
        orbitContainer(MainViewModelState.Loading) {
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
            }

            intent {
                login.set("profile", Json.encodeToString(factory.getCurrentUserSimpleProfile()))
            }

            val database = AppAccountDatabase.create(databasePath / "account-${info.value.userId}.db")

            reduce {
                MainViewModelState.LoadSuccess(
                    client = factory,
                    profile = info,
                    database = database,
                )
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

    data class LoadError(val error: String) : MainViewModelState
}

sealed interface MainViewModelSideEffect {
    data object NavigateToLogin : MainViewModelSideEffect
}


expect fun createPlatformEngine(): HttpClientEngineFactory<*>
