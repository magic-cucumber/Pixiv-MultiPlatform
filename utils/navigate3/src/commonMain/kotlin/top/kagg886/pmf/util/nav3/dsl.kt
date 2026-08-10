package top.kagg886.pmf.util.nav3

import androidx.compose.runtime.Composable
import androidx.compose.ui.window.DialogProperties
import androidx.navigation3.scene.DialogSceneStrategy
import kotlin.reflect.KClass

/** A typed navigation tree whose persisted back stack contains visible leaf destinations only. */
public class NavGraph<T : SerializableNavKey> internal constructor(
    internal val nodes: List<Node<T>>,
) {
    /** Resolves [key] to a visible leaf. Route keys recursively resolve through start destinations. */
    public fun resolveLeaf(key: T): T = resolvePath(key).last()

    /** Returns the unique route-to-leaf path for [key]. */
    public fun resolvePath(key: T): List<T> = resolvePath(key, emptySet())

    private fun resolvePath(key: T, resolvingRoutes: Set<T>): List<T> {
        findDestination(key)?.let { (parents, _) -> return parents.map(Route<T>::key) + key }

        val routePath = findRoute(key)
            ?: error("No destination or route is registered for ${key::class.qualifiedName}")
        val route = routePath.last()
        check(route.key !in resolvingRoutes) {
            "Route start destinations contain a cycle at ${route.key}"
        }
        val childPath = resolveDirectChildPath(route, route.startDestination, resolvingRoutes + route.key)
        return routePath.dropLast(1).map(Route<T>::key) + childPath
    }

    private fun resolveDirectChildPath(
        route: Route<T>,
        key: T,
        resolvingRoutes: Set<T>,
    ): List<T> {
        val child = route.children.firstOrNull { it.matches(key) }
            ?: error("Start destination $key is not a direct child of route ${route.key}")
        return when (child) {
            is Destination<T> -> listOf(key)
            is Route<T> -> {
                check(child.key !in resolvingRoutes) {
                    "Route start destinations contain a cycle at ${child.key}"
                }
                listOf(child.key) + resolveDirectChildPath(
                    child,
                    child.startDestination,
                    resolvingRoutes + child.key,
                )
            }
        }
    }

    internal fun destinationFor(key: T): Destination<T> = findDestination(key)?.second
        ?: error("No leaf destination is registered for ${key::class.qualifiedName}")

    internal fun nodeFor(path: List<T>, depth: Int): Node<T> {
        require(depth in path.indices) { "Invalid path depth $depth for $path" }
        var children = nodes
        var node: Node<T>? = null
        for (index in 0..depth) {
            val pathKey = path[index]
            node = children.firstOrNull { it.matches(pathKey) }
                ?: error("No direct child $pathKey exists at depth $index in $path")
            children = (node as? Route<T>)?.children.orEmpty()
        }
        return checkNotNull(node)
    }

    private fun findDestination(key: T): Pair<List<Route<T>>, Destination<T>>? =
        findDestination(nodes, key, emptyList())

    private fun findDestination(
        children: List<Node<T>>,
        key: T,
        parents: List<Route<T>>,
    ): Pair<List<Route<T>>, Destination<T>>? {
        children.forEach { node ->
            when (node) {
                is Destination<T> -> if (node.matches(key)) return parents to node
                is Route<T> -> findDestination(node.children, key, parents + node)?.let { return it }
            }
        }
        return null
    }

    private fun findRoute(key: T): List<Route<T>>? = findRoute(nodes, key, emptyList())

    private fun findRoute(
        children: List<Node<T>>,
        key: T,
        parents: List<Route<T>>,
    ): List<Route<T>>? {
        children.forEach { node ->
            if (node is Route<T>) {
                if (node.key == key) return parents + node
                findRoute(node.children, key, parents + node)?.let { return it }
            }
        }
        return null
    }

    @PublishedApi
    internal sealed interface Node<T : SerializableNavKey> {
        fun matches(key: T): Boolean
    }

    @PublishedApi
    internal class Destination<T : SerializableNavKey> @PublishedApi internal constructor(
        val type: KClass<out T>,
        val metadata: Map<String, Any>,
        val content: @Composable (T) -> Unit,
    ) : Node<T> {
        override fun matches(key: T): Boolean = type.isInstance(key)
    }

    @PublishedApi
    internal class Route<T : SerializableNavKey> @PublishedApi internal constructor(
        val key: T,
        val startDestination: T,
        val config: NavConfigOverride<T>,
        val content: @Composable (@Composable () -> Unit) -> Unit,
        val children: List<Node<T>>,
    ) : Node<T> {
        override fun matches(key: T): Boolean = this.key == key
    }

    @Nav3Dsl
    public class Builder<T : SerializableNavKey> internal constructor() {
        @PublishedApi
        internal val nodes: MutableList<Node<T>> = mutableListOf()

        public inline fun <reified K : T> destination(
            metadata: Map<String, Any> = emptyMap(),
            noinline content: @Composable (K) -> Unit,
        ): Unit = addDestination(K::class, metadata, content)

        public fun <K : T> destination(
            type: KClass<K>,
            metadata: Map<String, Any> = emptyMap(),
            content: @Composable (K) -> Unit,
        ): Unit = addDestination(type, metadata, content)

        @PublishedApi
        internal fun <K : T> addDestination(
            type: KClass<K>,
            metadata: Map<String, Any>,
            content: @Composable (K) -> Unit,
        ) {
            nodes += Destination(type, metadata) { key ->
                @Suppress("UNCHECKED_CAST")
                content(key as K)
            }
        }

        public inline fun <reified K : T> dialog(
            dialogProperties: DialogProperties = DialogProperties(),
            noinline content: @Composable (K) -> Unit,
        ): Unit = destination(
            metadata = DialogSceneStrategy.dialog(dialogProperties),
            content = content,
        )

        public inline fun <reified K : T> route(
            parent: K,
            startDestination: T,
            config: NavConfigOverride<T> = NavConfigOverride(),
            noinline content: @Composable (@Composable () -> Unit) -> Unit,
            builder: RouteBuilder<T>.() -> Unit,
        ) {
            val childBuilder = RouteBuilder<T>()
            childBuilder.builder()
            nodes += Route(parent, startDestination, config, content, childBuilder.nodes.toList())
        }

        internal fun build(): NavGraph<T> = NavGraph(nodes.toList()).also(NavGraph<T>::validate)
    }

    @Nav3Dsl
    public class RouteBuilder<T : SerializableNavKey> @PublishedApi internal constructor() {
        @PublishedApi
        internal val nodes: MutableList<Node<T>> = mutableListOf()

        public inline fun <reified K : T> destination(
            metadata: Map<String, Any> = emptyMap(),
            noinline content: @Composable (K) -> Unit,
        ) {
            nodes += Destination(K::class, metadata) { key ->
                @Suppress("UNCHECKED_CAST")
                content(key as K)
            }
        }

        public inline fun <reified K : T> dialog(
            dialogProperties: DialogProperties = DialogProperties(),
            noinline content: @Composable (K) -> Unit,
        ): Unit = destination(
            metadata = DialogSceneStrategy.dialog(dialogProperties),
            content = content,
        )

        public inline fun <reified K : T> route(
            parent: K,
            startDestination: T,
            config: NavConfigOverride<T> = NavConfigOverride(),
            noinline content: @Composable (@Composable () -> Unit) -> Unit,
            builder: RouteBuilder<T>.() -> Unit,
        ) {
            val childBuilder = RouteBuilder<T>()
            childBuilder.builder()
            nodes += Route(parent, startDestination, config, content, childBuilder.nodes.toList())
        }
    }

    private fun validate() {
        val destinationTypes = mutableSetOf<KClass<out T>>()
        val routeKeys = mutableSetOf<T>()

        fun validateChildren(children: List<Node<T>>) {
            children.forEach { node ->
                when (node) {
                    is Destination<T> -> check(destinationTypes.add(node.type)) {
                        "Destination ${node.type.qualifiedName} is already registered"
                    }
                    is Route<T> -> {
                        check(routeKeys.add(node.key)) { "Route ${node.key} is already registered" }
                        check(node.children.isNotEmpty()) { "Route ${node.key} must contain at least one child" }
                        check(node.children.count { it.matches(node.startDestination) } == 1) {
                            "Start destination ${node.startDestination} must be exactly one direct child of route ${node.key}"
                        }
                        validateChildren(node.children)
                    }
                }
            }
        }

        validateChildren(nodes)
        routeKeys.forEach { routeKey ->
            check(destinationTypes.none { it.isInstance(routeKey) }) {
                "Route key $routeKey also matches a registered destination type"
            }
            resolvePath(routeKey)
        }
    }
}

public fun <T : SerializableNavKey> createNavGraph(
    builder: NavGraph.Builder<T>.() -> Unit,
): NavGraph<T> = NavGraph.Builder<T>().apply(builder).build()
