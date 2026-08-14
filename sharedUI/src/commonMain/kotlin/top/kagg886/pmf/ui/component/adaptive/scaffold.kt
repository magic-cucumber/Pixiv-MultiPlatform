package top.kagg886.pmf.ui.component.adaptive

import androidx.compose.foundation.interaction.Interaction
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.*
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.adaptive.WindowAdaptiveInfo
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.runtime.*
import androidx.compose.runtime.collection.MutableVector
import androidx.compose.runtime.collection.mutableVectorOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.PreviewScreenSizes
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastFirst
import androidx.window.core.layout.WindowSizeClass
import top.kagg886.pmf.ui.component.adaptive.NavigationSuiteType.Companion.NavigationBar
import top.kagg886.pmf.ui.component.adaptive.NavigationSuiteType.Companion.NavigationRail
import top.kagg886.pmf.ui.util.rememberCurrentNavigationSuiteType
import kotlin.jvm.JvmInline

/**
 * The Navigation Suite Scaffold wraps the provided content and places the adequate provided
 * navigation component on the screen according to the current [NavigationSuiteType].
 *
 * Example default usage:
 * @sample androidx.compose.material3.adaptive.navigationsuite.samples.NavigationSuiteScaffoldSample
 * Example custom configuration usage:
 * @sample androidx.compose.material3.adaptive.navigationsuite.samples.NavigationSuiteScaffoldCustomConfigSample
 *
 * @param navigationSuiteItems the navigation items to be displayed
 * @param modifier the [Modifier] to be applied to the navigation suite scaffold
 * @param layoutType the current [NavigationSuiteType]. Defaults to
 * [NavigationSuiteScaffoldDefaults.calculateFromAdaptiveInfo]
 * @param navigationSuiteColors [NavigationSuiteColors] that will be used to determine the container
 * (background) color of the navigation component and the preferred color for content inside the
 * navigation component
 * @param containerColor the color used for the background of the navigation suite scaffold,
 * including the passed [content] composable. Use [Color.Transparent] to have no color
 * @param contentColor the preferred color to be used for typography and iconography within the
 * passed in [content] lambda inside the navigation suite scaffold.
 * @param content the content of your screen
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NavigationSuiteScaffold(
    navigationSuiteItems: NavigationSuiteScope.() -> Unit,
    navigationSuiteModifier: Modifier = Modifier,
    enableNavigation: Boolean = true,
    modifier: Modifier = Modifier,
    layoutType: NavigationSuiteType =
        NavigationSuiteScaffoldDefaults.calculateFromAdaptiveInfo(WindowAdaptiveInfoDefault),
    navigationSuiteColors: NavigationSuiteColors = NavigationSuiteDefaults.colors(),
    containerColor: Color = NavigationSuiteScaffoldDefaults.containerColor,
    contentColor: Color = NavigationSuiteScaffoldDefaults.contentColor,
    content: @Composable () -> Unit = {},
) {
    Surface(modifier = modifier, color = containerColor, contentColor = contentColor) {
        NavigationSuiteScaffoldLayout(
            navigationSuite = {
                NavigationSuite(
                    enable = enableNavigation,
                    layoutType = layoutType,
                    colors = navigationSuiteColors,
                    content = navigationSuiteItems,
                    modifier = navigationSuiteModifier
                )
            },
            layoutType = layoutType,
            content = {
                Box(
                    Modifier.consumeWindowInsets(
                        when (layoutType) {
                            NavigationBar ->
                                NavigationBarDefaults.windowInsets

                            NavigationRail ->
                                NavigationRailDefaults.windowInsets

                            NavigationSuiteType.NavigationDrawer ->
                                DrawerDefaults.windowInsets

                            else -> WindowInsets(0, 0, 0, 0)
                        }
                    )
                ) {
                    val scope by rememberStateOfItems(navigationSuiteItems)
                    val contentTopPadding = when (layoutType) {
                        NavigationRail -> NavigationRailDefaults.windowInsets
                            .asPaddingValues()
                            .calculateTopPadding()
                            .plus(10.dp)

                        NavigationSuiteType.NavigationDrawer -> DrawerDefaults.windowInsets
                            .asPaddingValues()
                            .calculateTopPadding()

                        else -> 0.dp
                    }

                    when (layoutType) {
                        NavigationBar -> {
                            val title by scope.title
                            val menu by scope.menu
                            val fab by scope.fabIcon
                            val fabClick by scope.fabOnClick
                            val fabModifier by scope.fabModifier
                            val back by scope.back
                            Scaffold(
                                floatingActionButton = {
                                    if (fab != null) {
                                        FloatingActionButton(
                                            modifier = fabModifier,
                                            onClick = {
                                                fabClick?.let { it() }
                                            },
                                        ) {
                                            fab?.let { it() }
                                        }
                                    }
                                },
                                topBar = {
                                    if (title != null || menu != null || back != null) {
                                        TopAppBar(
                                            title = {
                                                ProvideTextStyle(
                                                    MaterialTheme.typography.headlineSmall.copy(
                                                        fontWeight = FontWeight.Bold,
                                                    )
                                                ) {
                                                    title?.let { it() }
                                                }
                                            },
                                            navigationIcon = {
                                                back?.let { it() }
                                            },
                                            actions = {
                                                menu?.let { it() }
                                            }
                                        )
                                    }
                                }
                            ) { padding ->
                                Box(Modifier.fillMaxSize().padding(padding)) {
                                    content()
                                }
                            }
                        }

                        NavigationRail -> {
                            val title by scope.title
                            val back by scope.back
                            Scaffold(
                                modifier = Modifier.padding(top = contentTopPadding),
                                topBar = {
                                    if (title != null) {
                                        TopAppBar(
                                            title = {
                                                ProvideTextStyle(
                                                    MaterialTheme.typography.headlineSmall.copy(
                                                        fontWeight = FontWeight.Bold,
                                                    )
                                                ) {
                                                    title?.let { it() }
                                                }
                                            },
                                            navigationIcon = {
                                                back?.let { it() }
                                            },
                                        )
                                    }
                                }
                            ) { padding ->
                                Box(Modifier.fillMaxSize().padding(padding)) {
                                    content()
                                }
                            }
                        }

                        NavigationSuiteType.NavigationDrawer -> {
                            Box(Modifier.fillMaxSize().padding(top = contentTopPadding)) {
                                content()
                            }
                        }
                    }
                }
            }
        )
    }
}

/**
 * Layout for a [NavigationSuiteScaffold]'s content. This function wraps the [content] and places
 * the [navigationSuite] component according to the given [layoutType].
 *
 * The usage of this function is recommended when you need some customization that is not viable via
 * the use of [NavigationSuiteScaffold].
 * Example usage:
 * @sample androidx.compose.material3.adaptive.navigationsuite.samples.NavigationSuiteScaffoldCustomNavigationRail
 *
 * @param navigationSuite the navigation component to be displayed, typically [NavigationSuite]
 * @param layoutType the current [NavigationSuiteType]. Defaults to
 * [NavigationSuiteScaffoldDefaults.calculateFromAdaptiveInfo]
 * @param content the content of your screen
 */
