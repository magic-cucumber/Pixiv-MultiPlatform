package top.kagg886.pmf.fronted.welcome

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import org.orbitmvi.orbit.OrbitContainer
import org.orbitmvi.orbit.OrbitContainerHost
import org.orbitmvi.orbit.viewmodel.orbitContainer
import top.kagg886.pmf.util.Store
import top.kagg886.pmf.util.dataPath
import top.kagg886.pmf.util.flow
import top.kagg886.pmf.util.set

/**
 * ================================================
 * Author:     iveou
 * Created on: 2026/7/27 13:41
 * ================================================
 */

class WelcomeViewModel : ViewModel(),
    OrbitContainerHost<WelcomeViewModelState, WelcomeViewModelState, WelcomeViewModelEffect> {

    private val initialized = Store.of(dataPath / "welcome.preferences_pb")

    override val container: OrbitContainer<WelcomeViewModelState, WelcomeViewModelState, WelcomeViewModelEffect> =
        orbitContainer(WelcomeViewModelState) {
            intent {
                if (initialized.flow(viewModelScope, "initialized") { false }.value) {
                    confirmInitialized()
                }
            }
        }


    fun confirmInitialized() = intent {
        initialized.set("initialized",true)
        //TODO 有凭证前往Main，无凭证前往Login
    }
}

data object WelcomeViewModelState

sealed interface WelcomeViewModelEffect {
    data object NavigateToLogin : WelcomeViewModelEffect
    data object NavigateToMain : WelcomeViewModelEffect
}
