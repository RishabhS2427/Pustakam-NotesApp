@file:OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)

package com.app.pustakam.android.widgets.drawing

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.app.pustakam.android.widgets.colorPalete.ColorSelector
import com.app.pustakam.android.widgets.smartText.SmartTextTokens
import com.app.pustakam.core.drawing.editor.DrawCommands
import com.app.pustakam.core.drawing.editor.DrawEditorState
import com.app.pustakam.core.drawing.editor.DrawPanel
import com.app.pustakam.core.drawing.model.DrawColor

@Composable
fun DrawingPanelSheet(session: DrawingSession, panel: DrawPanel, onDismiss: () -> Unit) {
    if (panel == DrawPanel.NONE) return
    val colors = SmartTextTokens.colors
    val state by session.state.collectAsState()
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = colors.surface,
        contentColor = colors.onSurface
    ) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            when (panel) {
                DrawPanel.BRUSH -> DrawingBrushPanel(session, state)
                DrawPanel.ERASER -> DrawingEraserPanel(session, state)
                DrawPanel.SHAPE -> DrawingShapePanel(session, state)
                DrawPanel.COLOR -> DrawingColorPanel(session, state)
                DrawPanel.PAPER -> DrawingPaperPanel(session, state)
                DrawPanel.SETTINGS -> DrawingSettingsPanel(session, state, onDismiss)
                DrawPanel.NONE -> Unit
            }
        }
    }
}

@Composable
private fun DrawingBrushPanel(session: DrawingSession, state: DrawEditorState) {
    DrawingSectionTitle("Pen")
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        DrawCommands.brushKinds().forEach { kind ->
            DrawingChoiceCard(
                selected = DrawCommands.isBrush(state, kind),
                label = DrawCommands.brushLabel(kind),
                onClick = { session.dispatch(DrawCommands.setBrushKind(kind)) }
            ) {
                DrawingEntries(
                    entries = { width, height -> DrawCommands.brushPreview(state, kind, width, height) },
                    modifier = Modifier.size(width = 88.dp, height = 40.dp)
                )
            }
        }
    }
    DrawCommands.brushControls(state).forEach { control ->
        DrawingSlider(
            label = DrawCommands.brushControlLabel(control),
            text = DrawCommands.brushControlText(state, control),
            position = DrawCommands.brushControlPosition(state, control),
            onChange = { session.dispatch(DrawCommands.setBrushControl(session.current, control, it)) }
        )
    }
}

@Composable
private fun DrawingEraserPanel(session: DrawingSession, state: DrawEditorState) {
    DrawingSectionTitle("Eraser")
    DrawingChips(
        items = DrawCommands.eraserKinds(state),
        isSelected = { DrawCommands.isEraser(state, it) },
        label = DrawCommands::eraserLabel,
        onSelect = { session.dispatch(DrawCommands.setEraserKind(it)) }
    )
    DrawingSlider(
        label = "Size",
        text = DrawCommands.eraserText(state),
        position = DrawCommands.eraserPosition(state),
        onChange = { session.dispatch(DrawCommands.setEraserPosition(it)) }
    )
}

@Composable
private fun DrawingShapePanel(session: DrawingSession, state: DrawEditorState) {
    DrawingSectionTitle("Shapes")
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        DrawCommands.shapeKinds().forEach { kind ->
            DrawingChoiceCard(
                selected = DrawCommands.isShape(state, kind),
                label = DrawCommands.shapeLabel(kind),
                onClick = { session.dispatch(DrawCommands.setShapeKind(kind)) }
            ) {
                DrawingEntries(
                    entries = { width, height -> DrawCommands.shapePreview(state, kind, width, height) },
                    modifier = Modifier.size(44.dp)
                )
            }
        }
    }
    DrawCommands.shapeControls(state).forEach { control ->
        DrawingSlider(
            label = DrawCommands.shapeControlLabel(control),
            text = DrawCommands.shapeControlText(state, control),
            position = DrawCommands.shapeControlPosition(state, control),
            onChange = { session.dispatch(DrawCommands.setShapeControl(session.current, control, it)) }
        )
    }
    val stroke = DrawCommands.sizeControl()
    DrawingSlider(
        label = "Stroke",
        text = DrawCommands.brushControlText(state, stroke),
        position = DrawCommands.brushControlPosition(state, stroke),
        onChange = { session.dispatch(DrawCommands.setBrushControl(session.current, stroke, it)) }
    )
    if (DrawCommands.canFillShape(state)) {
        DrawingSwitchRow(
            label = "Fill",
            checked = DrawCommands.isShapeFilled(state),
            onChange = { session.dispatch(DrawCommands.setShapeFilled(session.current, it)) }
        )
    }
}

