package top.kagg886.pmf.util.nav3

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.saveable.rememberSerializable
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.ViewModelStoreProvider
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavEntryDecorator
import androidx.navigation3.runtime.rememberDecoratedNavEntries
import androidx.navigation3.runtime.serialization.NavBackStackSerializer
import androidx.navigation3.ui.NavDisplay
import androidx.savedstate.compose.LocalSavedStateRegistryOwner
import androidx.savedstate.serialization.SavedStateConfiguration
import kotlinx.serialization.PolymorphicSerializer

/** Remembers a leaf-only back stack using the application's generated polymorphic serializers. */
@Composable
public fun rememberNavController(
    graph: NavGraph<SerializableNavKey>,
    startDestination: SerializableNavKey,
    config: NavConfig<SerializableNavKey>,
): NavController<SerializableNavKey> {
    val backStack = rememberSerializable(
        configuration = SavedStateConfiguration {
            serializersModule = config.serializersModule
        },
        serializer = NavBackStackSerializer(PolymorphicSerializer(SerializableNavKey::class)),
    ) {
        NavBackStack(graph.resolveLeaf(startDestination))
    }
    return remember(graph, backStack) { NavController(graph, backStack) }
}

/** Renders the graph as nested flat NavDisplays derived from one leaf-only back stack. */
@Composable
public fun <T : SerializableNavKey> NavDisplay(
    controller: NavController<T>,
    config: NavConfig<T>,
    modifier: Modifier = Modifier,
): Unit {
    val parentOwner = checkNotNull(LocalViewModelStoreOwner.current) {
        "NavDisplay requires a parent ViewModelStoreOwner"
    }
    val savedStateOwner = LocalSavedStateRegistryOwner.current
    val provider = remember(parentOwner, controller.graph) {
        ViewModelStoreProvider(parentOwner, controller.graph)
    }
    val saveableStateHolder = rememberSaveableStateHolder()
    val lifecycle = remember(controller, provider, saveableStateHolder) {
        NavScopeLifecycle(provider, saveableStateHolder)
    }

    lifecycle.updateBackStackReferences(
        counts = controller.displayFrame.contentKeyReferenceCounts(),
        entryContentKeys = controller.displayFrame.entryContentKeys(),
    )
    DisposableEffect(controller, lifecycle) {
        controller.attachScopeLifecycle(lifecycle)
        onDispose { controller.detachScopeLifecycle(lifecycle) }
    }

    val decorators = remember(lifecycle, savedStateOwner) {
        listOf(
            NavEntryDecorator<T> { entry ->
                DisposableEffect(entry.contentKey, lifecycle) {
                    val token = lifecycle.acquireSaveableState(entry.contentKey as String)
                    onDispose { token.close() }
                }
                saveableStateHolder.SaveableStateProvider(entry.contentKey) { entry.Content() }
            },
            NavEntryDecorator<T> { entry ->
                val scopeKey = checkNotNull(entry.metadata[ViewModelScopeContentKey]) as String
                val owner = lifecycle.ownerFor(scopeKey, savedStateOwner)
                DisposableEffect(scopeKey, lifecycle) {
                    val token = lifecycle.acquire(scopeKey)
                    onDispose { token.close() }
                }
                val ancestors = LocalNavRouteViewModelStoreOwners.current
                CompositionLocalProvider(
                    LocalViewModelStoreOwner provides owner,
                    LocalNavViewModelContext provides NavViewModelContext(owner, ancestors),
                ) {
                    entry.Content()
                }
            },
        )
    }

    RenderFrame(
        frame = controller.displayFrame,
        controller = controller,
        config = config,
        decorators = decorators,
        modifier = modifier,
    )
}

@Composable
private fun <T : SerializableNavKey> RenderFrame(
    frame: NavDisplayFrame<T>,
    controller: NavController<T>,
    config: NavConfig<T>,
    decorators: List<NavEntryDecorator<T>>,
    modifier: Modifier = Modifier,
) {
    val rawEntries = frame.entries.map { frameEntry ->
        val node = frameEntry.node
        val entryContentKey = frame.entryContentKey(frameEntry)
        key(entryContentKey) {
            remember(entryContentKey, node, frameEntry.childFrame, config, decorators) {
                NavEntry(
                    key = frameEntry.key,
                    contentKey = entryContentKey,
                    metadata = when (node) {
                        is NavGraph.Destination<T> -> node.metadata +
                            (ViewModelScopeContentKey to frameEntry.key.contentKey())
                        is NavGraph.Route<T> -> mapOf(ViewModelScopeContentKey to frameEntry.key.contentKey())
                    },
                ) { entryKey ->
                    key(frame.scope, frameEntry.historyIndex, entryKey::class) {
                        when (node) {
                            is NavGraph.Destination<T> -> node.content(entryKey)
                            is NavGraph.Route<T> -> {
                                val routeOwner = checkNotNull(LocalViewModelStoreOwner.current)
                                val ancestors = LocalNavRouteViewModelStoreOwners.current
                                val childFrame = checkNotNull(frameEntry.childFrame)
                                CompositionLocalProvider(
                                    LocalNavRouteViewModelStoreOwners provides listOf(routeOwner) + ancestors,
                                    LocalNavViewModelContext provides NavViewModelContext(routeOwner, ancestors),
                                ) {
                                    node.content {
                                        RenderFrame(
                                            frame = childFrame,
                                            controller = controller,
                                            config = config.merge(node.config),
                                            decorators = decorators,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    val entries = rememberDecoratedNavEntries(rawEntries, decorators)
    NavDisplay(
        entries = entries,
        onBack = { controller.popBackStack() },
        modifier = modifier,
        sceneStrategies = config.sceneStrategies,
        sceneDecoratorStrategies = config.sceneDecoratorStrategies,
        sizeTransform = config.sizeTransform,
        transitionSpec = config.transitionSpec,
        popTransitionSpec = config.popTransitionSpec,
        predictivePopTransitionSpec = config.predictivePopTransitionSpec,
    )
}

private const val ViewModelScopeContentKey: String =
    "top.kagg886.pmf.util.nav3.ViewModelScopeContentKey"