@Composable
fun NavigationSuiteScaffoldLayout(
    navigationSuite: @Composable () -> Unit,
    layoutType: NavigationSuiteType =
        NavigationSuiteScaffoldDefaults.calculateFromAdaptiveInfo(WindowAdaptiveInfoDefault),
    content: @Composable () -> Unit = {}
) {
    Layout({
        // Wrap the navigation suite and content composables each in a Box to not propagate the
        // parent's (Surface) min constraints to its children (see b/312664933).
        Box(Modifier.layoutId(NavigationSuiteLayoutIdTag)) {
            navigationSuite()
        }
        Box(Modifier.layoutId(ContentLayoutIdTag)) {
            content()
        }
    }
    ) { measurables, constraints ->
        val looseConstraints = constraints.copy(minWidth = 0, minHeight = 0)
        // Find the navigation suite composable through it's layoutId tag
        val navigationPlaceable =
            measurables.fastFirst { it.layoutId == NavigationSuiteLayoutIdTag }
                .measure(looseConstraints)
        val isNavigationBar = layoutType == NavigationBar
        val layoutHeight = constraints.maxHeight
        val layoutWidth = constraints.maxWidth
        // Find the content composable through it's layoutId tag
        val contentPlaceable =
            measurables.fastFirst { it.layoutId == ContentLayoutIdTag }.measure(
                if (isNavigationBar) {
                    constraints.copy(
                        minHeight = layoutHeight - navigationPlaceable.height,
                        maxHeight = layoutHeight - navigationPlaceable.height
                    )
                } else {
                    constraints.copy(
                        minWidth = layoutWidth - navigationPlaceable.width,
                        maxWidth = layoutWidth - navigationPlaceable.width
                    )
                }
            )

        layout(layoutWidth, layoutHeight) {
            if (isNavigationBar) {
                // Place content above the navigation component.
                contentPlaceable.placeRelative(0, 0)
                // Place the navigation component at the bottom of the screen.
                navigationPlaceable.placeRelative(
                    0,
                    layoutHeight - (navigationPlaceable.height)
                )
            } else {
                // Place the navigation component at the start of the screen.
                navigationPlaceable.placeRelative(0, 0)
                // Place content to the side of the navigation component.
                contentPlaceable.placeRelative((navigationPlaceable.width), 0)
            }
        }
    }
}

