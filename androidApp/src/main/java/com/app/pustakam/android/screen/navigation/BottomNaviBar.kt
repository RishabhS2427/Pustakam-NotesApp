package com.app.pustakam.android.screen.navigation

import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarDefaults
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemColors
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.app.pustakam.android.extension.toImageVector
import com.app.pustakam.android.screen.AppViewModel

@Composable
fun BottomBar(
    navController: PustakmNavController = rememberPustakmNavController(),
) {
    val appViewModel: AppViewModel = viewModel()
    val unreadChats by appViewModel.unreadChats.collectAsStateWithLifecycle()
    NavigationBar(containerColor = colorScheme.surfaceBright) {
        val currentRoute = navController.currentRoute()
        navController.navigationScreen.forEach { item ->
            NavigationBarItem(
                selected = currentRoute == item.route,
                icon = {
                    val badgeCount = if (item.route == Route.Chat) unreadChats else 0
                    BadgedBox(badge = {
                        if (badgeCount > 0) Badge { Text(text = if (badgeCount > 99) "99+" else "$badgeCount") }
                    }) {
                        Icon(
                            imageVector = (if (item.route == currentRoute) item.selectedIcon.toImageVector() else item.unselectedIcon.toImageVector()),
                            contentDescription = item.title
                        )
                    }
                },
                onClick = {
                    navController.navigateToBottomBarRoute(item.route)
                },
            )
        }
    }
}