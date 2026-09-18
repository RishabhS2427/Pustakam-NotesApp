package com.app.pustakam.android.screen.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.app.pustakam.android.widgets.fabWidget.AddNewNoteFAB
import com.app.pustakam.core.common.extensions.isNotnull


@Composable
fun AuthScaffold(
    modifier: Modifier = Modifier,
    content: @Composable (PaddingValues) -> Unit
) {
    Scaffold(
        modifier = modifier.fillMaxSize().imePadding(),
        containerColor = colorScheme.background
    ) { padding ->
        content(padding)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScaffold(
    navController: PustakmNavController,
    currentRoute: String?,
    modifier: Modifier = Modifier,
    content: @Composable (PaddingValues) -> Unit
) {
    val chrome = NavRouteRegistry.chromeFor(currentRoute)
    Scaffold(
        modifier = modifier.fillMaxSize().imePadding(),
        containerColor = colorScheme.background,
        topBar = { if (chrome.showsTopBar)
            if (currentRoute.isNotnull() && navController.shouldShowTopBar)
                TopAppBar(title = {
                Text(text = currentRoute!!, textAlign = TextAlign.Center)
            }, colors = TopAppBarDefaults.topAppBarColors(containerColor = colorScheme.background)
            ) } ,
        bottomBar = { if (chrome.showsBottomBar) BottomBar(navController) },
        floatingActionButton =  {if (chrome.showsFab) {
            when (currentRoute) {
                Route.Notes -> AddNewNoteFAB {
                    navController.navigateTo(Route.NotesEditor)
                }
            }
        }
        }
    ) { padding ->
        content(padding)
    }
}
@Composable
fun EditorScaffold(
    modifier: Modifier = Modifier,
    content: @Composable (PaddingValues) -> Unit
) {
    Box(modifier = modifier.fillMaxSize()) {
        content(PaddingValues(0.dp))
    }
}
