package com.app.pustakam.android.screen.navigation

import androidx.navigation.NavGraphBuilder

data class RouteChrome(
    val showsBottomBar: Boolean = false,
    val showsTopBar: Boolean = false,
    val showsFab: Boolean = false,
)

object NavRouteRegistry {

    private val chrome: Map<String, RouteChrome> = mapOf(
        Route.Notes to RouteChrome(showsBottomBar = true, showsTopBar = true, showsFab = true),
        Route.Search to RouteChrome(showsBottomBar = true, showsTopBar = true),
        Route.Notification to RouteChrome(showsBottomBar = true, showsTopBar = true),
        // 💬 the inbox keeps the bottom bar; the thread screen brings its own title bar
        Route.Chat to RouteChrome(showsBottomBar = true, showsTopBar = true),
        Route.Settings to RouteChrome(showsBottomBar = true),
        // 👤 the profile screen brings its own title bar
        Route.Profile to RouteChrome(),
    )

    fun chromeFor(route: String?): RouteChrome = chrome[route] ?: RouteChrome()

    private val graphs: List<NavGraphBuilder.(PustakmNavController) -> Unit> = listOf(
        { nav -> AuthNavGraph(nav) },
        { nav -> HomeNavGraph(nav) },
        { nav -> EditorNavGraph(nav) },
    )

    fun NavGraphBuilder.buildAll(navController: PustakmNavController) {
        graphs.forEach { graph -> graph(navController) }
    }
}
