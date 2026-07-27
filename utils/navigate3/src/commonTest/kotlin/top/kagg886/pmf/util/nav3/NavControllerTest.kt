package top.kagg886.pmf.util.nav3

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation3.runtime.NavBackStack
import kotlinx.coroutines.test.TestResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ParentViewModel : ViewModel()

class ChildViewModel : ViewModel() {
    var cleared: Boolean = false

    override fun onCleared(): Unit {
        cleared = true
    }
}

@OptIn(ExperimentalTestApi::class)
class NavControllerTest {
    private sealed interface Key : SerializableNavKey
    private data object Login : Key
    private data object Main : Key
    private data object Gallery : Key
    private data class Detail(val id: Long) : Key

    @Test
    fun restoresProvidedBackStack_withoutNavigatingToStartDestination() {
        val restoredBackStack = NavBackStack(NavChain<Key>(Main, Detail(42)))

        val controller = NavController(graph, Gallery, restoredBackStack)

        assertEquals(listOf(NavChain<Key>(Main, Detail(42))), controller.backStack)
    }

    @Test
    fun display_navigatesToInnermostRoute(): TestResult = runComposeUiTest {
        val controller = NavController(graph, Gallery)

        setContent { NavDisplay(controller) }
        onNodeWithTag("gallery").assertIsDisplayed()

        controller.navigate(Detail(42))
        waitForIdle()

        onNodeWithTag("detail").assertIsDisplayed()

        controller.navigate(Login)
        waitForIdle()

        onNodeWithTag("login").assertIsDisplayed()
    }

    @Test
    fun parentChildNavigation_navigatesToLogin(): TestResult = runComposeUiTest {
        val controller = NavController(graph, Gallery)

        setContent { NavDisplay(controller) }
        onNodeWithTag("gallery").assertIsDisplayed()

        controller.navigate(Detail(42))
        waitForIdle()
        onNodeWithTag("detail").assertIsDisplayed()

        controller.navigate(Login)
        waitForIdle()

        assertEquals(NavChain<Key>(Login), controller.backStack.last())
        onNodeWithTag("login").assertIsDisplayed()
    }

    @Test
    fun parentChildViewModels_shareParentAndClearChildOnPop(): TestResult = runComposeUiTest {
        parentViewModel = null
        childViewModel = null
        parentViewModelFromChild = null
        val controller = NavController(graph, Gallery)

        setContent { NavDisplay(controller) }
        onNodeWithTag("gallery").assertIsDisplayed()

        controller.navigate(Detail(42))
        waitForIdle()

        assertEquals(assertNotNull(parentViewModel), assertNotNull(parentViewModelFromChild))
        val detailViewModel = assertNotNull(childViewModel)

        controller.navigate(Login)
        waitForIdle()

        assertTrue(detailViewModel.cleared)
        onNodeWithTag("login").assertIsDisplayed()
    }

    private companion object {
        private var parentViewModel: ParentViewModel? = null
        private var childViewModel: ChildViewModel? = null
        private var parentViewModelFromChild: ParentViewModel? = null
        private val testViewModelFactory = viewModelFactory {
            initializer { ParentViewModel() }
            initializer { ChildViewModel() }
        }

        private val graph: NavGraph<Key> = createNavGraph {
            destination<Login> { TestNode("login") }
            route(
                parent = Main,
                startDestination = Gallery,
                content = { child ->
                    parentViewModel = viewModel(factory = testViewModelFactory)
                    TestNode("main", child)
                }
            ) {
                destination<Gallery> { TestNode("gallery") }
                destination<Detail> {
                    childViewModel = viewModel(factory = testViewModelFactory)
                    parentViewModelFromChild = viewModel(
                        viewModelStoreOwner = checkNotNull(LocalNavRouteViewModelStoreOwner.current),
                        factory = testViewModelFactory,
                    )
                    TestNode("detail")
                }
            }
        }

        @Composable
        private fun TestNode(tag: String, content: @Composable () -> Unit = {}): Unit =
            Box(Modifier.testTag(tag)) {
                BasicText(tag)
                content()
            }
    }
}