/**
 * The default Material navigation component according to the current [NavigationSuiteType] to be
 * used with the [NavigationSuiteScaffold].
 *
 * For specifics about each navigation component, see [NavigationBar], [NavigationRail], and
 * [PermanentDrawerSheet].
 *
 * @param modifier the [Modifier] to be applied to the navigation component
 * @param layoutType the current [NavigationSuiteType] of the [NavigationSuiteScaffold]. Defaults to
 * [NavigationSuiteScaffoldDefaults.calculateFromAdaptiveInfo]
 * @param colors [NavigationSuiteColors] that will be used to determine the container (background)
 * color of the navigation component and the preferred color for content inside the navigation
 * component
 * @param content the content inside the current navigation component, typically
 * [NavigationSuiteScope.item]s
 */
@Composable
fun NavigationSuite(
    enable: Boolean = true,
    modifier: Modifier = Modifier,
    layoutType: NavigationSuiteType =
        NavigationSuiteScaffoldDefaults.calculateFromAdaptiveInfo(WindowAdaptiveInfoDefault),
    colors: NavigationSuiteColors = NavigationSuiteDefaults.colors(),
    content: NavigationSuiteScope.() -> Unit
) {
    val scope by rememberStateOfItems(content)
    // Define defaultItemColors here since we can't set NavigationSuiteDefaults.itemColors() as a
    // default for the colors param of the NavigationSuiteScope.item non-composable function.
    val defaultItemColors = NavigationSuiteDefaults.itemColors()

    when (layoutType) {
        NavigationBar -> {
            NavigationBar(
                modifier = modifier,
                containerColor = colors.navigationBarContainerColor,
                contentColor = colors.navigationBarContentColor
            ) {
                scope.itemList.forEach {
                    NavigationBarItem(
                        modifier = it.modifier,
                        selected = it.selected,
                        onClick = it.onClick,
                        icon = { NavigationItemIcon(icon = it.icon, badge = it.badge) },
                        enabled = it.enabled && enable,
                        label = it.label,
                        alwaysShowLabel = it.alwaysShowLabel,
                        colors = it.colors?.navigationBarItemColors
                            ?: defaultItemColors.navigationBarItemColors,
                        interactionSource = it.interactionSource
                    )
                }
            }
        }

        NavigationRail -> {
            NavigationRail(
                modifier = modifier,
                containerColor = colors.navigationRailContainerColor,
                contentColor = colors.navigationRailContentColor
            ) {
                val menu by scope.menu
                val fab by scope.fabIcon
                val fabClick by scope.fabOnClick
                val fabModifier by scope.fabModifier

                Spacer(Modifier.height(16.dp))
                Box(Modifier.size(40.dp)) {
                    menu?.let {
                        it()
                    }
                }
                Spacer(Modifier.height(24.dp))

                Box(
                    Modifier.defaultMinSize(
                        minWidth = 56.dp,
                        minHeight = 56.dp,
                    )
                ) {
                    fab?.let {
                        FloatingActionButton(
                            onClick = fabClick ?: {},
                            modifier = fabModifier
                        ) {
                            it()
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))

                scope.itemList.forEach {
                    NavigationRailItem(
                        modifier = it.modifier,
                        selected = it.selected,
                        onClick = it.onClick,
                        icon = { NavigationItemIcon(icon = it.icon, badge = it.badge) },
                        enabled = it.enabled && enable,
                        label = it.label,
                        alwaysShowLabel = it.alwaysShowLabel,
                        colors = it.colors?.navigationRailItemColors
                            ?: defaultItemColors.navigationRailItemColors,
                        interactionSource = it.interactionSource
                    )
                }
            }
        }

        NavigationSuiteType.NavigationDrawer -> {
            PermanentDrawerSheet(
                modifier = modifier,
                drawerContainerColor = colors.navigationDrawerContainerColor,
                drawerContentColor = colors.navigationDrawerContentColor
            ) {
                val title by scope.title
                val menu by scope.menu
                val fab by scope.fabIcon
                val fabText by scope.fabText
                val fabClick by scope.fabOnClick
                val fabModifier by scope.fabModifier
                val back by scope.back

                Box(
                    Modifier
                        .semantics { role = Role.Tab }
                        .heightIn(min = 56.dp)
                        .fillMaxWidth(),
                ) {
                    Box(Modifier.align(Alignment.CenterStart)) {
                        ProvideTextStyle(
                            MaterialTheme.typography.headlineSmall.copy(
                                fontWeight = FontWeight.Bold,
                            )
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Spacer(Modifier.width(8.dp))
                                Box(Modifier.sizeIn(48.dp), contentAlignment = Alignment.Center) {
                                    back?.let { it() }
                                }
                                Spacer(Modifier.width(8.dp))
                                title?.let { it() }
                            }
                        }
                    }
                    Box(Modifier.align(Alignment.CenterEnd)) {
                        menu?.let {
                            it()
                        }
                    }
                }
                Box(
                    Modifier
                        .semantics { role = Role.Button }
                        .heightIn(min = 56.dp)
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                ) {
                    if (fab != null || fabText != null) {
                        ExtendedFloatingActionButton(
                            onClick = fabClick ?: {},
                            text = {
                                fabText?.let {
                                    it()
                                }
                            },
                            icon = {
                                fab?.let {
                                    it()
                                }
                            },
                            modifier = fabModifier.fillMaxWidth().sizeIn(minWidth = 80.dp)
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))

                scope.itemList.forEach {
                    NavigationDrawerItem(
                        modifier = it.modifier.padding(horizontal = 16.dp),
                        selected = it.selected,
                        onClick = {
                            if (!it.enabled) return@NavigationDrawerItem
                            it.onClick()
                        },
                        icon = it.icon,
                        badge = it.badge,
                        label = { it.label?.invoke() ?: Text("") },
                        colors = if (enable)
                            (it.colors?.navigationDrawerItemColors
                                ?: defaultItemColors.navigationDrawerItemColors)
                        else
                            (NavigationDrawerItemDefaults.colors(
                                selectedContainerColor = Color.Transparent,
                                unselectedContainerColor = Color.Transparent,
                                selectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(
                                    alpha = 0.38f
                                ),
                                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(
                                    alpha = 0.38f
                                ),
                                selectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(
                                    alpha = 0.38f
                                ),
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(
                                    alpha = 0.38f
                                )
                            )),
                        interactionSource = if (enable) it.interactionSource else MutableInteractionSource()
                    )
                }
            }
        }

        NavigationSuiteType.None -> { /* Do nothing. */
        }
    }
}

/** The scope associated with the [NavigationSuiteScope]. */
sealed interface NavigationSuiteScope {

    /**
     * This function sets the parameters of the default Material navigation item to be used with the
     * Navigation Suite Scaffold. The item is called in [NavigationSuite], according to the
     * current [NavigationSuiteType].
     *
     * For specifics about each item component, see [NavigationBarItem], [NavigationRailItem], and
     * [NavigationDrawerItem].
     *
     * @param selected whether this item is selected
     * @param onClick called when this item is clicked
     * @param icon icon for this item, typically an [Icon]
     * @param modifier the [Modifier] to be applied to this item
     * @param enabled controls the enabled state of this item. When `false`, this component will not
     * respond to user input, and it will appear visually disabled and disabled to accessibility
     * services. Note: as of now, for [NavigationDrawerItem], this is always `true`.
     * @param label the text label for this item
     * @param alwaysShowLabel whether to always show the label for this item. If `false`, the label
     * will only be shown when this item is selected. Note: for [NavigationDrawerItem] this is
     * always `true`
     * @param badge optional badge to show on this item
     * @param colors [NavigationSuiteItemColors] that will be used to resolve the colors used for
     * this item in different states. If null, [NavigationSuiteDefaults.itemColors] will be used.
     * @param interactionSource an optional hoisted [MutableInteractionSource] for observing and
     * emitting [Interaction]s for this item. You can use this to change the item's appearance
     * or preview the item in different states. Note that if `null` is provided, interactions will
     * still happen internally.
     */
    fun item(
        selected: Boolean,
        onClick: () -> Unit,
        icon: @Composable () -> Unit,
        modifier: Modifier = Modifier,
        enabled: Boolean = true,
        label: @Composable (() -> Unit)? = null,
        alwaysShowLabel: Boolean = true,
        badge: (@Composable () -> Unit)? = null,
        colors: NavigationSuiteItemColors? = null,
        interactionSource: MutableInteractionSource? = null
    )

    /**
     * 程序的标题
     * drawer模式下显示在drawer的左上角
     * navigationBar，navigationRail模式下显示在content代表的top-bar里
     */
    fun title(
        title: (@Composable () -> Unit)?,
    )

    /**
     * 返回按钮
     * drawer模式下显示在drawer的左上角
     * navigationBar，navigationRail模式下显示在content代表的top-bar
     */
    fun back(
        back: (@Composable () -> Unit)?,
    )

    /**
     * 程序的菜单
     * drawer模式显示在title右侧
     * navigationBar模式显示在屏幕右上
     * navigationRail模式显示在title上侧
     */
    fun menu(
        menu: (@Composable () -> Unit)?,
    )

    /**
     * FAB按钮
     * drawer模式下作为extend显示在title下方
     * navigationRail模式下显示在menu下方
     * navigationBar模式下显示在屏幕底端
     */
    fun fab(
        onClick: () -> Unit,
        text: (@Composable () -> Unit)? = null,
        icon: (@Composable () -> Unit)? = null,
        modifier: Modifier = Modifier,
    )
}

/**
 * Class that describes the different navigation suite types of the [NavigationSuiteScaffold].
 *
 * The [NavigationSuiteType] informs the [NavigationSuite] of what navigation component to expect.
 */
@JvmInline
value class NavigationSuiteType private constructor(private val description: String) {
    override fun toString(): String {
        return description
    }

    companion object {
        /**
         * A navigation suite type that instructs the [NavigationSuite] to expect a [NavigationBar]
         * that will be displayed at the bottom of the screen.
         *
         * @see NavigationBar
         */
        val NavigationBar = NavigationSuiteType(description = "NavigationBar")

        /**
         * A navigation suite type that instructs the [NavigationSuite] to expect a [NavigationRail]
         * that will be displayed at the start of the screen.
         *
         * @see NavigationRail
         */
        val NavigationRail = NavigationSuiteType(description = "NavigationRail")

        /**
         * A navigation suite type that instructs the [NavigationSuite] to expect a
         * [PermanentDrawerSheet] that will be displayed at the start of the screen.
         *
         * @see PermanentDrawerSheet
         */
        val NavigationDrawer = NavigationSuiteType(description = "NavigationDrawer")

        /**
         * A navigation suite type that instructs the [NavigationSuite] to not display any
         * navigation components on the screen.
         */
        val None = NavigationSuiteType(description = "None")
    }
}

/** Contains the default values used by the [NavigationSuiteScaffold]. */
object NavigationSuiteScaffoldDefaults {
    /**
     * Returns the expected [NavigationSuiteType] according to the provided [WindowAdaptiveInfo].
     * Usually used with the [NavigationSuiteScaffold] and related APIs.
     *
     * @param adaptiveInfo the provided [WindowAdaptiveInfo]
     * @see NavigationSuiteScaffold
     */
    fun calculateFromAdaptiveInfo(adaptiveInfo: WindowAdaptiveInfo): NavigationSuiteType {
        return with(adaptiveInfo) {
            if (windowPosture.isTabletop ||
                !windowSizeClass.isHeightAtLeastBreakpoint(WindowSizeClass.HEIGHT_DP_MEDIUM_LOWER_BOUND)
            ) {
                NavigationBar
            } else if (
                windowSizeClass.isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND)
            ) {
                NavigationRail
            } else {
                NavigationBar
            }
        }
    }

    /** Default container color for a navigation suite scaffold. */
    val containerColor: Color @Composable get() = MaterialTheme.colorScheme.background

    /** Default content color for a navigation suite scaffold. */
    val contentColor: Color @Composable get() = MaterialTheme.colorScheme.onBackground
}

