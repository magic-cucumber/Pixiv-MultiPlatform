package top.kagg886.pmf.ui.screen.login

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.json.Json
import org.jetbrains.compose.resources.getString
import org.orbitmvi.orbit.OrbitContainer
import org.orbitmvi.orbit.OrbitContainerHost
import org.orbitmvi.orbit.viewmodel.orbitContainer
import top.kagg886.pixko.PixivAccountFactory
import top.kagg886.pixko.PixivVerification
import top.kagg886.pixko.TokenStorage
import top.kagg886.pixko.TokenType
import top.kagg886.pixko.TokenType.*
import top.kagg886.pixko.module.user.getCurrentUserSimpleProfile
import top.kagg886.pmf.i18n.Lang
import top.kagg886.pmf.i18n.login_profiling
import top.kagg886.pmf.i18n.login_verifying
import top.kagg886.pmf.i18n.login_welcome
import top.kagg886.pmf.logger.Logger
import top.kagg886.pmf.util.createPlatformEngine
import top.kagg886.pmf.util.Store
import top.kagg886.pmf.util.get
import top.kagg886.pmf.util.preferencePath
import top.kagg886.pmf.util.set
import kotlin.time.Duration.Companion.seconds

/**
 * ================================================
 * Author:     iveou
 * Created on: 2026/7/29 15:40
 * ================================================
 */

@Logger
class LoginViewModel : ViewModel(),
    OrbitContainerHost<LoginViewModelState, LoginViewModelState, LoginViewModelEffect> {

    private val login = Store.of(preferencePath / "login-properties.preferences_pb")

    override val container: OrbitContainer<LoginViewModelState, LoginViewModelState, LoginViewModelEffect> =
        orbitContainer(LoginViewModelState.BrowserLogin(PixivAccountFactory.newAccount(createPlatformEngine())))

    fun challenge(url: String) = intent {
        logger.i { "Received browser login verification request; authorization URL length=${url.length}" }
        val verification = (state as? LoginViewModelState.BrowserLogin)?.verification ?: run {
            logger.w { "Current page state is not BrowserLogin; ignoring the login verification request" }
            return@intent
        }

        val progress = MutableStateFlow(true)
        val emitter = MutableStateFlow(getString(Lang.string.login_verifying))
        logger.i { "Setting state to Verifying and starting browser login verification" }
        reduce { LoginViewModelState.Verifying(progress, emitter) }

        val tokens = object : TokenStorage {
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
        val client = try {
            logger.d { "Calling the Pixiv browser login verification API" }
            verification.verify(url) { storage = tokens }
        } catch (e: Exception) {
            logger.e(e) { "Browser login verification failed; setting state to VerificationFailed" }
            reduce { LoginViewModelState.VerificationFailed }
            return@intent
        }

        logger.i { "Browser login verification succeeded; fetching the current user profile" }
        emitter.emit(getString(Lang.string.login_profiling))

        val profile = try {
            logger.d { "Calling the current-user profile API" }
            client.getCurrentUserSimpleProfile()
        } catch (e: Exception) {
            logger.e(e) { "Fetching the current user profile failed; setting state to VerificationFailed" }
            reduce { LoginViewModelState.VerificationFailed }
            return@intent
        }

        logger.i { "Current user profile fetched successfully; saving the login profile" }
        login.set("profile", Json.encodeToString(profile))

        progress.emit(false)
        emitter.emit(getString(Lang.string.login_welcome, profile.name))
        logger.i { "Login profile saved successfully; waiting for the welcome message to finish" }
        delay(3.seconds)
        client.close()
        logger.i { "Login flow completed; posting NavigateToMain effect" }
        postSideEffect(LoginViewModelEffect.NavigateToMain)
    }

    fun retryBrowserLogin() = intent {
        logger.i { "Retrying browser login; setting state to BrowserLogin" }
        reduce { LoginViewModelState.BrowserLogin(PixivAccountFactory.newAccount(createPlatformEngine())) }
    }
}

sealed interface LoginViewModelState {
    data class BrowserLogin(val verification: PixivVerification<*>) : LoginViewModelState
    data class Verifying(val progress: Flow<Boolean>, val message: Flow<String>) : LoginViewModelState
    data object VerificationFailed : LoginViewModelState
}

sealed interface LoginViewModelEffect {
    data object NavigateToMain : LoginViewModelEffect
}
