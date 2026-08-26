package top.kagg886.pmf.ui.screen.main.home

import top.kagg886.pmf.ui.screen.main.home.rank.RankRoute
import top.kagg886.pmf.ui.screen.main.home.rank.RankRouteGraph
import top.kagg886.pmf.ui.screen.main.home.rank.RankScreen
import top.kagg886.pmf.ui.screen.main.home.recommend.RecommendRoute
import top.kagg886.pmf.ui.screen.main.home.recommend.RecommendRouteGraph
import top.kagg886.pmf.ui.screen.main.home.recommend.RecommendScreen
import top.kagg886.pmf.ui.screen.main.home.space.SpaceRoute
import top.kagg886.pmf.ui.screen.main.home.space.SpaceRouteGraph
import top.kagg886.pmf.ui.screen.main.home.space.SpaceScreen
import top.kagg886.pmf.ui.screen.main.home.rank.day.RankDayRoute
import top.kagg886.pmf.ui.screen.main.home.recommend.illust.RecommendIllustRoute
import top.kagg886.pmf.ui.screen.main.home.space.follow.SpaceFollowRoute
import top.kagg886.pmf.util.nav3.NavGraph
import top.kagg886.pmf.util.nav3.SerializableNavKey

val HomeRouteGraph: NavGraph.RouteBuilder<SerializableNavKey>.() -> Unit = {
    route<RecommendRoute>(
        parent = RecommendRoute,
        startDestination = RecommendIllustRoute,
        content = ::RecommendScreen,
        builder = RecommendRouteGraph,
    )
    route<RankRoute>(
        parent = RankRoute,
        startDestination = RankDayRoute,
        content = ::RankScreen,
        builder = RankRouteGraph,
    )
    route<SpaceRoute>(
        parent = SpaceRoute,
        startDestination = SpaceFollowRoute,
        content = ::SpaceScreen,
        builder = SpaceRouteGraph,
    )
}
