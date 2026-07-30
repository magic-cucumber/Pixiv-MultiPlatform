package top.kagg886.pmf.ui

import top.kagg886.pmf.ui.login.LoginRoute
import top.kagg886.pmf.ui.login.LoginScreen
import top.kagg886.pmf.ui.main.MainRoute
import top.kagg886.pmf.ui.main.MainRouteGraph
import top.kagg886.pmf.ui.main.MainScreen
import top.kagg886.pmf.ui.main.home.HomeRoute
import top.kagg886.pmf.ui.welcome.WelcomeRoute
import top.kagg886.pmf.ui.welcome.WelcomeScreen
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

        route<MainRoute>(MainRoute, HomeRoute,::MainScreen,MainRouteGraph)
    }
}
