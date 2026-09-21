package com.app.pustakam.android.screen.masterEditor

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.app.pustakam.android.widgets.audio.AudioPlayerUIState
import com.app.pustakam.android.widgets.document.InlineBookFileWidget
import com.app.pustakam.android.widgets.image.ImageCard
import com.app.pustakam.android.widgets.masterEditor.MasterTextWidget
import com.app.pustakam.android.widgets.smartText.SmartTextTokens
import com.app.pustakam.android.widgets.video.VideoCard
import com.app.pustakam.core.common.util.ContentType
import com.app.pustakam.core.common.util.isDoc
import com.app.pustakam.core.common.util.isImage
import com.app.pustakam.core.model.models.response.notes.NoteContentModel
import com.app.pustakam.core.model.models.response.notes.getMediaUrl
import com.app.pustakam.core.richtext.master.model.CanvasNode
import com.app.pustakam.core.richtext.master.presentation.MasterTextIntent

import com.app.pustakam.core.richtext.master.presentation.MasterTextState

@Composable
fun MasterNodeContent(
    node: CanvasNode,
    isEditing: Boolean,
    scale: Float,
    textState: MasterTextState?,
    content: NoteContentModel?,
    onTextIntent: (MasterTextIntent) -> Unit,
    onFocused: () -> Unit,
    onOpenMedia: () -> Unit,
    onDelete: () -> Unit = {},
    keyboardInsetPx: Float = 0f
) {
    val colors = SmartTextTokens.colors
    when  {
         content is NoteContentModel.TextContent  -> {
            if (textState == null) {
                MasterNodePlaceholder("Empty text")
            } else {
                MasterTextWidget(
                    state = textState,
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    scale = scale,
                    scrollable = false,
                    minLines = 5,
                    readOnly = false,
                    onIntent = onTextIntent,
                    keyboardInsetPx = keyboardInsetPx,
                    shouldFocus = isEditing,
                    onFocusChanged = { if (it) onFocused() }
                )
            }
        }
        content is NoteContentModel.MediaContent -> {
            when  {
                content.type.isImage() -> ImageCard(
                    modifier = Modifier.fillMaxSize(),
                    imageUrl = content.getMediaUrl(),
                    media = content,
                    onClick = onOpenMedia
                )

               content.type ==  ContentType.VIDEO -> VideoCard(
                    modifier = Modifier.fillMaxSize(),
                    contentVideo = content,
                    onClick = onOpenMedia
                )

                content.type == ContentType.AUDIO -> AudioPlayerUIState(
                    noteContentModel = content,
                    onDelete = { onDelete() }
                )

                content.type.isDoc()  -> {
                        InlineBookFileWidget(
                            media = content,
                            modifier = Modifier.fillMaxSize(),
                            onOpenFull = onOpenMedia
                        )
                }

                content.type == ContentType.LINK -> {
                    val link = content as? NoteContentModel.Link
                    val uriHandler = LocalUriHandler.current
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .clickable {
                                link?.url?.takeIf { it.isNotBlank() }
                                    ?.let { runCatching { uriHandler.openUri(it) } }
                            }
                            .padding(16.dp),
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Link,
                            contentDescription = null,
                            tint = colors.accent,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = link?.url.orEmpty().ifEmpty { "Link" },
                            style = TextStyle(color = colors.accent, fontSize = 15.sp),
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                }

                content.type ==  ContentType.LOCATION -> {
                    val location = content as? NoteContentModel.Location
                    val uriHandler = LocalUriHandler.current
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .clickable {
                                location?.let {
                                    runCatching {
                                        uriHandler.openUri("geo:${it.latitude},${it.longitude}")
                                    }
                                }
                            }
                            .padding(16.dp),
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.LocationOn,
                            contentDescription = null,
                            tint = colors.accent,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = location?.address?.takeIf { it.isNotBlank() }
                                ?: location?.let { "${it.latitude}, ${it.longitude}" }
                                ?: "Location",
                            style = TextStyle(color = colors.onSurface, fontSize = 15.sp),
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                }

                else -> MasterNodePlaceholder(node.kind.name)
            }
        }
    }
}

@Composable
private fun MasterNodePlaceholder(label: String) {
    val colors = SmartTextTokens.colors
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.codeBackground),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = TextStyle(color = colors.onSurfaceMuted, fontSize = 13.sp)
        )
    }
}