@Composable
private fun DrawingColorPanel(session: DrawingSession, state: DrawEditorState) {
    val colors = SmartTextTokens.colors
    var pickerKey by remember(session) { mutableIntStateOf(0) }
    val current = DrawCommands.currentColor(state)
    val pick: (DrawColor) -> Unit = { color ->
        session.dispatch(DrawCommands.setColor(color))
        pickerKey++
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        DrawingSectionTitle("Colour", DrawCommands.colorHex(current))
        Spacer(modifier = Modifier.weight(1f))
        IconButton(onClick = { session.dispatch(DrawCommands.toggleFavorite(current)) }) {
            Icon(
                imageVector = if (DrawCommands.isFavorite(state, current)) Icons.Default.Star else Icons.Default.StarBorder,
                contentDescription = "Favourite colour",
                tint = colors.accent
            )
        }
    }
    key(pickerKey) {
        ColorSelector(
            initial = current.toCompose(1f),
            showAlpha = true,
            onColorChanged = { picked ->
                val next = picked.toDrawColor()
                if (!DrawCommands.isSameColor(next, DrawCommands.currentColor(session.current))) {
                    session.dispatch(DrawCommands.setColor(next))
                }
            }
        )
    }
    val recent = DrawCommands.recentColors(state)
    if (recent.isNotEmpty()) {
        DrawingSectionTitle("Recent")
        DrawingSwatches(recent, state, pick)
    }
    val favorites = DrawCommands.favoriteColors(state)
    if (favorites.isNotEmpty()) {
        DrawingSectionTitle("Favourites")
        DrawingSwatches(favorites, state, pick)
    }
    DrawCommands.palettes(state).forEach { palette ->
        Row(verticalAlignment = Alignment.CenterVertically) {
            DrawingSectionTitle(palette.name)
            Spacer(modifier = Modifier.weight(1f))
            if (palette.editable) {
                TextButton(onClick = { session.dispatch(DrawCommands.addToPalette(palette.id, current)) }) {
                    Text("Add colour", color = colors.accent)
                }
                TextButton(onClick = { session.dispatch(DrawCommands.removePalette(palette.id)) }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            }
        }
        DrawingSwatches(
            palette.colors,
            state,
            pick,
            onLongClick = if (palette.editable) {
                { color -> session.dispatch(DrawCommands.removeFromPalette(palette.id, color)) }
            } else {
                null
            }
        )
    }
    TextButton(onClick = { session.dispatch(DrawCommands.addPalette(DrawCommands.nextPaletteName(state), listOf(current))) }) {
        Text("New palette", color = colors.accent)
    }
}

@Composable
private fun DrawingPaperPanel(session: DrawingSession, state: DrawEditorState) {
    val advanced = DrawCommands.isAdvanced(state)
    if (DrawCommands.canResizePaper(state)) {
        DrawingSectionTitle("Size", DrawCommands.paperSizeText(state))
        DrawingChips(
            items = DrawCommands.paperSizes(),
            isSelected = { DrawCommands.isPaperSize(state, it) },
            label = DrawCommands::paperLabel,
            onSelect = { session.dispatch(DrawCommands.setPaperSize(session.current, it)) }
        )
        DrawingChips(
            items = DrawCommands.orientations(),
            isSelected = { DrawCommands.isOrientation(state, it) },
            label = DrawCommands::orientationLabel,
            onSelect = { session.dispatch(DrawCommands.setOrientation(session.current, it)) }
        )
        if (advanced) DrawingCustomSize(session, state)
    }
    DrawingSectionTitle("Pattern")
    DrawingChips(
        items = DrawCommands.patterns(state),
        isSelected = { DrawCommands.isPattern(state, it) },
        label = DrawCommands::patternLabel,
        onSelect = { session.dispatch(DrawCommands.setPattern(session.current, it)) }
    )
    if (DrawCommands.hasPattern(state)) {
        DrawingSlider(
            label = "Spacing",
            text = DrawCommands.patternSpacingText(state),
            position = DrawCommands.patternSpacingPosition(state),
            onChange = { session.dispatch(DrawCommands.setPatternSpacingPosition(session.current, it)) }
        )
    }
    DrawingSectionTitle("Background")
    FlowRow {
        DrawCommands.paperBackgrounds().forEach { color ->
            DrawingColorDot(
                color = color,
                selected = DrawCommands.isBackground(state, color),
                onClick = { session.dispatch(DrawCommands.setBackground(session.current, color)) }
            )
        }
    }
    DrawingSwitchRow(
        label = "Transparent",
        checked = DrawCommands.isTransparent(state),
        onChange = { session.dispatch(DrawCommands.setTransparent(session.current, it)) }
    )
    DrawingSectionTitle("Texture")
    DrawingChips(
        items = DrawCommands.textures(),
        isSelected = { DrawCommands.isTexture(state, it) },
        label = DrawCommands::textureLabel,
        onSelect = { session.dispatch(DrawCommands.setTexture(session.current, it)) }
    )
    if (advanced) {
        DrawingSectionTitle("Units")
        DrawingChips(
            items = DrawCommands.units(),
            isSelected = { DrawCommands.isUnit(state, it) },
            label = DrawCommands::unitSymbol,
            onSelect = { session.dispatch(DrawCommands.setUnit(session.current, it)) }
        )
        DrawingSectionTitle("Resolution")
        DrawingChips(
            items = DrawCommands.dpiChoices(),
            isSelected = { DrawCommands.isDpi(state, it) },
            label = DrawCommands::dpiLabel,
            onSelect = { session.dispatch(DrawCommands.setDpi(session.current, it)) }
        )
    }
}

@Composable
private fun DrawingCustomSize(session: DrawingSession, state: DrawEditorState) {
    val colors = SmartTextTokens.colors
    val paper = DrawCommands.paper(state)
    val unit = DrawCommands.paperUnit(state)
    var width by remember(paper) { mutableStateOf(DrawCommands.lengthText(DrawCommands.paperWidthIn(state))) }
    var height by remember(paper) { mutableStateOf(DrawCommands.lengthText(DrawCommands.paperHeightIn(state))) }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = width,
            onValueChange = { width = it },
            label = { Text("Width") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.weight(1f)
        )
        OutlinedTextField(
            value = height,
            onValueChange = { height = it },
            label = { Text("Height") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.weight(1f)
        )
        Text(text = DrawCommands.unitSymbol(unit), color = colors.onSurfaceMuted)
        TextButton(
            onClick = {
                val parsedWidth = DrawCommands.parseLength(width)
                val parsedHeight = DrawCommands.parseLength(height)
                if (parsedWidth != null && parsedHeight != null) {
                    session.dispatch(DrawCommands.setCustomSize(session.current, parsedWidth, parsedHeight, unit))
                }
            }
        ) {
            Text("Apply", color = colors.accent)
        }
    }
}

@Composable
private fun DrawingSettingsPanel(session: DrawingSession, state: DrawEditorState, onDismiss: () -> Unit) {
    DrawingSectionTitle("Mode")
    DrawingChips(
        items = DrawCommands.modes(),
        isSelected = { DrawCommands.isMode(state, it) },
        label = DrawCommands::modeLabel,
        onSelect = { session.dispatch(DrawCommands.setMode(it)) }
    )
    DrawingSwitchRow(
        label = "Stylus only",
        checked = DrawCommands.isStylusOnly(state),
        onChange = { session.dispatch(DrawCommands.setStylusOnly(it)) }
    )
    TextButton(
        enabled = !DrawCommands.isEmpty(state),
        onClick = {
            session.dispatch(DrawCommands.clear())
            onDismiss()
        }
    ) {
        Text("Clear drawing", color = MaterialTheme.colorScheme.error)
    }
}

@Composable
private fun DrawingSectionTitle(title: String, detail: String? = null) {
    val colors = SmartTextTokens.colors
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(text = title, style = TextStyle(color = colors.onSurface, fontSize = 15.sp, fontWeight = FontWeight.SemiBold))
        if (detail != null) {
            Text(text = detail, style = TextStyle(color = colors.onSurfaceMuted, fontSize = 13.sp))
        }
    }
}

