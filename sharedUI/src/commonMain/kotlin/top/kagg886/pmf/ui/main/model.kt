package top.kagg886.pmf.ui.main

import androidx.lifecycle.ViewModel
import io.ktor.client.engine.*
import org.orbitmvi.orbit.OrbitContainer
import org.orbitmvi.orbit.OrbitContainerHost
import org.orbitmvi.orbit.viewmodel.orbitContainer
import top.kagg886.pixko.PixivAccount
import top.kagg886.pixko.PixivAccountFactory
import top.kagg886.pixko.TokenStorage
import top.kagg886.pixko.TokenType
import top.kagg886.pixko.TokenType.*
import top.kagg886.pmf.util.Store
import top.kagg886.pmf.util.get
import top.kagg886.pmf.util.preferencePath
import top.kagg886.pmf.util.set

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
        orbitContainer(
            initialState = MainViewModelState(
                client = PixivAccountFactory.newAccountFromConfig(createPlatformEngine()) {
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
            )
        )
}


data class MainViewModelState(
    val client: PixivAccount
)

sealed interface MainViewModelSideEffect {
    data object NavigateToLogin : MainViewModelSideEffect
}


expect fun createPlatformEngine(): HttpClientEngineFactory<*>
