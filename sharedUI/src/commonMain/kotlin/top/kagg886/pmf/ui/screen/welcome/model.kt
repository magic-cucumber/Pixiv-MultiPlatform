package top.kagg886.pmf.ui.screen.welcome

import androidx.lifecycle.ViewModel
import org.orbitmvi.orbit.OrbitContainer
import org.orbitmvi.orbit.OrbitContainerHost
import org.orbitmvi.orbit.viewmodel.orbitContainer
import top.kagg886.pmf.logger.Logger
import top.kagg886.pmf.util.Store
import top.kagg886.pmf.util.get
import top.kagg886.pmf.util.preferencePath
import top.kagg886.pmf.util.set

/**
 * ================================================
 * Author:     iveou
 * Created on: 2026/7/27 13:41
 * ================================================
 */

@Logger
class WelcomeViewModel : ViewModel(),
    OrbitContainerHost<WelcomeViewModelState, WelcomeViewModelState, WelcomeViewModelEffect> {

    private val initialized = Store.of(preferencePath / "welcome.preferences_pb")
    private val login = Store.of(preferencePath / "login-properties.preferences_pb")

    override val container: OrbitContainer<WelcomeViewModelState, WelcomeViewModelState, WelcomeViewModelEffect> =
        orbitContainer(WelcomeViewModelState()) {
            logger.i { "Checking the welcome page initialization state" }
            if (initialized.get("initialized") { false }) {
                logger.i { "Welcome page initialization is complete; running the confirmation flow" }
                confirmInitialized()
                return@orbitContainer
            }
            logger.i { "First-run initialization is incomplete; setting loading=false" }
            reduce { state.copy(loading = false) }
        }


    fun confirmInitialized() = intent {
        logger.i { "Confirming welcome page initialization and recording completion" }
        initialized.set("initialized", true)
        val hasRefreshToken = login.get("refresh_token") { "" }.isNotBlank()
        if (!hasRefreshToken) {
            logger.i { "No refresh token found; posting NavigateToLogin effect" }
            postSideEffect(WelcomeViewModelEffect.NavigateToLogin)
            return@intent
        }
        logger.i { "Refresh token found; posting NavigateToMain effect" }
        postSideEffect(WelcomeViewModelEffect.NavigateToMain)
    }
}

data class WelcomeViewModelState(val loading: Boolean = true)

sealed interface WelcomeViewModelEffect {
    data object NavigateToLogin : WelcomeViewModelEffect
    data object NavigateToMain : WelcomeViewModelEffect
}
