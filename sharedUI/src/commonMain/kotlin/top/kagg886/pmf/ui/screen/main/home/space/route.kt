package top.kagg886.pmf.ui.screen.main.home.space

import top.kagg886.pmf.ui.screen.main.home.space.follow.SpaceFollowRoute
import top.kagg886.pmf.ui.screen.main.home.space.follow.SpaceFollowScreen
import top.kagg886.pmf.ui.screen.main.home.space.latest.SpaceLatestRoute
import top.kagg886.pmf.ui.screen.main.home.space.latest.SpaceLatestScreen
import top.kagg886.pmf.util.nav3.NavGraph
import top.kagg886.pmf.util.nav3.SerializableNavKey

val SpaceRouteGraph: NavGraph.RouteBuilder<SerializableNavKey>.() -> Unit = {
    destination<SpaceFollowRoute> { SpaceFollowScreen() }
    destination<SpaceLatestRoute> { SpaceLatestScreen() }
}
