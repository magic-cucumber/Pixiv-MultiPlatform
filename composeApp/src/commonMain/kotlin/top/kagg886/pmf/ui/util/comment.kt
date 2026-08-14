package top.kagg886.pmf.ui.util

import androidx.compose.foundation.lazy.LazyListState
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import kotlinx.coroutines.flow.*
import org.jetbrains.compose.resources.getString
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.orbitmvi.orbit.Container
import org.orbitmvi.orbit.ContainerHost
import org.orbitmvi.orbit.annotation.OrbitExperimental
import top.kagg886.pixko.module.illust.Comment
import top.kagg886.pmf.res.Res
import top.kagg886.pmf.res.ai_translate_failed
import top.kagg886.pmf.res.comment_failed
import top.kagg886.pmf.res.comment_success
import top.kagg886.pmf.translate.LanguageDetector
import top.kagg886.pmf.translate.TranslateResult
import top.kagg886.pmf.translate.TranslateScheduler
import top.kagg886.pmf.translate.isAiTranslateEnabled
import top.kagg886.pmf.translate.translationDisplayText

abstract class CommentViewModel(private val id: Long) :
    ContainerHost<CommentViewState, CommentSideEffect>,
    ViewModel(),
    KoinComponent {
    override val container: Container<CommentViewState, CommentSideEffect> =
        container(CommentViewState.Success.Generic())

    abstract fun source(id: Long): Flow<PagingData<Comment>>
    abstract fun reply(id: Long): Flow<PagingData<Comment>>
    abstract suspend fun sendComment(parentId: Long?, id: Long, text: String)
    abstract suspend fun sendComment(parentId: Long?, id: Long, stamp: Long)

    private val refreshSignal = MutableSharedFlow<Unit>()
    private val translateScheduler by inject<TranslateScheduler>()

    val data = merge(flowOf(Unit), refreshSignal).flatMapLatest { source(id) }.cachedIn(viewModelScope)
    fun refresh() = intent { refreshSignal.emit(Unit) }

    /** 切换单条评论的译文显示；失败时 toast 并回到原文。 */
    @OptIn(OrbitExperimental::class)
    fun translateComment(comment: Comment) = intent {
        runOn<CommentViewState.Success> {
            val id = comment.id
            val current = state
            if (id in current.translating) return@runOn
            if (current.translations.containsKey(id)) {
                reduce { state.removeTranslation(id) }
                return@runOn
            }
            if (!isAiTranslateEnabled()) return@runOn

            reduce { state.setTranslating(id, true) }
            val result = translateScheduler.translate(
                comment.comment,
                LanguageDetector.targetLanguageName(),
            )
            when (result) {
                is TranslateResult.Success -> reduce {
                    state.setTranslating(id, false)
                        .withTranslation(id, result.text.translationDisplayText())
                }

                is TranslateResult.Failure -> {
                    postSideEffect(CommentSideEffect.Toast(getString(Res.string.ai_translate_failed)))
                    reduce { state.setTranslating(id, false) }
                }
            }
        }
    }

    @OptIn(OrbitExperimental::class)
    fun clearReply() = intent {
        runOn<CommentViewState.Success.HasReply> {
            reduce { CommentViewState.Success.Generic(state.scrollerState) }
        }
    }

    @OptIn(OrbitExperimental::class)
    fun loadReply(comment: Comment) = intent {
        runOn<CommentViewState.Success.HasReply> {
            clearReply().join()
        }
        runOn<CommentViewState.Success.Generic> {
            reduce {
                CommentViewState.Success.HasReply(
                    state.scrollerState,
                    reply(comment.id).cachedIn(viewModelScope),
                    comment,
                )
            }
        }
    }

    @OptIn(OrbitExperimental::class)
    private inline fun ContainerHost<CommentViewState, CommentSideEffect>.sendCommentInternal(crossinline block: suspend (CommentViewState) -> Unit) = intent {
        runOn<CommentViewState.Success> {
            val result = kotlin.runCatching {
                block(state)
            }
            if (result.isSuccess) {
                postSideEffect(CommentSideEffect.Toast(getString(Res.string.comment_success)))
                refreshSignal.emit(Unit)
                return@runOn
            }
            postSideEffect(CommentSideEffect.Toast(getString(Res.string.comment_failed)))
        }
    }

    @OptIn(OrbitExperimental::class)
    fun sendComment(text: String) = sendCommentInternal { sendComment((it as? CommentViewState.Success.HasReply)?.target?.id, id, text) }

    @OptIn(OrbitExperimental::class)
    fun sendComment(stamp: Long) = sendCommentInternal { sendComment((it as? CommentViewState.Success.HasReply)?.target?.id, id, stamp) }
}

sealed interface CommentViewState {
    sealed interface Success : CommentViewState {
        val scrollerState: LazyListState
        val translations: Map<Long, String>
        val translating: Set<Long>

        data class Generic(
            override val scrollerState: LazyListState = LazyListState(),
            override val translations: Map<Long, String> = emptyMap(),
            override val translating: Set<Long> = emptySet(),
        ) : Success

        data class HasReply(
            override val scrollerState: LazyListState,
            val reply: Flow<PagingData<Comment>>,
            val target: Comment,
            override val translations: Map<Long, String> = emptyMap(),
            override val translating: Set<Long> = emptySet(),
        ) : Success
    }
}

private fun CommentViewState.Success.setTranslating(id: Long, value: Boolean): CommentViewState.Success {
    val next = if (value) translating + id else translating - id
    return when (this) {
        is CommentViewState.Success.Generic -> copy(translating = next)
        is CommentViewState.Success.HasReply -> copy(translating = next)
    }
}

private fun CommentViewState.Success.withTranslation(id: Long, text: String): CommentViewState.Success = when (this) {
    is CommentViewState.Success.Generic -> copy(translations = translations + (id to text))
    is CommentViewState.Success.HasReply -> copy(translations = translations + (id to text))
}

private fun CommentViewState.Success.removeTranslation(id: Long): CommentViewState.Success = when (this) {
    is CommentViewState.Success.Generic -> copy(translations = translations - id)
    is CommentViewState.Success.HasReply -> copy(translations = translations - id)
}

sealed class CommentSideEffect {
    data class Toast(val msg: String) : CommentSideEffect()
}