/** Contains the default values used by the [NavigationSuite]. */
object NavigationSuiteDefaults {
    /**
     * Creates a [NavigationSuiteColors] with the provided colors for the container color, according
     * to the Material specification.
     *
     * Use [Color.Transparent] for the navigation*ContainerColor to have no color. The
     * navigation*ContentColor will default to either the matching content color for
     * navigation*ContainerColor, or to the current [LocalContentColor] if navigation*ContainerColor
     * is not a color from the theme.
     *
     * @param navigationBarContainerColor the default container color for the [NavigationBar]
     * @param navigationBarContentColor the default content color for the [NavigationBar]
     * @param navigationRailContainerColor the default container color for the [NavigationRail]
     * @param navigationRailContentColor the default content color for the [NavigationRail]
     * @param navigationDrawerContainerColor the default container color for the
     * [PermanentDrawerSheet]
     * @param navigationDrawerContentColor the default content color for the [PermanentDrawerSheet]
     */
    @Composable
    fun colors(
        navigationBarContainerColor: Color = NavigationBarDefaults.containerColor,
        navigationBarContentColor: Color = contentColorFor(navigationBarContainerColor),
        navigationRailContainerColor: Color = NavigationRailDefaults.ContainerColor,
        navigationRailContentColor: Color = contentColorFor(navigationRailContainerColor),
        navigationDrawerContainerColor: Color =
            @Suppress("DEPRECATION") DrawerDefaults.containerColor,
        navigationDrawerContentColor: Color = contentColorFor(navigationDrawerContainerColor),
    ): NavigationSuiteColors =
        NavigationSuiteColors(
            navigationBarContainerColor = navigationBarContainerColor,
            navigationBarContentColor = navigationBarContentColor,
            navigationRailContainerColor = navigationRailContainerColor,
            navigationRailContentColor = navigationRailContentColor,
            navigationDrawerContainerColor = navigationDrawerContainerColor,
            navigationDrawerContentColor = navigationDrawerContentColor
        )

