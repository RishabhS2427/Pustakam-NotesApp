package com.app.pustakam.android.screen.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.currentBackStackEntryAsState
import com.app.pustakam.android.fileimport.IncomingShare
import androidx.lifecycle.Lifecycle
import com.app.pustakam.android.screen.AppViewModel
import com.app.pustakam.android.screen.OnLifecycleEvent
import com.app.pustakam.android.screen.navigation.NavRouteRegistry.buildAll

@Composable
fun AppNavGraph(
    modifier: Modifier = Modifier,
    navHostController: PustakmNavController = rememberPustakmNavController(),
) {
    val appViewModel: AppViewModel = viewModel()

    // 🔐 null = not known yet; nothing composes until it resolves (fixes the rotation crash)
    val isAuthenticated: Boolean? = appViewModel.isAuthenticated
        .collectAsStateWithLifecycle(initialValue = null).value

    // 💬 31-Aug-2026: the chat socket follows the session, not the screen
    LaunchedEffect(isAuthenticated) {
        isAuthenticated?.let { appViewModel.onAuthenticationChanged(it) }
    }

    // 🔐 logout navigates here: the NavHost is no longer rebuilt when startRoute changes
    var wasAuthenticated by remember { mutableStateOf(false) }
    LaunchedEffect(isAuthenticated) {
        if (isAuthenticated == true) wasAuthenticated = true
        if (isAuthenticated == false && wasAuthenticated) {
            wasAuthenticated = false
            navHostController.navController.navigate(Route.Authentication) {
                popUpTo(0) { inclusive = true }
                launchSingleTop = true
            }
        }
    }
    OnLifecycleEvent { _, event ->
        when (event) {
            Lifecycle.Event.ON_RESUME -> appViewModel.onForeground(true)
            Lifecycle.Event.ON_PAUSE -> appViewModel.onForeground(false)
            else -> Unit
        }
    }

    val sharedUris = IncomingShare.uris.collectAsStateWithLifecycle().value
    LaunchedEffect(sharedUris, isAuthenticated) {
        if (sharedUris.isNotEmpty() && isAuthenticated == true) navHostController.navigateTo(Route.NotesEditor)
    }

    val backStackEntry by navHostController.navController.currentBackStackEntryAsState()
    val destination = backStackEntry?.destination
    val section = destination.navSection()


    // if/else, never an early return — an early return out of a composable crashes the runtime
    if (isAuthenticated == null) {
        Box(modifier = modifier.fillMaxSize().background(colorScheme.background))
    } else {
        val startRoute = if (isAuthenticated) Route.Home else Route.Authentication

        val host = remember(navHostController) {
            movableContentOf<PaddingValues> { padding ->
                NavHost(
                    navController = navHostController.navController,
                    startDestination = startRoute,
                    modifier = Modifier.padding(padding)
                ) {
                    buildAll(navHostController)
                }
            }
        }

        when (section) {
            NavSection.AUTH -> AuthScaffold(modifier) { padding -> host(padding) }

            NavSection.HOME -> HomeScaffold(
                navController = navHostController,
                currentRoute = destination?.route,
                modifier = modifier
            ) { padding -> host(padding) }

            NavSection.EDITOR -> EditorScaffold(modifier) { padding -> host(padding) }
        }
    }
}