@Composable
private fun <T> DrawingChips(
    items: List<T>,
    isSelected: (T) -> Boolean,
    label: (T) -> String,
    onSelect: (T) -> Unit
) {
    val colors = SmartTextTokens.colors
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        items.forEach { item ->
            FilterChip(
                selected = isSelected(item),
                onClick = { onSelect(item) },
                label = { Text(label(item)) },
                colors = FilterChipDefaults.filterChipColors(
                    containerColor = colors.page,
                    labelColor = colors.onSurfaceMuted,
                    selectedContainerColor = colors.accentSoft,
                    selectedLabelColor = colors.onSurface
                )
            )
        }
    }
}

@Composable
private fun DrawingSlider(label: String, text: String, position: Float, onChange: (Float) -> Unit) {
    val colors = SmartTextTokens.colors
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text = label, style = TextStyle(color = colors.onSurface, fontSize = 14.sp))
            Spacer(modifier = Modifier.weight(1f))
            Text(text = text, style = TextStyle(color = colors.onSurfaceMuted, fontSize = 13.sp))
        }
        Slider(
            value = position,
            onValueChange = onChange,
            colors = SliderDefaults.colors(
                thumbColor = colors.accent,
                activeTrackColor = colors.accent,
                inactiveTrackColor = colors.divider
            )
        )
    }
}

