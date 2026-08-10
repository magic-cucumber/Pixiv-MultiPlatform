package top.kagg886.pmf.util.nav3

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.saveable.SaveableStateHolder
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.defaultViewModelCreationExtras
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.ViewModelStoreProvider
import androidx.lifecycle.viewmodel.compose.viewModel as composeViewModel
import androidx.lifecycle.viewmodel.initializer as viewModelInitializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.savedstate.SavedStateRegistryOwner
import kotlin.reflect.KClass

@PublishedApi
internal data class NavViewModelContext(
    val currentOwner: ViewModelStoreOwner,
    val ancestorRouteOwners: List<ViewModelStoreOwner>,
)

@PublishedApi
internal val LocalNavViewModelContext: ProvidableCompositionLocal<NavViewModelContext?> =
    compositionLocalOf { null }

@PublishedApi
internal val LocalNavRouteViewModelStoreOwners: ProvidableCompositionLocal<List<ViewModelStoreOwner>> =
    compositionLocalOf { emptyList() }

/**
 * Coordinates global back-stack references with composition tokens. A scope is cleared only after
 * its final history reference and final exiting composition have both disappeared.
 */
internal class NavScopeLifecycle(
    private val provider: ViewModelStoreProvider,
    private val saveableStateHolder: SaveableStateHolder,
) {
    private data class References(
        var backStack: Int = 0,
        var composition: Int = 0,
    )

    private val references = mutableMapOf<String, References>()
    private val retainedEntryContentKeys = mutableSetOf<String>()
    private val entryCompositionReferences = mutableMapOf<String, Int>()

    fun ownerFor(key: String, savedStateRegistryOwner: SavedStateRegistryOwner): ViewModelStoreOwner =
        provider.getOrCreateOwner(key, savedStateRegistryOwner)

    fun acquire(key: String): AutoCloseable {
        val references = references.getOrPut(key) { References() }
        references.composition++
        val providerToken = provider.acquireToken(key)
        var closed = false
        return AutoCloseable {
            if (closed) return@AutoCloseable
            closed = true
            references.composition--
            check(references.composition >= 0) { "Negative composition reference count for $key" }
            clearIfUnused(key, references)
            providerToken.close()
        }
    }

    fun updateBackStackReferences(counts: Map<String, Int>, entryContentKeys: Set<String>) {
        val allKeys = references.keys.toSet() + counts.keys
        allKeys.forEach { key ->
            val references = references.getOrPut(key) { References() }
            references.backStack = counts[key] ?: 0
            check(references.backStack >= 0) { "Negative back-stack reference count for $key" }
            clearIfUnused(key, references)
        }
        val removedEntryKeys = retainedEntryContentKeys - entryContentKeys
        retainedEntryContentKeys.clear()
        retainedEntryContentKeys += entryContentKeys
        removedEntryKeys.forEach(::clearSaveableStateIfUnused)
    }

    fun acquireSaveableState(key: String): AutoCloseable {
        entryCompositionReferences[key] = (entryCompositionReferences[key] ?: 0) + 1
        var closed = false
        return AutoCloseable {
            if (closed) return@AutoCloseable
            closed = true
            val remaining = checkNotNull(entryCompositionReferences[key]) - 1
            check(remaining >= 0) { "Negative saveable-state composition count for $key" }
            if (remaining == 0) entryCompositionReferences.remove(key)
            else entryCompositionReferences[key] = remaining
            clearSaveableStateIfUnused(key)
        }
    }

    private fun clearSaveableStateIfUnused(key: String) {
        if (key in retainedEntryContentKeys || (entryCompositionReferences[key] ?: 0) != 0) return
        saveableStateHolder.removeState(key)
    }

    private fun clearIfUnused(key: String, references: References) {
        if (references.backStack != 0 || references.composition != 0) return
        provider.clearKey(key)
        this.references.remove(key)
    }

    fun clear() {
        references.values.forEach { it.backStack = 0 }
        references.toMap().forEach { (key, refs) -> clearIfUnused(key, refs) }
        val removedEntryKeys = retainedEntryContentKeys.toSet()
        retainedEntryContentKeys.clear()
        removedEntryKeys.forEach(::clearSaveableStateIfUnused)
    }

    internal fun backStackReferenceCount(key: String): Int = references[key]?.backStack ?: 0
    internal fun compositionReferenceCount(key: String): Int = references[key]?.composition ?: 0
}

private class MissingViewModelException : IllegalStateException()

private object ExistingViewModelOnlyFactory : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: KClass<T>, extras: CreationExtras): T =
        throw MissingViewModelException()
}

private fun <VM : ViewModel> existingViewModel(
    owner: ViewModelStoreOwner,
    modelClass: KClass<VM>,
    key: String?,
): VM? {
    val provider = ViewModelProvider.create(owner, ExistingViewModelOnlyFactory)
    return try {
        if (key == null) provider[modelClass] else provider[key, modelClass]
    } catch (_: MissingViewModelException) {
        null
    }
}

/**
 * Returns a ViewModel from the current navigation scope.
 *
 * The current store is searched first, followed by every ancestor route store. Supplying [factory]
 * creates a missing model in the current store. A factory-free call fails when no owner already has
 * the requested model, preventing a child UI from silently creating a replacement parent model.
 */
@Composable
public inline fun <reified VM : ViewModel> viewModel(
    key: String? = null,
    factory: ViewModelProvider.Factory? = null,
    extras: CreationExtras? = null,
): VM = navViewModel(VM::class, key, factory, extras)

/**
 * Creates a ViewModel in the current route or destination store using [initializer].
 *
 * Child screens should omit this overload when retrieving a parent route model.
 */
@Composable
public inline fun <reified VM : ViewModel> viewModel(
    key: String? = null,
    noinline initializer: CreationExtras.() -> VM,
): VM = navViewModel(
    modelClass = VM::class,
    key = key,
    factory = viewModelFactory {
        viewModelInitializer(initializer)
    },
    extras = null,
)

@PublishedApi
@Composable
internal fun <VM : ViewModel> navViewModel(
    modelClass: KClass<VM>,
    key: String?,
    factory: ViewModelProvider.Factory?,
    extras: CreationExtras?,
): VM {
    val context = checkNotNull(LocalNavViewModelContext.current) {
        "navigate3.viewModel() must be called from a route or destination registered in NavGraph."
    }
    existingViewModel(context.currentOwner, modelClass, key)?.let { return it }

    val owner = context.currentOwner
    if (factory != null) {
        return composeViewModel(
            modelClass = modelClass,
            viewModelStoreOwner = owner,
            key = key,
            factory = factory,
            extras = extras ?: owner.defaultViewModelCreationExtras,
        )
    }

    context.ancestorRouteOwners.forEach { ancestorOwner ->
        existingViewModel(ancestorOwner, modelClass, key)?.let { return it }
    }

    error(
        "No existing ${modelClass.qualifiedName} was found in the current navigation scope or " +
            "any ancestor route. Initialize the model with a factory or initializer in its " +
            "owning route before requesting it from a child UI.",
    )
}
