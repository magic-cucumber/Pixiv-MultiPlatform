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
import androidx.lifecycle.ViewModelStore
import androidx.navigation3.runtime.NavBackStack
import kotlinx.coroutines.test.TestResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class RootRouteViewModel : ViewModel() {
    var cleared: Boolean = false

    override fun onCleared(): Unit {
        cleared = true
    }
}

class MainRouteViewModel : ViewModel() {
    var cleared: Boolean = false

    override fun onCleared(): Unit {
        cleared = true
    }
}

class ChildViewModel : ViewModel() {
    var cleared: Boolean = false

    override fun onCleared(): Unit {
        cleared = true
    }
}

class MissingParentViewModel : ViewModel() {
    init {
        instances++
    }

    companion object {
        var instances: Int = 0
    }
}

@OptIn(ExperimentalTestApi::class)
class NavControllerTest {
    private sealed interface Key : SerializableNavKey
    private data object Root : Key
    private data object Login : Key
    private data object Main : Key
    private data object Gallery : Key
    private data class Detail(val id: Long) : Key

    @Test
    fun restoresProvidedBackStack_withoutNavigatingToStartDestination() {
        val restoredBackStack = NavBackStack(NavChain<Key>(Root, Main, Detail(42)))

        val controller = NavController(graph, Gallery, restoredBackStack)

        assertEquals(listOf(NavChain<Key>(Root, Main, Detail(42))), controller.backStack)
    }

    @Test
    fun nestedRoutes_resolveEveryParentAndRouteStartDestination(): Unit {
        assertEquals(NavChain<Key>(Root, Main, Gallery), graph.chainFor(Gallery))
        assertEquals(NavChain<Key>(Root, Main, Gallery), graph.chainFor(Main))
        assertEquals(NavChain<Key>(Root, Main, Gallery), graph.chainFor(Root))
        assertEquals(NavChain<Key>(Root, Login), graph.chainFor(Login))

        val controller = NavController(graph, Login)
        controller.navigate(Main)
        assertEquals(NavChain<Key>(Root, Main, Gallery), controller.backStack.last())
    }

    @Test
    fun nestedRoutes_rejectChainMissingOuterRoute(): Unit {
        assertFailsWith<IllegalArgumentException> {
            graph.entryFor(NavChain<Key>(Main, Gallery)) { ViewModelStore() }
        }
    }

    @Test
    fun display_navigatesToInnermostRoute(): TestResult = runComposeUiTest {
        val controller = NavController(graph, Gallery)

        setContent { NavDisplay(controller) }
        onNodeWithTag("root").assertIsDisplayed()
        onNodeWithTag("main").assertIsDisplayed()
        onNodeWithTag("gallery").assertIsDisplayed()

        controller.navigate(Detail(42))
        waitForIdle()

        onNodeWithTag("root").assertIsDisplayed()
        onNodeWithTag("main").assertIsDisplayed()
        onNodeWithTag("detail").assertIsDisplayed()

        controller.navigate(Login)
        waitForIdle()

        onNodeWithTag("root").assertIsDisplayed()
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

        assertEquals(NavChain<Key>(Root, Login), controller.backStack.last())
        onNodeWithTag("login").assertIsDisplayed()
    }

    @Test
    fun destinationViewModel_findsImmediateParentWithOrdinaryViewModelCall(): TestResult = runComposeUiTest {
        rootViewModel = null
        mainViewModel = null
        childViewModel = null
        mainViewModelFromGallery = null
        mainViewModelFromDetail = null
        rootViewModelFromDetail = null
        val controller = NavController(graph, Gallery)

        setContent { NavDisplay(controller) }
        onNodeWithTag("gallery").assertIsDisplayed()

        assertEquals(assertNotNull(mainViewModel), assertNotNull(mainViewModelFromGallery))
    }

    @Test
    fun destinationViewModel_findsNestedAncestors(): TestResult = runComposeUiTest {
        rootViewModel = null
        mainViewModel = null
        rootViewModelFromMain = null
        rootViewModelFromDetail = null
        mainViewModelFromDetail = null
        val controller = NavController(graph, Detail(42))

        setContent { NavDisplay(controller) }
        onNodeWithTag("detail").assertIsDisplayed()

        assertEquals(assertNotNull(rootViewModel), assertNotNull(rootViewModelFromDetail))
        assertEquals(assertNotNull(rootViewModel), assertNotNull(rootViewModelFromMain))
        assertEquals(assertNotNull(mainViewModel), assertNotNull(mainViewModelFromDetail))
    }

