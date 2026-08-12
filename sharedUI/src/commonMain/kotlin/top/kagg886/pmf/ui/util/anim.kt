package top.kagg886.pmf.ui.util

import androidx.compose.animation.*
import androidx.compose.ui.Alignment

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

fun <S> createContentAnim(block: AnimatedContentTransitionScope<S>.() -> Boolean): AnimatedContentTransitionScope<S>.() -> ContentTransform =
    {
        if (block()) {
            (fadeIn() + expandIn(expandFrom = Alignment.Center)) togetherWith
                    (fadeOut() + shrinkOut(shrinkTowards = Alignment.Center))
        } else {
            fadeIn() togetherWith fadeOut()
        }
    }
