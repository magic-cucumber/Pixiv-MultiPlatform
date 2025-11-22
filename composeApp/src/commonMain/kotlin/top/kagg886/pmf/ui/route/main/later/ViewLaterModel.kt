package top.kagg886.pmf.ui.route.main.later

import androidx.lifecycle.ViewModel
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import kotlinx.coroutines.flow.Flow
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.orbitmvi.orbit.Container
import org.orbitmvi.orbit.ContainerHost
import top.kagg886.pmf.backend.database.AppDatabase
import top.kagg886.pmf.backend.database.dao.WatchLaterItem
import top.kagg886.pmf.res.Res
import top.kagg886.pmf.res.remove_watch_later_success
import top.kagg886.pmf.ui.util.container
import top.kagg886.pmf.util.getString

/**
 * ================================================
 * Author:     886kagg
 * Created on: 2025/11/22 23:32
 * ================================================
 */

class ViewLaterModel : ContainerHost<ViewLaterState, ViewLaterSideEffect>, ViewModel(), KoinComponent {
    val database by inject<AppDatabase>()

    override val container: Container<ViewLaterState, ViewLaterSideEffect> = container(ViewLaterState.Loading) {
        val pager = Pager(
            config = PagingConfig(
                pageSize = 30,
            ),
            pagingSourceFactory = {
                database.watchLaterDAO().source()
            },
        )

        reduce {
            ViewLaterState.Success(
                pager.flow,
                0,
            )
        }
    }

    fun deleteItem(item: WatchLaterItem) = intent {
        database.watchLaterDAO().delete(item.type, item.payload)
        postSideEffect(
            ViewLaterSideEffect.Toast(getString(Res.string.remove_watch_later_success)),
        )
    }
}

sealed class ViewLaterState {
    data object Loading : ViewLaterState()
    data object Error : ViewLaterState()
    data class Success(val list: Flow<PagingData<WatchLaterItem>>, val initPage: Int = 0) : ViewLaterState()
}

sealed class ViewLaterSideEffect {
    data class Toast(val message: String) : ViewLaterSideEffect()
}
