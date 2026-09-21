package com.app.pustakam.android.widgets.image

import android.content.res.Configuration
import android.text.Layout
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.requiredSizeIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CardElevation
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.app.pustakam.android.MyApplicationTheme
import com.app.pustakam.android.extension.actionIconButtonBackground
import com.app.pustakam.android.theme.actionIconTintColor
import com.app.pustakam.android.widgets.LoadImage
import com.app.pustakam.android.widgets.media.MediaDownloadOverlay
import com.app.pustakam.core.model.models.response.notes.NoteContentModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ImageCard(modifier: Modifier = Modifier,
              imageUrl : String = "", onClick: ()-> Unit,
              onShowActions: (Boolean) -> Unit = {},
              // 📥 20-Sep-2026 — optional so the previews and any caller without a block still compile
              media: NoteContentModel.MediaContent? = null,
              overlay: @Composable BoxScope.() -> Unit = {}) {
    val scope = rememberCoroutineScope()
    // 🔧 14-Jul-2026: pending auto-hide; cancelled and restarted on every long-press
    val hideJob = remember { mutableStateOf<Job?>(null) }
    val screenHeightDp = LocalConfiguration.current.screenHeightDp
    val cardHeight = (screenHeightDp * 0.42f).dp.coerceIn(260.dp, 460.dp)

        Card(modifier = Modifier.fillMaxWidth(0.7f).requiredHeight(cardHeight)
            .clickable{ onClick()}
            .padding(8.dp),
            elevation =CardDefaults.cardElevation(defaultElevation = 6.dp),
            shape = RoundedCornerShape(14.dp),
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                LoadImage(url = imageUrl, modifier = modifier.matchParentSize())
                Icon(imageVector = Icons.Filled.MoreVert, contentDescription = "More options",
                    tint = actionIconTintColor,
                    modifier = Modifier
                        .padding(vertical = 12.dp, horizontal = 8.dp)
                        .actionIconButtonBackground()
                        .align(
                    Alignment.TopEnd).clickable{
                    onShowActions(true)
                    hideJob.value?.cancel()
                    hideJob.value = scope.launch {
                        delay(2500)
                        onShowActions(false)
                    }
                })
                // 📥 20-Sep-2026 — the generic transfer bar; draws nothing once the bytes are here
                if (media != null) MediaDownloadOverlay(media, Modifier.align(Alignment.BottomCenter))
                overlay()
            }
        }
}
@Preview("default")
@Preview("dark theme", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview("large font", fontScale = 2f)
@Composable
private fun ImageCardPreview() {
    /** App Theme */ /** View */
    MyApplicationTheme {
        /** View */ /** View */ ImageCard(onClick =  {}){}
    }
}
