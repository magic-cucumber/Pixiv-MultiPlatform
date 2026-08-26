package top.kagg886.pmf.ui.screen.main.home.recommend

import top.kagg886.pmf.ui.screen.main.home.recommend.illust.RecommendIllustScreen
import top.kagg886.pmf.ui.screen.main.home.recommend.illust.RecommendIllustRoute
import top.kagg886.pmf.ui.screen.main.home.recommend.novel.RecommendNovelRoute
import top.kagg886.pmf.ui.screen.main.home.recommend.novel.RecommendNovelScreen
import top.kagg886.pmf.util.nav3.NavGraph
import top.kagg886.pmf.util.nav3.SerializableNavKey

val RecommendRouteGraph: NavGraph.RouteBuilder<SerializableNavKey>.() -> Unit = {
    destination<RecommendIllustRoute> { RecommendIllustScreen() }
    destination<RecommendNovelRoute> { RecommendNovelScreen() }
}
