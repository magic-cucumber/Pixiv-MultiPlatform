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

    /** Removes exactly one history record and clears every route store owned by it. */
    public fun popBackStack(): Boolean {
        val removed = backStack.removeLastOrNull() ?: return false
        routeStores.remove(removed)?.values?.forEach(ViewModelStore::clear)
        return true
    }

    internal fun routeStoreFor(chain: NavChain<T>, routeKey: T): ViewModelStore =
        routeStores.getOrPut(chain) { mutableMapOf() }.getOrPut(routeKey) { ViewModelStore() }

    public fun clear(): Unit {
        routeStores.values.forEach { stores -> stores.values.forEach(ViewModelStore::clear) }
        routeStores.clear()
        backStack.clear()
    }
}
