package top.kagg886.pmf.fronted.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import org.jetbrains.compose.resources.getString
import org.orbitmvi.orbit.OrbitContainer
import org.orbitmvi.orbit.OrbitContainerHost
import org.orbitmvi.orbit.viewmodel.orbitContainer
import top.kagg886.pixko.PixivVerification
import top.kagg886.pixko.TokenStorage
import top.kagg886.pixko.TokenType
import top.kagg886.pixko.TokenType.*
import top.kagg886.pixko.module.user.getCurrentUserSimpleProfile
import top.kagg886.pmf.i18n.Lang
import top.kagg886.pmf.i18n.login_profiling
import top.kagg886.pmf.i18n.login_verifying
import top.kagg886.pmf.i18n.login_welcome
import top.kagg886.pmf.util.Store
import top.kagg886.pmf.util.dataPath
import top.kagg886.pmf.util.flow
import top.kagg886.pmf.util.set
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * ================================================
 * Author:     iveou
 * Created on: 2026/7/29 15:40
 * ================================================
 */

class LoginViewModel : ViewModel(),
    OrbitContainerHost<LoginViewModelState, LoginViewModelState, LoginViewModelEffect> {

    private val login = Store.of(dataPath / "login-properties.preferences_pb")

    override val container: OrbitContainer<LoginViewModelState, LoginViewModelState, LoginViewModelEffect> =
        orbitContainer(LoginViewModelState.BrowserLogin(createPixivVerification()))

    fun challenge(url: String) = intent {
        val verification = (state as? LoginViewModelState.BrowserLogin)?.verification ?: return@intent

        val progress = MutableStateFlow(true)
        val emitter = MutableStateFlow(getString(Lang.string.login_verifying))
        reduce { LoginViewModelState.Verifying(progress, emitter) }

        val tokens = object : TokenStorage {
            override suspend fun getToken(type: TokenType): String? = when (type) {
                ACCESS -> login.flow(viewModelScope, "access_token") { "" }.value
                REFRESH -> login.flow(viewModelScope, "refresh_token") { "" }.value
                EXPIRE_TIME -> login.flow(viewModelScope, "expire_time") { "" }.value
            }

            override suspend fun setToken(type: TokenType, token: String) = when (type) {
                ACCESS -> login.set("access_token", token)
                REFRESH -> login.set("refresh_token", token)
                EXPIRE_TIME -> login.set("expire_time", token)
            }
        }
        val client = try {
            verification.verify(url) { storage = tokens }
        } catch (e: Exception) {
            reduce { LoginViewModelState.VerificationFailed }
            return@intent
        }

        emitter.emit(getString(Lang.string.login_profiling))

        val profile = try {
            client.getCurrentUserSimpleProfile()
        } catch (e: Exception) {
            reduce { LoginViewModelState.VerificationFailed }
            return@intent
        }

        progress.emit(false)
        emitter.emit(getString(Lang.string.login_welcome, profile.name))
        delay(3.seconds)

        client.close()
        postSideEffect(LoginViewModelEffect.NavigateToMain)
    }

    fun retryBrowserLogin() = intent {
        reduce { LoginViewModelState.BrowserLogin(createPixivVerification()) }
    }
}

expect fun createPixivVerification(): PixivVerification<*>

sealed interface LoginViewModelState {
    data class BrowserLogin(val verification: PixivVerification<*>) : LoginViewModelState
    data class Verifying(val progress: Flow<Boolean>, val message: Flow<String>) : LoginViewModelState
    data object VerificationFailed : LoginViewModelState
}

sealed interface LoginViewModelEffect {
    data object NavigateToMain : LoginViewModelEffect
}