    /**
     * Creates a [NavigationSuiteItemColors] with the provided colors for a
     * [NavigationSuiteScope.item].
     *
     * For specifics about each navigation item colors see [NavigationBarItemColors],
     * [NavigationRailItemColors], and [NavigationDrawerItemColors].
     *
     * @param navigationBarItemColors the [NavigationBarItemColors] associated with the
     * [NavigationBarItem] of the [NavigationSuiteScope.item]
     * @param navigationRailItemColors the [NavigationRailItemColors] associated with the
     * [NavigationRailItem] of the [NavigationSuiteScope.item]
     * @param navigationDrawerItemColors the [NavigationDrawerItemColors] associated with the
     * [NavigationDrawerItem] of the [NavigationSuiteScope.item]
     */
    @Composable
    fun itemColors(
        navigationBarItemColors: NavigationBarItemColors = NavigationBarItemDefaults.colors(),
        navigationRailItemColors: NavigationRailItemColors = NavigationRailItemDefaults.colors(),
        navigationDrawerItemColors: NavigationDrawerItemColors =
            NavigationDrawerItemDefaults.colors()
    ): NavigationSuiteItemColors =
        NavigationSuiteItemColors(
            navigationBarItemColors = navigationBarItemColors,
            navigationRailItemColors = navigationRailItemColors,
            navigationDrawerItemColors = navigationDrawerItemColors
        )
}

