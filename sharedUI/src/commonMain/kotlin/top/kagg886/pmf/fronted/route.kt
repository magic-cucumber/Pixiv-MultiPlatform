package top.kagg886.pmf.fronted

import top.kagg886.pmf.fronted.login.LoginRoute
import top.kagg886.pmf.fronted.login.LoginScreen
import top.kagg886.pmf.fronted.main.MainRoute
import top.kagg886.pmf.fronted.main.MainRouteGraph
import top.kagg886.pmf.fronted.main.MainScreen
import top.kagg886.pmf.fronted.main.home.HomeRoute
import top.kagg886.pmf.fronted.welcome.WelcomeRoute
import top.kagg886.pmf.fronted.welcome.WelcomeScreen
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
