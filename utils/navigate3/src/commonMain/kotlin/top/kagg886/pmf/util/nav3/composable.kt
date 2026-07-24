package top.kagg886.pmf.util.nav3

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavEntryDecorator
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay


/**
 * ================================================
 * Author:     iveou
 * Created on: 2026/7/24 11:10
 * ================================================
 */

/** The owner made available while a [NavGraph.Route] is composing its chrome. */
public val LocalNavRouteViewModelStoreOwner: ProvidableCompositionLocal<ViewModelStoreOwner?> =
    compositionLocalOf { null }

@Composable
public fun <T : Any> NavDisplay(
    controller: NavController<T>,
    modifier: Modifier = Modifier,
    entryDecorators: List<NavEntryDecorator<NavChain<T>>> = listOf(
        rememberSaveableStateHolderNavEntryDecorator<NavChain<T>>(),
        rememberViewModelStoreNavEntryDecorator<NavChain<T>>(),
    ),
): Unit {
    NavDisplay(
        backStack = controller.backStack,
        modifier = modifier,
        entryDecorators = entryDecorators,
        entryProvider = { chain ->
            controller.graph.entryFor(chain) { routeKey -> controller.routeStoreFor(chain, routeKey) }
        },
    )
}