/**
 * Represents the colors of a [NavigationSuite].
 *
 * For specifics about each navigation component colors see [NavigationBarDefaults],
 * [NavigationRailDefaults], and [DrawerDefaults].
 *
 * @param navigationBarContainerColor the container color for the [NavigationBar] of the
 * [NavigationSuite]
 * @param navigationBarContentColor the content color for the [NavigationBar] of the
 * [NavigationSuite]
 * @param navigationRailContainerColor the container color for the [NavigationRail] of the
 * [NavigationSuite]
 * @param navigationRailContentColor the content color for the [NavigationRail] of the
 * [NavigationSuite]
 * @param navigationDrawerContainerColor the container color for the [PermanentDrawerSheet] of the
 * [NavigationSuite]
 * @param navigationDrawerContentColor the content color for the [PermanentDrawerSheet] of the
 * [NavigationSuite]
 */
class NavigationSuiteColors
internal constructor(
    val navigationBarContainerColor: Color,
    val navigationBarContentColor: Color,
    val navigationRailContainerColor: Color,
    val navigationRailContentColor: Color,
    val navigationDrawerContainerColor: Color,
    val navigationDrawerContentColor: Color
)

/**
 * Represents the colors of a [NavigationSuiteScope.item].
 *
 * For specifics about each navigation item colors see [NavigationBarItemColors],
 * [NavigationRailItemColors], and [NavigationDrawerItemColors].
 *
 * @param navigationBarItemColors the [NavigationBarItemColors] associated with the
 * [NavigationBarItem] of the [NavigationSuiteScope.item]
 * @param navigationRailItemColors the [NavigationRailItemColors] associated with the
 * [NavigationRailItem] of the [NavigationSuiteScope.item]
 * @param navigationDrawerItemColors the [NavigationDrawerItemColors] associated with the
 * [NavigationDrawerItem] of the [NavigationSuiteScope.item]
 */
