package top.kagg886.pmf.fronted.main

import kotlinx.serialization.Serializable
import top.kagg886.pmf.fronted.main.home.HomeRoute
import top.kagg886.pmf.fronted.main.home.HomeScreen
import top.kagg886.pmf.util.nav3.NavGraph
import top.kagg886.pmf.util.nav3.SerializableNavKey

/**
 * ================================================
 * Author:     iveou
 * Created on: 2026/7/29 15:18
 * ================================================
 */


val MainRouteGraph: NavGraph.RouteBuilder<SerializableNavKey>.() -> Unit = {
    destination<HomeRoute> { HomeScreen() }
}
