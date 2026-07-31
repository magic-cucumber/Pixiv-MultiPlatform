package top.kagg886.pmf.util.nav3

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.navigation3.runtime.NavEntry
import kotlinx.serialization.Serializable
import kotlin.reflect.KClass

/** One immutable Navigation 3 history record: every parent route and its visible destination. */
@Serializable
public data class NavChain<T : SerializableNavKey>(public val keys: List<T>) : SerializableNavKey {
    init {
        require(keys.isNotEmpty()) { "A navigation chain cannot be empty." }
    }

    public constructor(vararg keys: T) : this(keys.toList())

    public val destination: T get() = keys.last()
}

/**
 * Authoring-time navigation tree. Parent route keys are argument-free values (normally `data object`s),
 * so the graph can always create a complete [NavChain] for an arbitrary destination.
 */
public class NavGraph<T : SerializableNavKey> internal constructor(
    private val destinations: List<Destination<T>>,
) {
    public fun chainFor(key: T): NavChain<T> = chainFor(key, emptySet())

    private fun chainFor(key: T, resolvingRoutes: Set<T>): NavChain<T> {
        val routePath = destinations
            .asSequence()
            .map(Destination<T>::parents)
            .firstOrNull { parents -> parents.any { it.key == key } }
        if (routePath != null) {
            check(key !in resolvingRoutes) { "Route start destinations contain a cycle at $key" }
            val routeIndex = routePath.indexOfFirst { it.key == key }
            val route = routePath[routeIndex]
            val expectedPrefix = routePath.take(routeIndex + 1).map(Route<T>::key)
            val startChain = chainFor(route.startDestination, resolvingRoutes + key)
            require(startChain.keys.take(expectedPrefix.size) == expectedPrefix) {
                "Start destination ${route.startDestination} does not belong to route ${route.key}"
            }
            return startChain
        }
        val destination = destinations.firstOrNull { it.type.isInstance(key) }
            ?: error("No destination is registered for ${key::class.qualifiedName}")
        return NavChain(destination.parents.map(Route<T>::key) + key)
    }

    public fun entryFor(
        chain: NavChain<T>,
        routeStoreFor: (T) -> ViewModelStore,
    ): NavEntry<NavChain<T>> {
        val key = chain.destination
        val destination = destinations.firstOrNull { it.type.isInstance(key) }
            ?: error("No destination is registered for ${key::class.qualifiedName}")
        val expectedParents = destination.parents.map(Route<T>::key)
        require(chain.keys.dropLast(1) == expectedParents) {
            "Invalid navigation chain $chain for ${key::class.qualifiedName}."
        }
        return NavEntry(
            key = chain,
            metadata = destination.metadata,
            contentKey = chain.keys.joinToString { "," },
        ) {
            destination.content(key, routeStoreFor)
        }
    }

    @PublishedApi
    internal class Destination<T : Any> @PublishedApi internal constructor(
        @PublishedApi internal val type: KClass<out T>,
        @PublishedApi internal val metadata: Map<String, Any>,
        @PublishedApi internal val parents: List<Route<T>>,
        @PublishedApi internal val content: @Composable (T, (T) -> ViewModelStore) -> Unit,
    )

    @PublishedApi
    internal class Route<T : Any> @PublishedApi internal constructor(
        @PublishedApi internal val key: T,
        @PublishedApi internal val startDestination: T,
        @PublishedApi internal val content: @Composable (@Composable () -> Unit) -> Unit,
    )

    @Nav3Dsl
    public class Builder<T : SerializableNavKey> internal constructor() {
        @PublishedApi
        internal val destinations: MutableList<Destination<T>> = mutableListOf()

        public inline fun <reified K : T> destination(
            metadata: Map<String, Any> = emptyMap(),
            noinline content: @Composable (K) -> Unit,
        ): Unit = destination(K::class, metadata, content)

        public fun <K : T> destination(
            type: KClass<K>,
            metadata: Map<String, Any> = emptyMap(),
            content: @Composable (K) -> Unit,
        ): Unit {
            check(destinations.none { it.type == type }) { "Destination ${type.qualifiedName} is already registered" }
            destinations += Destination(type, metadata, emptyList(), { key, _ -> content(key as K) })
        }

        /** `parent` must be an argument-free route key, e.g. a `data object Main`. */
        public inline fun <reified K : T> route(
            parent: K,
            startDestination: T,
            noinline content: @Composable (@Composable () -> Unit) -> Unit,
            builder: RouteBuilder<T>.() -> Unit,
        ): Unit {
            val route = Route(parent, startDestination, content)
            val childBuilder = RouteBuilder<T>(listOf(route))
            childBuilder.builder()
            destinations += childBuilder.destinations
        }

        internal fun build(): NavGraph<T> = NavGraph(destinations.toList())
    }

    @Nav3Dsl
    public class RouteBuilder<T : SerializableNavKey> @PublishedApi internal constructor(
        @PublishedApi internal val parents: List<Route<T>>,
    ) {
        @PublishedApi
        internal val destinations: MutableList<Destination<T>> = mutableListOf()

        @Suppress("DEPRECATION")
        public inline fun <reified K : T> destination(
            metadata: Map<String, Any> = emptyMap(),
            noinline content: @Composable (K) -> Unit,
        ): Unit {
            check(destinations.none { it.type == K::class }) { "Destination ${K::class.qualifiedName} is already registered" }
            destinations += Destination(K::class, metadata, parents, { key, routeStoreFor ->
                val entryOwner = checkNotNull(LocalViewModelStoreOwner.current)
                var nestedContent: @Composable () -> Unit = {
                    val ancestorRouteOwners = LocalNavRouteViewModelStoreOwners.current
                    CompositionLocalProvider(
                        LocalViewModelStoreOwner provides entryOwner,
                        LocalNavViewModelContext provides NavViewModelContext(
                            currentOwner = entryOwner,
                            ancestorRouteOwners = ancestorRouteOwners,
                        ),
                    ) {
                        content(key as K)
                    }
                }
                for (route in parents.asReversed()) {
                    val childContent = nestedContent
                    nestedContent = {
                        val routeStore = routeStoreFor(route.key)
                        val routeOwner: ViewModelStoreOwner = object : ViewModelStoreOwner {
                            override val viewModelStore: ViewModelStore = routeStore
                        }
                        val ancestorRouteOwners = LocalNavRouteViewModelStoreOwners.current
                        CompositionLocalProvider(
                            LocalViewModelStoreOwner provides routeOwner,
                            LocalNavRouteViewModelStoreOwner provides routeOwner,
                            LocalNavRouteViewModelStoreOwners provides
                                listOf(routeOwner) + ancestorRouteOwners,
                            LocalNavViewModelContext provides NavViewModelContext(
                                currentOwner = routeOwner,
                                ancestorRouteOwners = ancestorRouteOwners,
                            ),
                        ) {
                            route.content {
                                childContent()
                            }
                        }
                    }
                }
                nestedContent()
            })
        }

        /** Declares a child route whose destinations inherit this builder's complete route path. */
        public inline fun <reified K : T> route(
            parent: K,
            startDestination: T,
            noinline content: @Composable (@Composable () -> Unit) -> Unit,
            builder: RouteBuilder<T>.() -> Unit,
        ): Unit {
            val route = Route(parent, startDestination, content)
            val childBuilder = RouteBuilder<T>(parents + route)
            childBuilder.builder()
            destinations += childBuilder.destinations
        }
    }
}

public fun <T : SerializableNavKey> createNavGraph(builder: NavGraph.Builder<T>.() -> Unit): NavGraph<T> =
    NavGraph.Builder<T>().apply(builder).build()