class NavigationSuiteItemColors(
    val navigationBarItemColors: NavigationBarItemColors,
    val navigationRailItemColors: NavigationRailItemColors,
    val navigationDrawerItemColors: NavigationDrawerItemColors,
)

internal val WindowAdaptiveInfoDefault
    @Composable
    get() = currentWindowAdaptiveInfo()

private interface NavigationSuiteItemProvider {
    val itemsCount: Int
    val itemList: MutableVector<NavigationSuiteItem>

    val title: MutableState<@Composable (() -> Unit)?>
    val menu: MutableState<@Composable (() -> Unit)?>
    val back: MutableState<@Composable (() -> Unit)?>

    val fabText: MutableState<@Composable (() -> Unit)?>
    val fabIcon: MutableState<@Composable (() -> Unit)?>
    val fabOnClick: MutableState<(() -> Unit)?>
    val fabModifier: MutableState<Modifier>
}

private class NavigationSuiteItem(
    val selected: Boolean,
    val onClick: () -> Unit,
    val icon: @Composable () -> Unit,
    val modifier: Modifier,
    val enabled: Boolean,
    val label: @Composable (() -> Unit)?,
    val alwaysShowLabel: Boolean,
    val badge: (@Composable () -> Unit)?,
    val colors: NavigationSuiteItemColors?,
    // TODO(conradchen): Make this nullable when material3 1.3.0 is released.
    val interactionSource: MutableInteractionSource
)

private class NavigationSuiteScopeImpl : NavigationSuiteScope,
    NavigationSuiteItemProvider {

    override fun item(
        selected: Boolean,
        onClick: () -> Unit,
        icon: @Composable () -> Unit,
        modifier: Modifier,
        enabled: Boolean,
        label: @Composable (() -> Unit)?,
        alwaysShowLabel: Boolean,
        badge: (@Composable () -> Unit)?,
        colors: NavigationSuiteItemColors?,
        interactionSource: MutableInteractionSource?
    ) {
        itemList.add(
            NavigationSuiteItem(
                selected = selected,
                onClick = onClick,
                icon = icon,
                modifier = modifier,
                enabled = enabled,
                label = label,
                alwaysShowLabel = alwaysShowLabel,
                badge = badge,
                colors = colors,
                // TODO(conradchen): Remove the fallback logic when material3 1.3.0 is released.
                interactionSource = interactionSource ?: MutableInteractionSource()
            )
        )
    }

    override fun title(title: @Composable (() -> Unit)?) {
        this.title.value = title
    }

    override fun back(back: @Composable (() -> Unit)?) {
        this.back.value = back
    }

    override fun menu(menu: @Composable (() -> Unit)?) {
        this.menu.value = menu
    }

    override fun fab(
        onClick: () -> Unit,
        text: (@Composable (() -> Unit))?,
        icon: (@Composable (() -> Unit))?,
        modifier: Modifier
    ) {
        fabText.value = text
        fabIcon.value = icon
        fabOnClick.value = onClick
        fabModifier.value = modifier
    }

    override val itemList: MutableVector<NavigationSuiteItem> = mutableVectorOf()
    override val fabIcon: MutableState<@Composable (() -> Unit)?> = mutableStateOf(null)
    override val fabText: MutableState<@Composable (() -> Unit)?> = mutableStateOf(null)
    override val fabOnClick: MutableState<(() -> Unit)?> = mutableStateOf(null)
    override val fabModifier: MutableState<Modifier> = mutableStateOf(Modifier)
    override val title: MutableState<@Composable (() -> Unit)?> = mutableStateOf(null)
    override val menu: MutableState<@Composable (() -> Unit)?> = mutableStateOf(null)
    override val back: MutableState<@Composable (() -> Unit)?> = mutableStateOf(null)

    override val itemsCount: Int
        get() = itemList.size
}

