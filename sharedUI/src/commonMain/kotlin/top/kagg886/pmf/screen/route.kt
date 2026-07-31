package top.kagg886.pmf.screen

import top.kagg886.pmf.screen.login.LoginRoute
import top.kagg886.pmf.screen.login.LoginScreen
import top.kagg886.pmf.screen.main.MainRoute
import top.kagg886.pmf.screen.main.MainRouteGraph
import top.kagg886.pmf.screen.main.MainScreen
import top.kagg886.pmf.screen.main.home.HomeRoute
import top.kagg886.pmf.screen.welcome.WelcomeRoute
import top.kagg886.pmf.screen.welcome.WelcomeScreen
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
