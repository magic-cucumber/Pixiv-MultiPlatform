package top.kagg886.pmf.util.nav3

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.SizeTransform
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSerializable
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavEntryDecorator
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.runtime.serialization.NavBackStackSerializer
import androidx.navigation3.scene.DialogSceneStrategy
import androidx.navigation3.scene.Scene
import androidx.navigation3.ui.NavDisplay
import androidx.navigation3.ui.defaultPopTransitionSpec
import androidx.navigation3.ui.defaultPredictivePopTransitionSpec
import androidx.navigation3.ui.defaultTransitionSpec
import androidx.navigationevent.NavigationEvent
import androidx.savedstate.serialization.SavedStateConfiguration
import kotlinx.serialization.PolymorphicSerializer
import kotlinx.serialization.modules.SerializersModule


/**
 * ================================================
 * Author:     iveou
 * Created on: 2026/7/24 11:10
 * ================================================
 */

/** The owner made available while a [NavGraph.Route] is composing its chrome. */
@Deprecated(
    message = "Use navigate3.viewModel() to resolve parent route models without reading a CompositionLocal.",
)
public val LocalNavRouteViewModelStoreOwner: ProvidableCompositionLocal<ViewModelStoreOwner?> =
    compositionLocalOf { null }

/**
 * Remembers a [NavController] whose navigation chains survive state restoration.
 *
 * [NavController]'s graph and ViewModel stores are runtime-only resources, so this persists only
 * its [NavController.backStack]. [serializersModule] must register every concrete
 * [SerializableNavKey] used by [graph].
 */
@Composable
public fun rememberNavController(
    graph: NavGraph<SerializableNavKey>,
    startDestination: SerializableNavKey,
    serializersModule: SerializersModule,
): NavController<SerializableNavKey> {
    val backStack = rememberSerializable(
        configuration = SavedStateConfiguration {
            this.serializersModule = serializersModule
        },
        serializer = NavBackStackSerializer(
            NavChain.serializer(PolymorphicSerializer(SerializableNavKey::class)),
        ),
    ) {
        NavBackStack(graph.chainFor(startDestination))
    }
    return remember(graph, startDestination, backStack) {
        NavController(graph, startDestination, backStack)
    }
}

@Composable
public fun <T : SerializableNavKey> NavDisplay(
    controller: NavController<T>,
    modifier: Modifier = Modifier,
    sceneStrategies: List<androidx.navigation3.scene.SceneStrategy<NavChain<T>>> = listOf(
        DialogSceneStrategy(),
    ),
    entryDecorators: List<NavEntryDecorator<NavChain<T>>> = listOf(
        rememberSaveableStateHolderNavEntryDecorator<NavChain<T>>(),
        rememberViewModelStoreNavEntryDecorator<NavChain<T>>(),
    ),
    sizeTransform: SizeTransform? = null,
    transitionSpec: AnimatedContentTransitionScope<Scene<NavChain<T>>>.() -> ContentTransform =
        defaultTransitionSpec(),
    popTransitionSpec: AnimatedContentTransitionScope<Scene<NavChain<T>>>.() -> ContentTransform =
        defaultPopTransitionSpec(),
    predictivePopTransitionSpec:
    AnimatedContentTransitionScope<Scene<NavChain<T>>>.(
        @NavigationEvent.SwipeEdge Int
    ) -> ContentTransform =
        defaultPredictivePopTransitionSpec(),
): Unit {
    NavDisplay(
        backStack = controller.backStack,
        onBack = { controller.popBackStack() },
        modifier = modifier,
        entryDecorators = entryDecorators,
        sceneStrategies = sceneStrategies,
        entryProvider = { chain ->
            controller.graph.entryFor(chain) { routeKey -> controller.routeStoreFor(chain, routeKey) }
        },
        sizeTransform = sizeTransform,
        transitionSpec = transitionSpec,
        popTransitionSpec = popTransitionSpec,
        predictivePopTransitionSpec = predictivePopTransitionSpec,
    )
}
