package com.app.pustakam.android.screen.navigation

import android.annotation.SuppressLint
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.Lifecycle
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavDestination
import androidx.navigation.NavGraph
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.app.pustakam.core.common.extensions.isNotnull
import com.app.pustakam.core.model.models.CameraData

object Route {
    const val Home = "HOME"
    const val Search = "Search"
    const val Notification = "NOTIFICATION"
    const val Login = "LOGIN"
    const val Signup = "SIGNUP"
    const val Notes = "NOTES"
    const val NotesEditor = "NOTES_EDITOR"
    const val NotesEditorRouter = "NOTES_EDITOR_ROUTER"
    const val MasterEditor = "MASTER_EDITOR"
    const val Authentication = "AUTH"
    const val ImagePreview = "IMAGE_PREVIEW"
    const val VideoPreview = "VIDEO_PREVIEW"
    const val Settings = "SETTINGS"
    const val BookReader = "BOOK_READER"
    const val NoteBookReader = "NOTEBOOK_READER"
    const val Chat = "CHAT"
    const val ChatThread = "CHAT_THREAD"
    const val Profile = "PROFILE"
}

/** Screens that belong to a capture flow rather than to a place the user was working. */
private val captureRoutes = listOf(
    Route.ImagePreview,
    Route.VideoPreview,
    CameraData::class.qualifiedName.orEmpty()
)
@Stable
class PustakmNavController(
    val navController: NavHostController,
) {
    val navigationScreen = listOf(
        Screen.HomeScreen.NotesScreen,
        Screen.HomeScreen.SearchScreen,
        // 💬 31-Aug-2026: chat sits between search and notifications
        Screen.HomeScreen.ChatScreen,
        Screen.HomeScreen.NotificationScreen,
        Screen.HomeScreen.SettingsScreen
    )
    private val currentChrome
        get() = NavRouteRegistry.chromeFor(navController.currentBackStackEntry?.destination?.route)
    val shouldShowTopBar get() = currentChrome.showsTopBar

    fun upPress() {
        navController.navigateUp()
    }
    /**
     * Pops back to [route], keeping it on the stack. One atomic pop rather than a loop of
     * upPress(): if the route is not on the back stack popBackStack() reports it and we step
     * back once, instead of emptying the stack and leaving the host with no destination.
     */
    fun popBackInclusive (route: String ?= null){
        if (route.isNotnull() && navController.popBackStack(route!!, false)) return
        upPress()
    }


    /**
     * The screen that opened the capture flow: the newest back stack entry that is neither a
     * camera/preview screen nor a NavGraph. Graph entries carry routes too, but the current
     * entry is never one, so returning a graph route would make popBackInclusive unstoppable.
     */
    @SuppressLint("RestrictedApi")
    fun captureOriginRoute(): String? = navController.currentBackStack.value
        .asReversed()
        .filterNot { it.destination is NavGraph }
        .mapNotNull { it.destination.route }
        .firstOrNull { route -> captureRoutes.none { route.startsWith(it) } }

    fun goToHomeScreen() {
        navController.clearBackStack<Screen.Authentication>()
        navController.navigate(Route.Home) {
            launchSingleTop = true
        }
    }

    fun navigateTo(route: String) {
        navController.navigate(route)
    }
    fun navigateTo(route: Any){
        if(route is String)
            navigateTo(route)
        else navController.navigate(route)
    }
@Composable
fun currentRoute(): String? {
        val navBackStackEntry by navController.currentBackStackEntryAsState()
        val currentRoute = navBackStackEntry?.destination?.route
        return currentRoute
    }

    fun navigateToBottomBarRoute(route: String) {
        if (route != navController.currentDestination?.route) {
            navController.navigate(route) {
                launchSingleTop = true
                restoreState = true
                popUpTo(findStartDestination(navController.graph).id) {
                    saveState = true
                }
            }
        }
    }
}

@Composable
fun rememberPustakmNavController(
    navController: NavHostController = rememberNavController()
): PustakmNavController = remember(navController) {
    PustakmNavController(navController)
}

private fun NavBackStackEntry.lifecycleIsResumed() = this.lifecycle.currentState == Lifecycle.State.RESUMED

private val NavGraph.startDestination: NavDestination?
    get() = findNode(startDestinationId)

/**
 * Copied from similar function in NavigationUI.kt
 * https://cs.android.com/androidx/platform/frameworks/support/+/androidx-main:navigation/navigation-ui/src/main/java/androidx/navigation/ui/NavigationUI.kt
 */
private tailrec fun findStartDestination(graph: NavDestination): NavDestination {
    return if (graph is NavGraph) findStartDestination(graph.startDestination!!) else graph
}


