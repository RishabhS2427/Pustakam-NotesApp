package com.app.pustakam.android.screen.chat

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.app.pustakam.android.theme.typography
import com.app.pustakam.android.widgets.chat.ChatAvatar
import com.app.pustakam.android.widgets.chat.ChatWidget
import com.app.pustakam.android.screen.navigation.Route
import com.app.pustakam.core.common.util.ContentType
import com.app.pustakam.core.common.util.isImage
import com.app.pustakam.core.model.models.response.notes.NoteContentModel

/**
 * 💬 The full-screen thread. It is a title bar and [ChatWidget] — every behaviour lives in the
 * widget, so the sheet, the panel and this screen can never drift apart.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    conversationId: String,
    onBack: () -> Unit,
    onOpenMediaPreview: (NoteContentModel.MediaContent, String) -> Unit = { _, _ -> },
) {
    val viewModel: ChatViewModel = viewModel(key = "chat-$conversationId")
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(
        containerColor = colorScheme.background,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = colorScheme.surface),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                title = {
                    Text(
                        text = state.title,
                        style = typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                actions = {
                    ChatAvatar(
                        label = state.title,
                        avatarUrl = state.conversation?.avatarUrl(),
                        isAssistant = state.isAssistantThread,
                        modifier = Modifier.padding(end = 12.dp),
                    )
                },
            )
        },
    ) { padding ->
        ChatWidget(
            conversationId = conversationId,
            viewModel = viewModel,
            modifier = Modifier.fillMaxSize().padding(padding),
            onOpenMedia = { media ->
                val route = when {
                    media.type.isImage() -> Route.ImagePreview
                    media.type == ContentType.VIDEO -> Route.VideoPreview
                    else -> ""
                }
                if (route.isNotBlank()) onOpenMediaPreview(media, route)
            },
            onOpenDocument = { media -> onOpenMediaPreview(media, Route.BookReader) },
        )
    }
}
