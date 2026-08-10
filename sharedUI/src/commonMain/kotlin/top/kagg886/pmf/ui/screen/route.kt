package top.kagg886.pmf.ui.screen

import top.kagg886.pmf.ui.screen.logger.LoggerRoute
import top.kagg886.pmf.ui.screen.logger.LoggerRouteGraph
import top.kagg886.pmf.ui.screen.logger.LoggerScreen
import top.kagg886.pmf.ui.screen.logger.list.LoggerListRoute
import top.kagg886.pmf.ui.screen.login.LoginRoute
import top.kagg886.pmf.ui.screen.login.LoginScreen
import top.kagg886.pmf.ui.screen.main.MainRoute
import top.kagg886.pmf.ui.screen.main.MainRouteGraph
import top.kagg886.pmf.ui.screen.main.MainScreen
import top.kagg886.pmf.ui.screen.main.home.HomeRoute
import top.kagg886.pmf.ui.screen.welcome.WelcomeRoute
import top.kagg886.pmf.ui.screen.welcome.WelcomeScreen
import top.kagg886.pmf.util.nav3.createNavGraph

/**
 * ================================================
 * Author:     iveou
 * Created on: 2026/7/27 13:37
 * ================================================
 */

val ApplicationGraph = createNavGraph {
    route(parent = RootRoute, startDestination = WelcomeRoute, content = ::RootScreen) {
        destination<WelcomeRoute> { WelcomeScreen() }
        destination<LoginRoute> { LoginScreen() }

        route<LoggerRoute>(
            parent = LoggerRoute,
            startDestination = LoggerListRoute,
            content = ::LoggerScreen,
            builder = LoggerRouteGraph,
        )

        route<MainRoute>(
            parent = MainRoute,
            startDestination = HomeRoute,
            content = ::MainScreen,
            builder = MainRouteGraph,
        )
    }
}
