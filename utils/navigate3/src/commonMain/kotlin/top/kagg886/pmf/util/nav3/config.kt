package top.kagg886.pmf.util.nav3

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.SizeTransform
import androidx.navigation3.scene.DialogSceneStrategy
import androidx.navigation3.scene.Scene
import androidx.navigation3.scene.SceneDecoratorStrategy
import androidx.navigation3.scene.SceneStrategy
import androidx.navigation3.ui.defaultPopTransitionSpec
import androidx.navigation3.ui.defaultPredictivePopTransitionSpec
import androidx.navigation3.ui.defaultTransitionSpec
import androidx.navigationevent.NavigationEvent
import kotlinx.serialization.modules.SerializersModule

/** Root navigation configuration. The generated application serializers module is mandatory. */
public class NavConfig<T : SerializableNavKey>(
    public val serializersModule: SerializersModule,
    public val sceneStrategies: List<SceneStrategy<T>> = listOf(DialogSceneStrategy()),
    public val sceneDecoratorStrategies: List<SceneDecoratorStrategy<T>> = emptyList(),
    public val sizeTransform: SizeTransform? = null,
    public val transitionSpec: AnimatedContentTransitionScope<Scene<T>>.() -> ContentTransform =
        defaultTransitionSpec(),
    public val popTransitionSpec: AnimatedContentTransitionScope<Scene<T>>.() -> ContentTransform =
        defaultPopTransitionSpec(),
    public val predictivePopTransitionSpec:
        AnimatedContentTransitionScope<Scene<T>>.(@NavigationEvent.SwipeEdge Int) -> ContentTransform =
        defaultPredictivePopTransitionSpec(),
)

/** Display-only overrides inherited by descendants. Serialization is always configured at root. */
public class NavConfigOverride<T : SerializableNavKey>(
    public val sceneStrategies: List<SceneStrategy<T>>? = null,
    public val sceneDecoratorStrategies: List<SceneDecoratorStrategy<T>>? = null,
    public val sizeTransform: SizeTransform? = null,
    public val transitionSpec: (AnimatedContentTransitionScope<Scene<T>>.() -> ContentTransform)? = null,
    public val popTransitionSpec: (AnimatedContentTransitionScope<Scene<T>>.() -> ContentTransform)? = null,
    public val predictivePopTransitionSpec:
        (AnimatedContentTransitionScope<Scene<T>>.(@NavigationEvent.SwipeEdge Int) -> ContentTransform)? = null,
)

internal fun <T : SerializableNavKey> NavConfig<T>.merge(
    override: NavConfigOverride<T>,
): NavConfig<T> = NavConfig(
    serializersModule = serializersModule,
    sceneStrategies = override.sceneStrategies ?: sceneStrategies,
    sceneDecoratorStrategies = override.sceneDecoratorStrategies ?: sceneDecoratorStrategies,
    sizeTransform = override.sizeTransform ?: sizeTransform,
    transitionSpec = override.transitionSpec ?: transitionSpec,
    popTransitionSpec = override.popTransitionSpec ?: popTransitionSpec,
    predictivePopTransitionSpec = override.predictivePopTransitionSpec ?: predictivePopTransitionSpec,
)
