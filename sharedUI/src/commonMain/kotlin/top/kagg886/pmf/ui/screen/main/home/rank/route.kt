package top.kagg886.pmf.ui.screen.main.home.rank

import top.kagg886.pmf.ui.screen.main.home.rank.day.RankDayRoute
import top.kagg886.pmf.ui.screen.main.home.rank.day.RankDayScreen
import top.kagg886.pmf.ui.screen.main.home.rank.dayfemale.RankDayFemaleRoute
import top.kagg886.pmf.ui.screen.main.home.rank.dayfemale.RankDayFemaleScreen
import top.kagg886.pmf.ui.screen.main.home.rank.daymale.RankDayMaleRoute
import top.kagg886.pmf.ui.screen.main.home.rank.daymale.RankDayMaleScreen
import top.kagg886.pmf.ui.screen.main.home.rank.month.RankMonthRoute
import top.kagg886.pmf.ui.screen.main.home.rank.month.RankMonthScreen
import top.kagg886.pmf.ui.screen.main.home.rank.week.RankWeekRoute
import top.kagg886.pmf.ui.screen.main.home.rank.week.RankWeekScreen
import top.kagg886.pmf.ui.screen.main.home.rank.weekoriginal.RankWeekOriginalRoute
import top.kagg886.pmf.ui.screen.main.home.rank.weekoriginal.RankWeekOriginalScreen
import top.kagg886.pmf.ui.screen.main.home.rank.weekrookie.RankWeekRookieRoute
import top.kagg886.pmf.ui.screen.main.home.rank.weekrookie.RankWeekRookieScreen
import top.kagg886.pmf.util.nav3.NavGraph
import top.kagg886.pmf.util.nav3.SerializableNavKey

val RankRouteGraph: NavGraph.RouteBuilder<SerializableNavKey>.() -> Unit = {
    destination<RankDayRoute> { RankDayScreen() }
    destination<RankWeekRoute> { RankWeekScreen() }
    destination<RankMonthRoute> { RankMonthScreen() }
    destination<RankDayMaleRoute> { RankDayMaleScreen() }
    destination<RankDayFemaleRoute> { RankDayFemaleScreen() }
    destination<RankWeekOriginalRoute> { RankWeekOriginalScreen() }
    destination<RankWeekRookieRoute> { RankWeekRookieScreen() }
}
