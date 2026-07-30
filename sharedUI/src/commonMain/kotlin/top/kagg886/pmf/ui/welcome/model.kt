package top.kagg886.pmf.ui.welcome

import androidx.lifecycle.ViewModel
import org.orbitmvi.orbit.OrbitContainer
import org.orbitmvi.orbit.OrbitContainerHost
import org.orbitmvi.orbit.viewmodel.orbitContainer
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

class WelcomeViewModel : ViewModel(),
    OrbitContainerHost<WelcomeViewModelState, WelcomeViewModelState, WelcomeViewModelEffect> {

    private val initialized = Store.of(preferencePath / "welcome.preferences_pb")
    private val login = Store.of(preferencePath / "login-properties.preferences_pb")

    override val container: OrbitContainer<WelcomeViewModelState, WelcomeViewModelState, WelcomeViewModelEffect> =
        orbitContainer(WelcomeViewModelState()) {
            if (initialized.get("initialized") { false }) {
                confirmInitialized()
                return@orbitContainer
            }
            reduce { state.copy(loading = false) }
        }


    fun confirmInitialized() = intent {
        initialized.set("initialized", true)
        if (login.get("refresh_token") { "" }.isBlank()) {
            postSideEffect(WelcomeViewModelEffect.NavigateToLogin)
            return@intent
        }
        postSideEffect(WelcomeViewModelEffect.NavigateToMain)
    }
}

data class WelcomeViewModelState(val loading: Boolean = true)

sealed interface WelcomeViewModelEffect {
    data object NavigateToLogin : WelcomeViewModelEffect
    data object NavigateToMain : WelcomeViewModelEffect
}
