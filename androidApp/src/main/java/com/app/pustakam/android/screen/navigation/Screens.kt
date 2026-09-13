package com.app.pustakam.android.screen.navigation


import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import com.app.pustakam.android.R

import kotlinx.serialization.Serializable

sealed class BottomNavigationItem(
    val title: String,
    val selectedIcon: Any,
    val unselectedIcon: Any,
    val batchCount: Int? = null,
    val hasNotification: Boolean, val route: String
)
@Serializable
sealed class Screen(val route: String) {
    @Serializable
    data object Authentication : Screen(route = Route.Authentication) {
        @Serializable
        data object SignUpScreen : Screen(route = Route.Signup)
        @Serializable
        data object LoginScreen : Screen(route = Route.Login)
    }

    data object HomeScreen : BottomNavigationItem(
        "Notes", selectedIcon = R.drawable.ic_book_icon, unselectedIcon = R.drawable.ic_book_icon, hasNotification = false, route = Route.Home
    ) {
        data object NotesScreen : BottomNavigationItem(
            "Notes", selectedIcon =  R.drawable.ic_book_icon, unselectedIcon =  R.drawable.ic_book_icon, hasNotification = false, route = Route.Notes
        )

        data object SearchScreen : BottomNavigationItem(
            "Search", selectedIcon = Icons.Default.Search, unselectedIcon = Icons.Default.Search, hasNotification = false, route = Route.Search
        )

        // 💬 31-Aug-2026 chat
        data object ChatScreen : BottomNavigationItem(
            "Chat", selectedIcon = Icons.AutoMirrored.Filled.Chat, unselectedIcon = Icons.AutoMirrored.Filled.Chat, hasNotification = false, route = Route.Chat
        )

        data object NotificationScreen : BottomNavigationItem(
            "Notification", selectedIcon = Icons.Default.Notifications, unselectedIcon = Icons.Default.Notifications, hasNotification = false, route = Route.Notification
        )
        data object SettingsScreen : BottomNavigationItem(
            "Settings", selectedIcon = Icons.Default.Settings, unselectedIcon = Icons.Default.Settings, hasNotification = false, route = Route.Settings
        )
    }
}

