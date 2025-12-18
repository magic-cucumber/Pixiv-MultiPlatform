package top.kagg886.pmf.ui.route.main.search.v2

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.orbitmvi.orbit.Container
import org.orbitmvi.orbit.ContainerHost
import top.kagg886.pixko.Tag
import top.kagg886.pixko.module.illust.Illust
import top.kagg886.pixko.module.illust.getIllustDetail
import top.kagg886.pixko.module.novel.Novel
import top.kagg886.pixko.module.novel.SeriesInfo
import top.kagg886.pixko.module.novel.getNovelDetail
import top.kagg886.pixko.module.novel.getNovelSeries
import top.kagg886.pixko.module.search.SearchSort
import top.kagg886.pixko.module.search.SearchTarget
import top.kagg886.pixko.module.search.searchTag
import top.kagg886.pixko.module.trending.TrendingTags
import top.kagg886.pixko.module.trending.getRecommendTags
import top.kagg886.pixko.module.user.UserInfo
import top.kagg886.pixko.module.user.getUserInfo
import top.kagg886.pmf.backend.database.AppDatabase
import top.kagg886.pmf.backend.database.dao.BlackListItem
import top.kagg886.pmf.backend.database.dao.BlackListType
import top.kagg886.pmf.res.*
import top.kagg886.pmf.ui.route.main.search.v2.components.TagPropertiesState
import top.kagg886.pmf.ui.util.container
import top.kagg886.pmf.util.getString
import top.kagg886.pmf.util.logger