@Composable
private fun DrawingSwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    val colors = SmartTextTokens.colors
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(text = label, style = TextStyle(color = colors.onSurface, fontSize = 14.sp))
        Spacer(modifier = Modifier.weight(1f))
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(checkedTrackColor = colors.accent, checkedThumbColor = colors.onAccent)
        )
    }
}

@Composable
private fun DrawingChoiceCard(
    selected: Boolean,
    label: String,
    onClick: () -> Unit,
    content: @Composable () -> Unit
) {
    val colors = SmartTextTokens.colors
    val shape = RoundedCornerShape(10.dp)
    Column(
        modifier = Modifier
            .clip(shape)
            .background(if (selected) colors.accentSoft else colors.page)
            .border(if (selected) 1.5.dp else 1.dp, if (selected) colors.accent else colors.divider, shape)
            .clickable(onClick = onClick)
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(Color.White)) {
            content()
        }
        Text(
            text = label,
            maxLines = 1,
            style = TextStyle(color = if (selected) colors.onSurface else colors.onSurfaceMuted, fontSize = 11.sp)
        )
    }
}

@Composable
private fun DrawingSwatches(
    colors: List<DrawColor>,
    state: DrawEditorState,
    onClick: (DrawColor) -> Unit,
    onLongClick: ((DrawColor) -> Unit)? = null
) {
    val tokens = SmartTextTokens.colors
    FlowRow {
        colors.forEach { color ->
            val selected = DrawCommands.isCurrentColor(state, color)
            Box(
                modifier = Modifier
                    .padding(5.dp)
                    .size(30.dp)
                    .clip(CircleShape)
                    .combinedClickable(
                        onClick = { onClick(color) },
                        onLongClick = onLongClick?.let { remove -> { remove(color) } }
                    )
                    .border(if (selected) 2.5.dp else 1.dp, if (selected) tokens.accent else tokens.divider, CircleShape)
                    .padding(3.dp)
                    .background(color.toCompose(1f), CircleShape)
            )
        }
    }
}