    @Test
    fun siblingDestinations_reuseRouteViewModels(): TestResult = runComposeUiTest {
        rootViewModel = null
        mainViewModel = null
        mainViewModelFromGallery = null
        mainViewModelFromDetail = null
        val controller = NavController(graph, Gallery)

        setContent { NavDisplay(controller) }
        onNodeWithTag("gallery").assertIsDisplayed()
        val initialRootViewModel = assertNotNull(rootViewModel)
        val initialMainViewModel = assertNotNull(mainViewModel)

        controller.navigate(Detail(42))
        waitForIdle()

        assertEquals(initialRootViewModel, assertNotNull(rootViewModel))
        assertEquals(initialMainViewModel, assertNotNull(mainViewModel))
        assertEquals(initialMainViewModel, assertNotNull(mainViewModelFromDetail))
    }

    @Test
    fun leavingNestedRoute_clearsNestedModelButRetainsOuterModel(): TestResult = runComposeUiTest {
        rootViewModel = null
        mainViewModel = null
        childViewModel = null
        val controller = NavController(graph, Detail(42))

        setContent { NavDisplay(controller) }
        onNodeWithTag("detail").assertIsDisplayed()
        val rootRouteViewModel = assertNotNull(rootViewModel)
        val mainRouteViewModel = assertNotNull(mainViewModel)
        val detailViewModel = assertNotNull(childViewModel)

        controller.navigate(Login)
        waitForIdle()

        assertTrue(detailViewModel.cleared)
        assertTrue(mainRouteViewModel.cleared)
        assertTrue(!rootRouteViewModel.cleared)
        onNodeWithTag("root").assertIsDisplayed()
        onNodeWithTag("login").assertIsDisplayed()

        controller.clear()
        assertTrue(rootRouteViewModel.cleared)
    }

    @Test
    fun missingParentViewModel_failsFastWithoutCreatingIt(): TestResult = runComposeUiTest {
        MissingParentViewModel.instances = 0
        val controller = NavController(missingParentGraph, MissingParentDestination)

        assertFailsWith<IllegalStateException> {
            setContent { NavDisplay(controller) }
            waitForIdle()
        }
        assertEquals(0, MissingParentViewModel.instances)
    }

    @Test
    fun removeBackStack_clearsOnlyRouteViewModelsNoLongerReferenced(): Unit {
        val galleryChain = NavChain<Key>(Root, Main, Gallery)
        val controller = NavController(graph, Gallery)
        val rootRouteViewModel = ChildViewModel()
        val mainRouteViewModel = ChildViewModel()
        controller.routeStoreFor(galleryChain, Root).put("route", rootRouteViewModel)
        controller.routeStoreFor(galleryChain, Main).put("route", mainRouteViewModel)
        controller.backStack += galleryChain

        assertTrue(controller.removeBackStack(galleryChain))
        assertTrue(!rootRouteViewModel.cleared)
        assertTrue(!mainRouteViewModel.cleared)
        assertEquals(listOf(galleryChain), controller.backStack)

        assertTrue(controller.removeBackStack(Gallery))
        assertTrue(rootRouteViewModel.cleared)
        assertTrue(mainRouteViewModel.cleared)
        assertTrue(controller.backStack.isEmpty())
    }

    private companion object {
        private var rootViewModel: RootRouteViewModel? = null
        private var mainViewModel: MainRouteViewModel? = null
        private var rootViewModelFromMain: RootRouteViewModel? = null
        private var childViewModel: ChildViewModel? = null
        private var mainViewModelFromGallery: MainRouteViewModel? = null
        private var mainViewModelFromDetail: MainRouteViewModel? = null
        private var rootViewModelFromDetail: RootRouteViewModel? = null

        private val graph: NavGraph<Key> = createNavGraph {
            route(
                parent = Root,
                startDestination = Main,
                content = { child ->
                    rootViewModel = viewModel { RootRouteViewModel() }
                    TestNode("root", child)
                },
            ) {
                destination<Login> { TestNode("login") }
                route(
                    parent = Main,
                    startDestination = Gallery,
                    content = { child ->
                        mainViewModel = viewModel { MainRouteViewModel() }
                        rootViewModelFromMain = viewModel()
                        TestNode("main", child)
                    },
                ) {
                    destination<Gallery> {
                        mainViewModelFromGallery = viewModel()
                        TestNode("gallery")
                    }
                    destination<Detail> {
                        childViewModel = viewModel { ChildViewModel() }
                        mainViewModelFromDetail = viewModel()
                        rootViewModelFromDetail = viewModel()
                        TestNode("detail")
                    }
                }
            }
        }

        private sealed interface MissingParentKey : SerializableNavKey
        private data object MissingParentRoute : MissingParentKey
        private data object MissingParentDestination : MissingParentKey

        private val missingParentGraph: NavGraph<MissingParentKey> = createNavGraph {
            route(
                parent = MissingParentRoute,
                startDestination = MissingParentDestination,
                content = { child -> TestNode("missing-parent-route", child) },
            ) {
                destination<MissingParentDestination> {
                    viewModel<MissingParentViewModel>()
                    TestNode("missing-parent-destination")
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
