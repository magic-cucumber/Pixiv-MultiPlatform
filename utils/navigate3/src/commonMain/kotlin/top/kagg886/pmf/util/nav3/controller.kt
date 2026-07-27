package top.kagg886.pmf.util.nav3

import androidx.lifecycle.ViewModelStore
import androidx.navigation3.runtime.NavBackStack

/** Owns complete history chains and the route-scoped ViewModel stores belonging to each chain. */
public class NavController<T : SerializableNavKey>(
    public val graph: NavGraph<T>,
    startDestination: T,
    public val backStack: NavBackStack<NavChain<T>> = NavBackStack(),
) {
    private val routeStores = mutableMapOf<NavChain<T>, MutableMap<T, ViewModelStore>>()

    init {
        if (backStack.isEmpty()) navigate(startDestination)
    }

    /** Replaces the current branch with the graph-derived chain for [key]. */
    public fun navigate(key: T): Unit = navigate(graph.chainFor(key))

    /**
     * Pops entries up to the deepest key shared by the current and target chains, then
     * navigates to [chain]. Route wrapper keys can be shared without being entries themselves, so
     * they still cause the current entry to be removed before the target is added.
     */
    public fun navigate(chain: NavChain<T>): Unit {
        graph.entryFor(chain) { routeStoreFor(chain, it) }
        val commonKey = backStack.lastOrNull()
            ?.keys
            ?.zip(chain.keys)
            ?.takeWhile { (current, target) -> current == target }
            ?.lastOrNull()
            ?.first

        while (backStack.isNotEmpty() && backStack.last().destination != commonKey) {
            popBackStack()
        }
        if (backStack.lastOrNull() != chain) {
            backStack += chain
        }
    }

    /** Removes exactly one history record from the top of the back stack. */
    public fun popBackStack(): Boolean {
        val removed = backStack.removeLastOrNull() ?: return false
        clearUnreferencedRouteStores(removed)
        return true
    }

    /** Removes the first history record equal to [chain], regardless of its position. */
    public fun removeBackStack(chain: NavChain<T>): Boolean {
        val index = backStack.indexOf(chain)
        if (index < 0) return false

        val removed = backStack.removeAt(index)
        clearUnreferencedRouteStores(removed)
        return true
    }

    /** Removes the graph-derived history record for [key], regardless of its position. */
    public fun removeBackStack(key: T): Boolean = removeBackStack(graph.chainFor(key))

    internal fun routeStoreFor(chain: NavChain<T>, routeKey: T): ViewModelStore =
        routeStores.getOrPut(chain) { mutableMapOf() }.getOrPut(routeKey) { ViewModelStore() }

    private fun clearUnreferencedRouteStores(chain: NavChain<T>): Unit {
        if (chain in backStack) return
        routeStores.remove(chain)?.values?.forEach(ViewModelStore::clear)
    }

    public fun clear(): Unit {
        routeStores.values.forEach { stores -> stores.values.forEach(ViewModelStore::clear) }
        routeStores.clear()
        backStack.clear()
    }
}
