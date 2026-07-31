package top.kagg886.pmf.util.nav3

import androidx.lifecycle.ViewModelStore
import androidx.navigation3.runtime.NavBackStack

/** Owns complete history chains and the route-scoped ViewModel stores belonging to each route prefix. */
public class NavController<T : SerializableNavKey>(
    public val graph: NavGraph<T>,
    startDestination: T,
    public val backStack: NavBackStack<NavChain<T>> = NavBackStack(),
) {
    private val routeStores = mutableMapOf<NavChain<T>, ViewModelStore>()

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
            backStack.removeLastOrNull()
        }
        if (backStack.lastOrNull() != chain) {
            backStack += chain
        }
        clearUnreferencedRouteStores()
    }

    /** Removes exactly one history record from the top of the back stack. */
    public fun popBackStack(): Boolean {
        backStack.removeLastOrNull() ?: return false
        clearUnreferencedRouteStores()
        return true
    }

    /** Removes the first history record equal to [chain], regardless of its position. */
    public fun removeBackStack(chain: NavChain<T>): Boolean {
        val index = backStack.indexOf(chain)
        if (index < 0) return false

        backStack.removeAt(index)
        clearUnreferencedRouteStores()
        return true
    }

    /** Removes the graph-derived history record for [key], regardless of its position. */
    public fun removeBackStack(key: T): Boolean = removeBackStack(graph.chainFor(key))

    internal fun routeStoreFor(chain: NavChain<T>, routeKey: T): ViewModelStore {
        val routeIndex = chain.keys.indexOf(routeKey)
        require(routeIndex in 0..<chain.keys.lastIndex) {
            "$routeKey is not a parent route in $chain"
        }
        val routeScope = NavChain(chain.keys.take(routeIndex + 1))
        return routeStores.getOrPut(routeScope) { ViewModelStore() }
    }

    private fun clearUnreferencedRouteStores(): Unit {
        val unreferencedScopes = routeStores.keys.filter { scope ->
            backStack.none { chain ->
                chain.keys.size > scope.keys.size &&
                    chain.keys.take(scope.keys.size) == scope.keys
            }
        }
        unreferencedScopes.forEach { scope ->
            routeStores.remove(scope)?.clear()
        }
    }

    public fun clear(): Unit {
        routeStores.values.forEach(ViewModelStore::clear)
        routeStores.clear()
        backStack.clear()
    }
}
