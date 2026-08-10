package top.kagg886.pmf.util.nav3

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.lifecycle.ViewModel
import androidx.navigation3.runtime.NavBackStack
import kotlinx.coroutines.test.TestResult
import kotlinx.serialization.Serializable
import kotlinx.serialization.modules.SerializersModule
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

private open class TrackedViewModel : ViewModel() {
    var clearedCount: Int = 0
        private set

    override fun onCleared() {
        clearedCount++
    }
}

private class RootViewModel : TrackedViewModel()
private class Level1ViewModel : TrackedViewModel()
private class Level2ViewModel : TrackedViewModel()
private class Level3ViewModel : TrackedViewModel()
private class Level4ViewModel : TrackedViewModel()
private class SharedLeafViewModel : TrackedViewModel()
private class DialogViewModel : TrackedViewModel()
private class MissingParentViewModel : ViewModel()

@OptIn(ExperimentalTestApi::class)
class NavControllerTest {
    // 测试内容：解析五层嵌套路由及各级起始节点；预期目标：所有路由键均解析到正确叶子，路径包含完整父子层级。
    @Test
    fun graph_resolvesFiveNestedRoutesAndEveryStartDestination() {
        assertEquals(listOf(Root, Level1, Level2, Level3, Level4, LeafA), graph.resolvePath(LeafA))
        assertEquals(LeafA, graph.resolveLeaf(Root))
        assertEquals(LeafA, graph.resolveLeaf(Level1))
        assertEquals(LeafA, graph.resolveLeaf(Level2))
        assertEquals(LeafA, graph.resolveLeaf(Level3))
        assertEquals(LeafA, graph.resolveLeaf(Level4))
        assertEquals(listOf(Root, Login), graph.resolvePath(Login))
    }

    // 测试内容：构建含未知键、重复目标、非法起始节点及空路由的导航图；预期目标：所有非法图结构均被明确拒绝。
    @Test
    fun graph_rejectsUnknownKeysDuplicateDestinationsAndInvalidStarts() {
        assertFailsWith<IllegalStateException> { graph.resolvePath(Unknown) }
        assertFailsWith<IllegalStateException> {
            createNavGraph<Key> {
                destination<LeafA> { }
                destination<LeafA> { }
            }
        }
        assertFailsWith<IllegalStateException> {
            createNavGraph<Key> {
                route(parent = Root, startDestination = Unknown, content = { it() }) {
                    destination<Login> { }
                }
            }
        }
        assertFailsWith<IllegalStateException> {
            createNavGraph<Key> {
                route(parent = Root, startDestination = Login, content = { it() }) { }
            }
        }
    }

    // 测试内容：控制器初始化与连续 navigate 的历史记录行为；预期目标：backStack 只保存叶子且每次 navigate 都无条件追加。
    @Test
    fun controllerStoresLeavesOnlyAndNavigateAlwaysAppends() {
        val controller = NavController(graph, Level4)
        assertEquals(listOf(LeafA), controller.backStack)

        controller.navigate(Detail(42))
        controller.navigate(Detail(42))
        controller.navigate(Level4)

        assertEquals(listOf(LeafA, Detail(42), Detail(42), LeafA), controller.backStack)
        assertEquals(listOf(Root, Level1, Level2, Level3, Level4, LeafA), controller.currentPath)
    }

    // 测试内容：连续调用 popBackStack 弹出历史栈；预期目标：每次仅移除栈顶一项，并始终保留最后一个可显示节点。
    @Test
    fun popBackStackRemovesExactlyOneTopRecordAndKeepsTheFinalRecord() {
        val controller = NavController(graph, LeafA)
        controller.navigate(Detail(1))
        controller.navigate(Detail(2))

        assertTrue(controller.popBackStack())
        assertEquals(listOf(LeafA, Detail(1)), controller.backStack)
        assertTrue(controller.popBackStack())
        assertEquals(listOf(LeafA), controller.backStack)
        assertFalse(controller.popBackStack())
        assertEquals(listOf(LeafA), controller.backStack)
    }