class SearchPanelViewModel(
    initialSort: SearchSort,
    initialTarget: SearchTarget,
    initialKeyword: List<String>,
    initialText: String,
) : ViewModel(), KoinComponent, ContainerHost<SearchPanelViewState, SearchPanelSideEffect> {

    private val database by inject<AppDatabase>()
    private val client = top.kagg886.pmf.backend.pixiv.PixivConfig.newAccountFromConfig()

    override val container: Container<SearchPanelViewState, SearchPanelSideEffect> = container(
        SearchPanelViewState(
            panelState = SearchPanelState.SettingProperties,
            keyword = initialKeyword,
            text = initialText,
            sort = initialSort,
            target = initialTarget,
            hotTag = TagPropertiesState.Loading,
        ),
    ) {
        refreshHotTag()
    }

    fun updateKeywords(keywords: List<String>) = intent {
        reduce {
            state.copy(keyword = keywords)
        }
    }

    fun updateText(newText: String) = intent {
        reduce {
            state.copy(text = newText)
        }
    }

    fun updateSort(newSort: SearchSort) = intent {
        reduce {
            state.copy(sort = newSort)
        }
    }

    fun updateTarget(newTarget: SearchTarget) = intent {
        reduce {
            state.copy(target = newTarget)
        }
    }

    fun refreshHotTag() = intent {
        reduce {
            state.copy(hotTag = TagPropertiesState.Loading)
        }

        try {
            val tags = client.getRecommendTags().map {
                viewModelScope.async {
                    if (!blackingListDao.matchRules(BlackListType.TAG_NAME, it.tag.name)) {
                        it
                    } else {
                        null.apply {
                            logger.d("successfully filter tag ${it.tag.name}, because it is blacklisted")
                        }
                    }
                }
            }.awaitAll().filterNotNull()
            reduce {
                state.copy(hotTag = TagPropertiesState.Loaded(tags))
            }
        } catch (e: Exception) {
            reduce {
                state.copy(hotTag = TagPropertiesState.Failed(e.toString()))
            }
        }
    }

    fun searchTagOrExactSearch(query: String) = intent {
        with(query.toLongOrNull()) {
            if (this != null && state.keyword.isEmpty()) {
                reduce {
                    state.copy(panelState = SearchPanelState.Searching)
                }

                data class Result(
                    val illust: Illust?,
                    val novel: Novel?,
                    val author: UserInfo?,
                    val series: SeriesInfo?,
                )

                val (illust, novel, author, series) = coroutineScope {
                    val a1 = async {
                        try {
                            client.getIllustDetail(this@with)
                        } catch (e: Exception) {
                            null
                        }
                    }

                    val a2 = async {
                        try {
                            client.getNovelDetail(this@with)
                        } catch (e: Exception) {
                            null
                        }
                    }

                    val a3 = async {
                        try {
                            client.getUserInfo(this@with.toInt())
                        } catch (e: Exception) {
                            null
                        }
                    }

                    val a4 = async {
                        try {
                            client.getNovelSeries(this@with.toInt())
                        } catch (e: Exception) {
                            null
                        }
                    }

                    Result(a1.await(), a2.await(), a3.await(), a4.await())
                }

                reduce {
                    state.copy(
                        panelState = SearchPanelState.RedirectToPage(
                            illust = illust,
                            novel = novel,
                            user = author,
                            series = series,
                        ),
                    )
                }
                return@intent
            }
        }

        reduce {
            state.copy(panelState = SearchPanelState.Searching)
        }

        try {
            val result = client.searchTag(query).map {
                viewModelScope.async {
                    if (database.blacklistDAO().matchRules(BlackListType.TAG_NAME, it.name)) null else it
                }
            }.awaitAll().filterNotNull()

            reduce {
                state.copy(panelState = SearchPanelState.SelectTag(result))
            }
        } catch (e: Exception) {
            val unknown = getString(Res.string.unknown)
            reduce {
                state.copy(panelState = SearchPanelState.SearchingFailed(e.message ?: unknown))
            }
        }
    }

    fun selectTag(tag: Tag) = intent {
        val updatedKeywords = state.keyword + tag.name

        reduce {
            val newTarget = if (state.target != SearchTarget.PARTIAL_MATCH_FOR_TAGS &&
                state.target != SearchTarget.EXACT_MATCH_FOR_TAGS
            ) {
                SearchTarget.PARTIAL_MATCH_FOR_TAGS
            } else {
                state.target
            }

            // Only update the state to SettingProperties if it's not already in that state
            val newPanelState = if (state.panelState !is SearchPanelState.SettingProperties) {
                SearchPanelState.SettingProperties
            } else {
                state.panelState
            }

            state.copy(
                keyword = updatedKeywords,
                text = "",
                target = newTarget,
                panelState = newPanelState,
            )
        }

        // If we just switched to settings state, refresh the hot tags
        if (state.panelState is SearchPanelState.SettingProperties &&
            state.panelState != SearchPanelState.SettingProperties
        ) {
            refreshHotTag()
        }
    }

    val blackingListDao = database.blacklistDAO()

    fun blockingTag(data: TrendingTags) = intent {
        val state = state
        if (state.hotTag !is TagPropertiesState.Loaded) {
            return@intent
        }
        blackingListDao.insert(BlackListItem(data.tag.name))
        postSideEffect(SearchPanelSideEffect.Toast(getString(Res.string.filter_add_tags_tips)))
        reduce {
            this.state.copy(hotTag = state.hotTag.copy(tags = state.hotTag.tags.filter { it != data }))
        }
    }

    fun navigateToSearchResult(stack: NavBackStack<NavKey>) = intent {
        val tagInBlackList = state.keyword.map {
            viewModelScope.async {
                database.blacklistDAO().matchRules(BlackListType.TAG_NAME, it)
            }
        }.awaitAll()

        if (tagInBlackList.any { it }) {
            postSideEffect(
                SearchPanelSideEffect.Toast(
                    getString(
                        Res.string.blocking_because_black,
                        getString(Res.string.tags),
                    ),
                ),
            )
            return@intent
        }

        stack += SearchResultRoute(
            state.keyword,
            state.sort,
            state.target,
        )
    }
}

data class SearchPanelViewState(
    val panelState: SearchPanelState,
    val keyword: List<String>,
    val text: String,
    val sort: SearchSort,
    val target: SearchTarget,
    val hotTag: TagPropertiesState,
)

sealed interface SearchPanelSideEffect {
    data class Toast(val message: String) : SearchPanelSideEffect
}

sealed interface SearchPanelState {
    data object SettingProperties : SearchPanelState
    data object Searching : SearchPanelState
    data class SearchingFailed(val msg: String) : SearchPanelState
    data class SelectTag(val tags: List<Tag>) : SearchPanelState
    data class RedirectToPage(
        val illust: Illust?,
        val novel: Novel?,
        val user: UserInfo?,
        val series: SeriesInfo?,
    ) : SearchPanelState
}
