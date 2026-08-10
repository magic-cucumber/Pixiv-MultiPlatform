package top.kagg886.pmf.ui.util

import androidx.compose.animation.*

/**
 * ================================================
 * Author:     iveou
 * Created on: 2026/8/10 12:53
 * ================================================
 */

fun <S> createMenuButtonAnim(block: AnimatedContentTransitionScope<S>.() -> Boolean): AnimatedContentTransitionScope<S>.() -> ContentTransform =
    {
        if (block()) {
            slideInVertically { height -> height } + fadeIn() togetherWith
                    slideOutVertically { height -> -height } + fadeOut()
        } else {
            slideInVertically { height -> -height } + fadeIn() togetherWith
                    slideOutVertically { height -> height } + fadeOut()
        }
    }