    // 测试内容：在一次 update 中先弹出栈顶再压入新页面；预期目标：外部观察者只能看到原状态和最终状态，不会看到中间空栈。
    @Test
    fun updateCommitsPopAndPushAtomically() {
        val controller = NavController(graph, LeafA)
        val observed = mutableListOf<List<Key>>()
        val observer = Snapshot.registerApplyObserver { _, _ -> observed += controller.backStack.toList() }
        try {
            assertTrue(controller.update {
                pop()
                push(Login)
            })
            Snapshot.sendApplyNotifications()
        } finally {
            observer.dispose()
        }

        assertEquals(listOf(Login), controller.backStack)
        assertTrue(observed.none { it.isEmpty() })
        assertEquals(listOf(Login), observed.last())
    }

    // 测试内容：执行空栈、异常及嵌套 update 等非法原子更新；预期目标：更新全部回滚且原 backStack 保持不变。
    @Test
    fun updateRollsBackOnEmptyFailureExceptionAndNestedUpdate() {
        val controller = NavController(graph, LeafA)
        assertFailsWith<IllegalArgumentException> { controller.update { pop() } }
        assertEquals(listOf(LeafA), controller.backStack)

        assertFailsWith<IllegalStateException> {
            controller.update {
                push(Detail(1))
                error("boom")
            }
        }
        assertEquals(listOf(LeafA), controller.backStack)

        assertFailsWith<IllegalStateException> {
            controller.update { controller.update { push(Login) } }
        }
        assertEquals(listOf(LeafA), controller.backStack)
    }

    // 测试内容：将跨 route、Dialog 和重复叶子的历史投影为显示树；预期目标：生成独立 route 实例、保留 Dialog 下层页面并正确统计引用。
    @Test
    fun projectionCreatesIndependentRouteOccurrencesAndKeepsDialogUnderlay() {
        val controller = NavController(graph, LeafA)
        controller.navigate(LoggerList)
        controller.navigate(LoggerDialog)
        controller.navigate(LeafA)

        val root = controller.displayFrame.entries.single()
        val rootChildren = assertNotNull(root.childFrame).entries
        assertEquals(listOf(Level1, Logger, Level1), rootChildren.map { it.key })

        val loggerChildren = assertNotNull(rootChildren[1].childFrame).entries
        assertEquals(listOf(LoggerList, LoggerDialog), loggerChildren.map { it.key })
        assertEquals(2, controller.displayFrame.contentKeyReferenceCounts()[Level1.contentKey()])
        assertEquals(2, controller.displayFrame.contentKeyReferenceCounts()[LeafA.contentKey()])
    }

    // 测试内容：使用已恢复的叶子 backStack 创建控制器；预期目标：完整采用恢复历史且不额外追加起始节点。
    @Test
    fun restoredLeafBackStackIsUsedWithoutAppendingStartDestination() {
        val restored = NavBackStack<Key>(LeafA, Detail(42), Login)
        val controller = NavController(graph, restored)

        assertEquals(listOf(LeafA, Detail(42), Login), controller.backStack)
        assertEquals(listOf(Root, Login), controller.currentPath)
    }

    // 测试内容：渲染包含五层父 route 的最深叶子页面；预期目标：所有父级 NavDisplay 与叶子均显示，叶子可取得正确父级 ViewModel。
    @Test
    fun displayRendersAllFiveParentsAndInnermostLeaf(): TestResult = runComposeUiTest {
        resetCapturedModels()
        val controller = NavController(graph, LeafA)

        setContent { NavDisplay(controller, testConfig) }

        listOf("root", "level1", "level2", "level3", "level4", "leaf-a").forEach {
            onNodeWithTag(it).assertIsDisplayed()
        }
        assertSame(assertNotNull(rootModel), assertNotNull(rootFromLeaf))
        assertSame(assertNotNull(level4Model), assertNotNull(level4FromLeaf))
    }

    // 测试内容：在普通页面之上导航到 Dialog 并弹出；预期目标：Dialog 作为覆盖层显示、下层页面保持可见，弹出后恢复下层页面。
    @Test
    fun dialogIsAnOverlayAboveItsSiblingAndPopRestoresSibling(): TestResult = runComposeUiTest {
        resetCapturedModels()
        val controller = NavController(graph, LoggerList)
        setContent { NavDisplay(controller, testConfig) }
        onNodeWithTag("logger-list").assertIsDisplayed()

        controller.navigate(LoggerDialog)
        waitForIdle()
        onNodeWithTag("logger-list").assertIsDisplayed()
        onNodeWithTag("logger-dialog").assertIsDisplayed()

        assertTrue(controller.popBackStack())
        waitForIdle()
        onNodeWithTag("logger-list").assertIsDisplayed()
    }

