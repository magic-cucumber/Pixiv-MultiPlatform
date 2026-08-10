package top.kagg886.pmf.util.nav3

import androidx.compose.runtime.snapshots.Snapshot
import androidx.navigation3.runtime.NavBackStack

/** Owns the leaf-only back stack and commits all navigation mutations atomically. */
public class NavController<T : SerializableNavKey> internal constructor(
    public val graph: NavGraph<T>,
    private val history: NavBackStack<T>,
) {
    private var updating: Boolean = false
    private var scopeLifecycle: NavScopeLifecycle? = null

    public constructor(graph: NavGraph<T>, startDestination: T) : this(
        graph = graph,
        history = NavBackStack(graph.resolveLeaf(startDestination)),
    )

    /** Every retained visible page, in back-navigation order. Routes are never stored here. */
    public val backStack: List<T> get() = history

    /** The unique graph path from the root node to the currently visible leaf. */
    public val currentPath: List<T> get() = graph.resolvePath(history.last())

    /** Appends one visible leaf record, even when the key or content key already exists. */
    public fun navigate(key: T): Unit {
        update { push(key) }
    }

    /** Removes exactly one top record. The final record cannot be removed by this convenience API. */
    public fun popBackStack(): Boolean {
        if (history.size <= 1) return false
        return update { pop() }
    }

    /**
     * Applies top-only mutations to a copy and publishes the validated result in one snapshot.
     * A failed block or invalid final state leaves the original stack untouched.
     */
    public fun update(block: NavUpdateScope<T>.() -> Unit): Boolean {
        check(!updating) { "Nested NavController.update calls are not supported" }
        updating = true
        try {
            val candidate = history.toMutableList()
            NavUpdateScope(graph, candidate).block()
            require(candidate.isNotEmpty()) { "A navigation update cannot commit an empty back stack" }
            candidate.forEach { key ->
                require(graph.resolveLeaf(key) == key) { "$key is not a visible leaf destination" }
            }
            if (candidate == history) return false

            Snapshot.withMutableSnapshot {
                history.clear()
                history.addAll(candidate)
            }
            updateScopeReferences()
            return true
        } finally {
            updating = false
        }
    }

    internal val displayFrame: NavDisplayFrame<T> get() = graph.project(history)

    internal fun attachScopeLifecycle(lifecycle: NavScopeLifecycle) {
        scopeLifecycle = lifecycle
        updateScopeReferences()
    }

    internal fun detachScopeLifecycle(lifecycle: NavScopeLifecycle) {
        if (scopeLifecycle === lifecycle) scopeLifecycle = null
    }

    private fun updateScopeReferences() {
        val frame = displayFrame
        scopeLifecycle?.updateBackStackReferences(
            counts = frame.contentKeyReferenceCounts(),
            entryContentKeys = frame.entryContentKeys(),
        )
    }

    public fun clear(): Unit {
        scopeLifecycle?.clear()
        scopeLifecycle = null
    }
}

@Nav3Dsl
public class NavUpdateScope<T : SerializableNavKey> internal constructor(
    private val graph: NavGraph<T>,
    private val candidate: MutableList<T>,
) {
    public val top: T get() = candidate.last()
    public val size: Int get() = candidate.size

    /** Resolves routes through their start destinations and appends the resulting visible leaf. */
    public fun push(key: T) {
        candidate += graph.resolveLeaf(key)
    }

    /** Removes and returns the top candidate record, including a temporary final record. */
    public fun pop(): T? = candidate.removeLastOrNull()

    public fun replaceTop(key: T) {
        pop()
        push(key)
    }
}