@Composable
private fun rememberStateOfItems(
    content: NavigationSuiteScope.() -> Unit
): State<NavigationSuiteItemProvider> {
    val latestContent = rememberUpdatedState(content)
    return remember {
        derivedStateOf { NavigationSuiteScopeImpl().apply(latestContent.value) }
    }
}

@Composable
private fun NavigationItemIcon(
    icon: @Composable () -> Unit,
    badge: (@Composable () -> Unit)? = null,
) {
    if (badge != null) {
        BadgedBox(badge = { badge.invoke() }) {
            icon()
        }
    } else {
        icon()
    }
}

private const val NavigationSuiteLayoutIdTag = "navigationSuite"
private const val ContentLayoutIdTag = "content"

@PreviewScreenSizes
@Composable
private fun NavigationSuiteScaffoldPreview() {
    MaterialTheme {
        NavigationSuiteScaffold(
            modifier = Modifier.fillMaxSize(),
            layoutType = rememberCurrentNavigationSuiteType(),
            navigationSuiteItems = {
                item(
                    selected = true,
                    onClick = {},
                    icon = {
                        Icon(
                            imageVector = Icons.Outlined.Home,
                            contentDescription = "Home",
                        )
                    },
                    label = { Text("Home") },
                    badge = { Badge { Text("3") } },
                )
                item(
                    selected = false,
                    onClick = {},
                    icon = {
                        Icon(
                            imageVector = Icons.Outlined.FavoriteBorder,
                            contentDescription = "Favorites",
                        )
                    },
                    label = { Text("Favorites") },
                )
                item(
                    selected = false,
                    onClick = {},
                    icon = {
                        Icon(
                            imageVector = Icons.Outlined.Search,
                            contentDescription = "Search",
                        )
                    },
                    label = { Text("Search") },
                )
                item(
                    selected = false,
                    onClick = {},
                    icon = {
                        Icon(
                            imageVector = Icons.Outlined.Settings,
                            contentDescription = "Settings",
                        )
                    },
                    label = { Text("Settings") },
                )
                title { Text("Pixiv Home") }
                back {
                    IconButton(onClick = {}) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                        )
                    }
                }
                menu {
                    IconButton(onClick = {}) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "More",
                        )
                    }
                }
                fab(
                    onClick = {},
                    text = { Text("Create") },
                    icon = {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Create",
                        )
                    },
                )
            },
            content = {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Text(
                        text = "Adaptive navigation suite",
                        style = MaterialTheme.typography.headlineSmall,
                    )
                    Text(
                        text = "The navigation layout follows the preview window size.",
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Card(modifier = Modifier.fillMaxWidth()) {
                        ListItem(
                            headlineContent = { Text("Featured illustration") },
                            supportingContent = { Text("A content area for the current destination") },
                            leadingContent = {
                                Icon(
                                    imageVector = Icons.Outlined.FavoriteBorder,
                                    contentDescription = null,
                                )
                            },
                        )
                    }
                    repeat(3) { index ->
                        ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                text = "Preview content ${index + 1}",
                                modifier = Modifier.padding(16.dp),
                                style = MaterialTheme.typography.bodyLarge,
                            )
                        }
                    }
                }
            },
        )
    }
}
