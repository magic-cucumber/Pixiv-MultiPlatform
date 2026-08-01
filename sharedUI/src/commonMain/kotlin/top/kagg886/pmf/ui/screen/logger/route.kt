package top.kagg886.pmf.ui.screen.logger

import top.kagg886.pmf.ui.screen.logger.detail.LoggerDetailRoute
import top.kagg886.pmf.ui.screen.logger.detail.LoggerDetailScreen
import top.kagg886.pmf.ui.screen.logger.list.LoggerListRoute
import top.kagg886.pmf.ui.screen.logger.list.LoggerListScreen
import top.kagg886.pmf.util.nav3.NavGraph
import top.kagg886.pmf.util.nav3.SerializableNavKey



val LoggerRouteGraph: NavGraph.RouteBuilder<SerializableNavKey>.() -> Unit = {
    destination<LoggerListRoute> { LoggerListScreen() }
    dialog<LoggerDetailRoute> { LoggerDetailScreen(it) }
}