    // 测试内容：导航到两个 contentKey 相同的不同页面并逐一弹出；预期目标：二者共享 ViewModel，且仅在最后一个引用退出后清理一次。
    @Test
    fun equalContentKeysShareViewModelUntilTheirFinalReferenceExits(): TestResult = runComposeUiTest {
        resetCapturedModels()
        val controller = NavController(graph, SharedLeafA)
        var frame = controller.displayFrame
        repeat(5) { frame = assertNotNull(frame.entries.single().childFrame) }
        assertEquals(SharedLeafA::class, (frame.entries.single().node as NavGraph.Destination).type)
        setContent { NavDisplay(controller, testConfig) }
        onNodeWithTag("shared-a").assertIsDisplayed()
        val shared = assertNotNull(sharedModelFromA)

        controller.navigate(SharedLeafB)
        waitForIdle()
        onNodeWithTag("shared-b").assertIsDisplayed()
        assertSame(shared, assertNotNull(sharedModelFromB))

        assertTrue(controller.popBackStack())
        waitForIdle()
        assertEquals(0, shared.clearedCount)

        controller.update {
            pop()
            push(LeafA)
        }
        waitForIdle()
        assertEquals(1, shared.clearedCount)
    }

    // 测试内容：覆盖及移除拥有父 route ViewModel 的多段历史；预期目标：父 ViewModel 在仍有 route 引用时存活，并在最终引用退出后清理。
    @Test
    fun routeViewModelsSurviveCoverAndClearAfterFinalRouteOccurrence(): TestResult = runComposeUiTest {
        resetCapturedModels()
        val controller = NavController(graph, LeafA)
        setContent { NavDisplay(controller, testConfig) }
        onNodeWithTag("leaf-a").assertIsDisplayed()
        val level1 = assertNotNull(level1Model)
        val level4 = assertNotNull(level4Model)

        controller.navigate(Login)
        waitForIdle()
        assertEquals(0, level1.clearedCount)
        assertEquals(0, level4.clearedCount)

        controller.update {
            pop()
            pop()
            push(Login)
        }
        waitForIdle()
        assertEquals(1, level1.clearedCount)
        assertEquals(1, level4.clearedCount)
        assertEquals(0, assertNotNull(rootModel).clearedCount)
    }

    // 测试内容：界面仍持有组合引用时调用 controller.clear；预期目标：清理等待组合引用释放，并最终将每个作用域恰好清理一次。
    @Test
    fun controllerClearWaitsForCompositionTokensThenClearsEveryScope(): TestResult = runComposeUiTest {
        resetCapturedModels()
        val controller = NavController(graph, LeafA)
        var show by mutableStateOf(true)
        setContent { if (show) NavDisplay(controller, testConfig) }
        onNodeWithTag("leaf-a").assertIsDisplayed()
        val root = assertNotNull(rootModel)
        val level4 = assertNotNull(level4Model)

        controller.clear()
        assertEquals(0, root.clearedCount)
        assertEquals(0, level4.clearedCount)
        show = false
        waitForIdle()

        assertEquals(1, root.clearedCount)
        assertEquals(1, level4.clearedCount)
    }

    // 测试内容：叶子请求导航图中不存在的父级 ViewModel；预期目标：立即抛出异常且不会创建替代 ViewModel。
    @Test
    fun missingParentViewModelFailsWithoutCreatingAReplacement(): TestResult = runComposeUiTest {
        val controller = NavController(missingParentGraph, MissingParentLeaf)
        assertFailsWith<IllegalStateException> {
            setContent { NavDisplay(controller, missingParentConfig) }
            waitForIdle()
        }
    }

