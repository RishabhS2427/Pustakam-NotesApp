package com.app.pustakam.android.widgets.drawing

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.app.pustakam.android.widgets.smartText.SmartTextTokens
import com.app.pustakam.core.drawing.editor.DrawCommands
import com.app.pustakam.core.drawing.editor.DrawIntent
import com.app.pustakam.core.drawing.editor.DrawPanel
import com.app.pustakam.core.drawing.model.DrawColor
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

@Composable
fun DrawingChrome(session: DrawingSession, onDone: () -> Unit, modifier: Modifier = Modifier) {
    var panel by remember(session) { mutableStateOf(DrawCommands.noPanel()) }
    DrawingToolbar(session = session, onPanel = { panel = it }, onDone = onDone, modifier = modifier)
    DrawingPanelSheet(session = session, panel = panel, onDismiss = { panel = DrawCommands.noPanel() })
}

@Composable
fun DrawingToolbar(
    session: DrawingSession,
    onPanel: (DrawPanel) -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = SmartTextTokens.colors
    val toolbarFlow = remember(session) { session.state.map(DrawCommands::toolbar).distinctUntilChanged() }
    val toolbar by toolbarFlow.collectAsState(DrawCommands.toolbar(session.current))
    val send: (DrawIntent) -> Unit = session::dispatch
    Row(
        modifier = modifier
            .background(colors.toolbar, RoundedCornerShape(12.dp))
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 6.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        DrawingToolButton(Icons.Default.Check, "Done", colors.accent, onClick = onDone)
        DrawingToolbarDivider()
        toolbar.tools.forEach { spec ->
            DrawingToolButton(
                icon = DrawingIcons.tool(spec.iconKey),
                label = spec.label,
                tint = if (toolbar.isTool(spec.id)) colors.accent else colors.onSurface,
                onClick = {
                    if (DrawCommands.opensPanel(session.current, spec.id)) onPanel(DrawCommands.panelFor(spec.id))
                    else send(DrawCommands.setTool(spec.id))
                }
            )
        }
        DrawingColorDot(color = toolbar.color, onClick = { onPanel(DrawCommands.colorPanel()) })
        DrawingToolbarDivider()
        DrawingToolButton(
            Icons.AutoMirrored.Filled.Undo,
            "Undo",
            colors.onSurface,
            enabled = toolbar.canUndo,
            onClick = { send(DrawCommands.undo()) }
        )
        DrawingToolButton(
            Icons.AutoMirrored.Filled.Redo,
            "Redo",
            colors.onSurface,
            enabled = toolbar.canRedo,
            onClick = { send(DrawCommands.redo()) }
        )
        if (toolbar.navigates) {
            DrawingToolbarDivider()
            DrawingToolButton(Icons.Default.ZoomOut, "Zoom out", colors.onSurface, onClick = { send(DrawCommands.zoomOut()) })
            Text(
                text = "${toolbar.zoomPercent}%",
                style = TextStyle(color = colors.onSurfaceMuted, fontSize = 13.sp)
            )
            DrawingToolButton(Icons.Default.ZoomIn, "Zoom in", colors.onSurface, onClick = { send(DrawCommands.zoomIn()) })
            DrawingToolButton(Icons.Default.CenterFocusStrong, "Fit", colors.onSurface, onClick = { send(DrawCommands.fitPaper()) })
            if (toolbar.rotated) {
                DrawingToolButton(Icons.Default.Explore, "Reset rotation", colors.onSurface, onClick = { send(DrawCommands.resetRotation()) })
            }
        }
        DrawingToolbarDivider()
        if (toolbar.showsPaper) {
            DrawingToolButton(Icons.Default.GridOn, "Paper", colors.onSurface, onClick = { onPanel(DrawCommands.paperPanel()) })
        }
        DrawingToolButton(Icons.Default.Tune, "Drawing settings", colors.onSurface, onClick = { onPanel(DrawCommands.settingsPanel()) })
    }
}

@Composable
private fun DrawingToolButton(
    icon: ImageVector,
    label: String,
    tint: Color,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    IconButton(onClick = onClick, enabled = enabled) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = if (enabled) tint else tint.copy(alpha = 0.35f)
        )
    }
}

@Composable
private fun DrawingToolbarDivider() {
    Box(
        modifier = Modifier
            .padding(horizontal = 4.dp)
            .width(1.dp)
            .height(24.dp)
            .background(SmartTextTokens.colors.divider)
    )
}

@Composable
fun DrawingColorDot(color: DrawColor, onClick: () -> Unit, selected: Boolean = false, diameter: Int = 28) {
    val colors = SmartTextTokens.colors
    Box(
        modifier = Modifier
            .padding(6.dp)
            .size(diameter.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick)
            .border(if (selected) 2.5.dp else 1.dp, if (selected) colors.accent else colors.divider, CircleShape)
            .padding(3.dp)
            .background(color.toCompose(1f), CircleShape)
    )
}
