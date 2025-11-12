package top.kagg886.pmf.ui.util

import androidx.compose.runtime.snapshotFlow
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingSource
import androidx.paging.PagingSource.LoadParams
import androidx.paging.PagingSource.LoadResult
import androidx.paging.PagingSource.LoadResult.Page
import androidx.paging.PagingState
import androidx.paging.awaitNotLoading
import androidx.paging.compose.LazyPagingItems
import arrow.core.identity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.withContext

inline fun <T, reified E : Throwable> Result<T>.except() = onFailure { e -> if (e is E) throw e }

suspend inline fun <K : Any, V : Any, R : LoadResult<K, V>> catch(crossinline f: suspend () -> R) = withContext(Dispatchers.IO) {
    runCatching { f() }.except<R, CancellationException>().fold(::identity) { LoadResult.Error<K, V>(it) }
}

inline fun <K : Any, V : Any> flowOf(pageSize: Int, crossinline f: suspend (LoadParams<K>) -> LoadResult<K, V>) = Pager(PagingConfig(pageSize)) {
    object : PagingSource<K, V>() {
        override fun getRefreshKey(state: PagingState<K, V>) = null
        override suspend fun load(params: LoadParams<K>) = catch { f(params) }
    }
}.flow

val empty = Page(emptyList(), null, null, 0, 0)

@Suppress("UNCHECKED_CAST")
fun <Key : Any, Value : Any> empty() = empty as Page<Key, Value>

suspend inline fun <K : Any, T : Any> LoadParams<K>.next(
    fa: suspend () -> K,
    fb: suspend (K) -> K?,
    t: (K) -> List<T>,
): Page<K, T> {
    val k = key?.let { fb(it) ?: return empty() } ?: fa()
    val l = t(k).takeUnless { it.isEmpty() } ?: return empty()
    return LoadResult.Page(l, null, k)
}

suspend inline fun <T : Any> LoadParams<Int>.page(
    f: suspend (Int) -> List<T>,
): Page<Int, T> {
    val k = key ?: 1
    val r = f(k).takeUnless { it.isEmpty() } ?: return empty()
    return LoadResult.Page(r, if (k > 1) k - 1 else null, k + 1)
}

suspend fun <T : Any> LazyPagingItems<T>.awaitNextState() {
    delay(200)
    snapshotFlow { loadState }.awaitNotLoading()
}

inline fun <T, R> Flow<T>.flatMapLatestScoped(crossinline transform: suspend (scope: CoroutineScope, value: T) -> Flow<R>) = transformLatest {
    coroutineScope { emitAll(transform(this, it)) }
}