    private companion object {
        @Serializable
        private sealed interface Key : SerializableNavKey

        @Serializable private data object Root : Key
        @Serializable private data object Level1 : Key
        @Serializable private data object Level2 : Key
        @Serializable private data object Level3 : Key
        @Serializable private data object Level4 : Key
        @Serializable private data object LeafA : Key
        @Serializable private data class Detail(val id: Long) : Key
        @Serializable private data object SharedLeafA : Key {
            override fun contentKey(): String = "shared-leaf"
        }
        @Serializable private data object SharedLeafB : Key {
            override fun contentKey(): String = "shared-leaf"
        }
        @Serializable private data object Login : Key
        @Serializable private data object Logger : Key
        @Serializable private data object LoggerList : Key
        @Serializable private data object LoggerDialog : Key
        @Serializable private data object Unknown : Key

        private var rootModel: RootViewModel? = null
        private var level1Model: Level1ViewModel? = null
        private var level2Model: Level2ViewModel? = null
        private var level3Model: Level3ViewModel? = null
        private var level4Model: Level4ViewModel? = null
        private var rootFromLeaf: RootViewModel? = null
        private var level4FromLeaf: Level4ViewModel? = null
        private var sharedModelFromA: SharedLeafViewModel? = null
        private var sharedModelFromB: SharedLeafViewModel? = null
        private var dialogModel: DialogViewModel? = null

        private val graph: NavGraph<Key> = createNavGraph {
            route(parent = Root, startDestination = Level1, content = { child ->
                rootModel = viewModel { RootViewModel() }
                TestNode("root", child)
            }) {
                destination<Login> { TestNode("login") }
                route(parent = Logger, startDestination = LoggerList, content = { child ->
                    TestNode("logger", child)
                }) {
                    destination<LoggerList> { TestNode("logger-list") }
                    dialog<LoggerDialog> {
                        dialogModel = viewModel { DialogViewModel() }
                        TestNode("logger-dialog")
                    }
                }
                route(parent = Level1, startDestination = Level2, content = { child ->
                    level1Model = viewModel { Level1ViewModel() }
                    TestNode("level1", child)
                }) {
                    route(parent = Level2, startDestination = Level3, content = { child ->
                        level2Model = viewModel { Level2ViewModel() }
                        TestNode("level2", child)
                    }) {
                        route(parent = Level3, startDestination = Level4, content = { child ->
                            level3Model = viewModel { Level3ViewModel() }
                            TestNode("level3", child)
                        }) {
                            route(parent = Level4, startDestination = LeafA, content = { child ->
                                level4Model = viewModel { Level4ViewModel() }
                                TestNode("level4", child)
                            }) {
                                destination<LeafA> {
                                    rootFromLeaf = viewModel()
                                    level4FromLeaf = viewModel()
                                    TestNode("leaf-a")
                                }
                                destination<Detail> { TestNode("detail") }
                                destination<SharedLeafA> {
                                    sharedModelFromA = viewModel { SharedLeafViewModel() }
                                    TestNode("shared-a")
                                }
                                destination<SharedLeafB> {
                                    sharedModelFromB = viewModel { SharedLeafViewModel() }
                                    TestNode("shared-b")
                                }
                            }
                        }
                    }
                }
            }
        }

        private val testConfig = NavConfig<Key>(SerializersModule { })

        @Serializable
        private sealed interface MissingParentKey : SerializableNavKey
        @Serializable private data object MissingParentRoute : MissingParentKey
        @Serializable private data object MissingParentLeaf : MissingParentKey

        private val missingParentGraph = createNavGraph<MissingParentKey> {
            route(parent = MissingParentRoute, startDestination = MissingParentLeaf, content = { it() }) {
                destination<MissingParentLeaf> {
                    viewModel<MissingParentViewModel>()
                    TestNode("missing-parent")
                }
            }
        }
        private val missingParentConfig = NavConfig<MissingParentKey>(SerializersModule { })

        private fun resetCapturedModels() {
            rootModel = null
            level1Model = null
            level2Model = null
            level3Model = null
            level4Model = null
            rootFromLeaf = null
            level4FromLeaf = null
            sharedModelFromA = null
            sharedModelFromB = null
            dialogModel = null
        }

        @Composable
        private fun TestNode(tag: String, content: @Composable () -> Unit = {}) {
            Box(Modifier.testTag(tag)) {
                BasicText(tag)
                content()
            }
        }
    }
}
