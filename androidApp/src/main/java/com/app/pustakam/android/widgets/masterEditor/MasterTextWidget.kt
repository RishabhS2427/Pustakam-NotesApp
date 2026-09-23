package com.app.pustakam.android.widgets.masterEditor

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors

import androidx.compose.material3.Text

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import com.app.pustakam.android.widgets.smartText.SmartTextTokens
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalFocusManager
import com.app.pustakam.android.widgets.smartText.LocalSmartTextToolbar
import com.app.pustakam.android.widgets.smartText.SmartTextSheet
import com.app.pustakam.android.widgets.smartText.SmartTextSheetHost
import com.app.pustakam.core.model.models.RichTextMetadata
import com.app.pustakam.core.richtext.codec.RichTextCodec
import com.app.pustakam.core.richtext.model.RichDocument
import com.app.pustakam.core.richtext.presentation.SmartTextCommands
import com.app.pustakam.core.richtext.presentation.ToolbarAction
import java.util.UUID
import com.app.pustakam.core.richtext.master.model.CanvasNode
import com.app.pustakam.core.richtext.master.presentation.CanvasCommands
import com.app.pustakam.core.richtext.master.presentation.MasterTextCommands
import com.app.pustakam.core.richtext.master.presentation.MasterTextIntent
import com.app.pustakam.core.richtext.master.presentation.MasterTextReducer
import com.app.pustakam.core.richtext.master.presentation.MasterTextState

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MasterTextWidget(
    state: MasterTextState,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester = remember { FocusRequester() },
    readOnly: Boolean = false,
    scale: Float = 1f,
    scrollable: Boolean = true,
    minLines: Int = 1,
    placeholder: String = "Keep your thoughts alive.",
    onIntent: (MasterTextIntent) -> Unit,
    keyboardInsetPx: Float = 0f,
    shouldFocus: Boolean = false,
    // 📄 a canvas page makes the keyboard room on its own content and only the field being
    //   edited may scroll it — with several fields on one page, each doing both fought
    reserveKeyboardRoom: Boolean = true,
    revealCaret: Boolean = true,
    onFocusChanged: (Boolean) -> Unit = {}
) {
    val colors = SmartTextTokens.colors
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer()
    val baseSize: TextUnit = SmartTextTokens.baseFontSize * scale * CanvasNode.BASE_FONT_SCALE

    var fieldValue by remember { mutableStateOf(TextFieldValue(state.text)) }
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    var caretRect by remember { mutableStateOf<Rect?>(null) }
    val caretRevealer = remember { BringIntoViewRequester() }

    if (fieldValue.text != state.text) {
        fieldValue = TextFieldValue(
            text = state.text,
            selection = TextRange(
                state.selection.normalizedStart.coerceIn(0, state.text.length),
                state.selection.normalizedEnd.coerceIn(0, state.text.length)
            )
        )
    }

    val rendered = remember(state.document, state.text, colors, baseSize) {
        MasterTextRenderer.annotate(state, colors, baseSize)
    }

    val selectionColors = TextSelectionColors(
        handleColor = colors.selectionHandle,
        backgroundColor = colors.accent.copy(alpha = 0.24f)
    )

    val minHeight = with(density) { (baseSize.toPx() * LINE_HEIGHT * minLines).toDp() }

    // room to scroll the end of the text clear of the keyboard, and the caret clear of that
    val trailingRoom = with(density) {
        if (!reserveKeyboardRoom || keyboardInsetPx <= 0f) 0.dp
        else (keyboardInsetPx + CanvasCommands.caretRevealPadding(baseSize.toPx() * LINE_HEIGHT)).toDp()
    }

    LaunchedEffect(shouldFocus) {
        if (shouldFocus) runCatching { focusRequester.requestFocus() }
    }

    LaunchedEffect(caretRect, keyboardInsetPx, revealCaret) {
        if (!revealCaret) return@LaunchedEffect
        val caret = caretRect ?: return@LaunchedEffect
        val clearance = CanvasCommands.caretRevealPadding(caret.height) + keyboardInsetPx
        caretRevealer.bringIntoView(
            Rect(caret.left, caret.top, caret.right, caret.bottom + clearance)
        )
    }

    val checkboxTaps = Modifier.pointerInput(state.document, readOnly) {
        if (readOnly) return@pointerInput
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            val result = layout ?: return@awaitEachGesture
            val hit = MasterTextRenderer
                .markerHitOffset(state, result, down.position, density)
                ?: return@awaitEachGesture
            down.consume()
            val up = waitForUpOrCancellation(PointerEventPass.Initial)
            if (up != null) {
                up.consume()
                onIntent(MasterTextCommands.toggleChecked(hit))
            }
        }
    }

    Box(
        modifier = (if (scrollable) {
            modifier.fillMaxSize().verticalScroll(rememberScrollState())
        } else {
            modifier.fillMaxWidth().heightIn(min = minHeight)
        }).then(checkboxTaps).padding(bottom = trailingRoom)
    ) {
        if (state.text.isEmpty()) {
            Text(
                text = placeholder,
                style = TextStyle(color = colors.onSurfaceMuted, fontSize = baseSize),
                modifier = Modifier.padding(top = 2.dp)
            )
        }

        CompositionLocalProvider(LocalTextSelectionColors provides selectionColors) {
            BasicTextField(
                value = fieldValue,
                onValueChange = { newValue ->
                    fieldValue = newValue
                    if (newValue.text != state.text) {
                        onIntent(
                            MasterTextCommands.edit(
                                text = newValue.text,
                                selectionStart = newValue.selection.start,
                                selectionEnd = newValue.selection.end
                            )
                        )
                    } else {
                        onIntent(
                            MasterTextCommands.selectionChanged(
                                start = newValue.selection.start,
                                end = newValue.selection.end
                            )
                        )
                    }
                },
                readOnly = readOnly,
                textStyle = TextStyle(color = colors.onSurface, fontSize = baseSize),
                cursorBrush = SolidColor(colors.accent),
                keyboardOptions = KeyboardOptions.Default,
                onTextLayout = {
                    layout = it
                    caretRect = runCatching {
                        it.getCursorRect(
                            state.selection.normalizedEnd.coerceIn(0, state.text.length)
                        )
                    }.getOrNull()
                },
                visualTransformation = remember(rendered) {
                    VisualTransformation { TransformedText(rendered, OffsetMapping.Identity) }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .bringIntoViewRequester(caretRevealer)
                    .focusRequester(focusRequester)
                    .onFocusChanged { focus ->
                        if (focus.isFocused) {
                            val end = state.text.length
                            fieldValue = fieldValue.copy(selection = TextRange(end))
                            onIntent(MasterTextCommands.selectionChanged(end, end))
                        }
                        onFocusChanged(focus.isFocused)
                    }
                    .drawBehind {
                        layout?.let { result ->
                            MasterTextRenderer.drawMarkers(
                                scope = this,
                                state = state,
                                layout = result,
                                measurer = measurer,
                                colors = colors,
                                baseSize = baseSize,
                                density = density
                            )
                        }
                    }
                    .pointerInput(state.document, readOnly) {
                        detectTapGestures(
                            onDoubleTap = { position ->
                                layout?.let { result ->
                                    onIntent(
                                        MasterTextCommands.selectWord(
                                            result.getOffsetForPosition(position)
                                        )
                                    )
                                }
                            },
                            onLongPress = { position ->
                                layout?.let { result ->
                                    onIntent(
                                        MasterTextCommands.selectParagraph(
                                            result.getOffsetForPosition(position)
                                        )
                                    )
                                }
                            }
                        )
                    }
            )
        }
    }
}

@Composable
fun MasterTextContentWidget(
    text: String,
    metadata: RichTextMetadata?,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester = remember { FocusRequester() },
    readOnly: Boolean = false,
    showKeyboardToolbar: Boolean = true,
    onDocumentChange: (RichDocument) -> Unit
) {
    val focusManager = LocalFocusManager.current
    val document = remember(text, metadata) { RichTextCodec.documentFrom(text, metadata) }

    var state by remember { mutableStateOf(MasterTextState.of(document)) }
    var lastEmitted by remember { mutableStateOf(document) }
    var isFocused by remember { mutableStateOf(false) }
    var sheet by remember { mutableStateOf(SmartTextSheet.NONE) }

    if (document != lastEmitted && document != state.document) {
        state = MasterTextState.of(document)
        lastEmitted = document
    }

    fun dispatch(intent: MasterTextIntent) {
        val next = MasterTextReducer.reduce(state, intent)
        state = next
        if (next.document != lastEmitted) {
            lastEmitted = next.document
            onDocumentChange(next.document)
        }
    }

    fun handle(action: ToolbarAction) {
        when {
            SmartTextCommands.isMore(action) ->
                dispatch(MasterTextCommands.setToolbarExpanded(!state.isToolbarExpanded))

            SmartTextCommands.isDismiss(action) -> focusManager.clearFocus()

            else -> {
                val intent = MasterTextCommands.forToolbar(action)
                if (intent != null) dispatch(intent)
                else sheet = SmartTextSheet.fromIndex(MasterTextCommands.sheetIndex(action))
            }
        }
    }

    val toolbarHost = LocalSmartTextToolbar.current
    val widgetId = rememberSaveable { UUID.randomUUID().toString() }

    LaunchedEffect(isFocused, state.toolbar, state.isToolbarExpanded, state.canUndo, state.canRedo) {
        if (toolbarHost == null) return@LaunchedEffect
        if (isFocused && !readOnly && showKeyboardToolbar) {
            toolbarHost.publish(
                ownerId = widgetId,
                toolbar = state.toolbar,
                expanded = state.isToolbarExpanded,
                canUndo = state.canUndo,
                canRedo = state.canRedo,
                onAction = ::handle
            )
        } else {
            toolbarHost.release(widgetId)
        }
    }
    DisposableEffect(widgetId) { onDispose { toolbarHost?.release(widgetId) } }

    MasterTextWidget(
        state = state,
        modifier = modifier,
        focusRequester = focusRequester,
        readOnly = readOnly,
        scrollable = false,
        onIntent = ::dispatch,
        onFocusChanged = { isFocused = it }
    )

    SmartTextSheetHost(
        sheet = sheet,
        currentStyle = state.toolbar.paragraphStyle,
        currentAlign = state.toolbar.align,
        currentFontSize = state.toolbar.fontSize,
        currentLink = state.toolbar.link,
        searchQuery = "",
        replacement = "",
        matchCount = 0,
        currentMatch = 0,
        tableRowCount = 0,
        tableColumnCount = 0,
        onDismiss = { sheet = SmartTextSheet.NONE },
        onStyle = { dispatch(MasterTextCommands.setParagraphStyle(it)); sheet = SmartTextSheet.NONE },
        onAlign = { dispatch(MasterTextCommands.setAlignment(it)); sheet = SmartTextSheet.NONE },
        onColor = {
            val intent = if (sheet == SmartTextSheet.BACKGROUND_COLOR) {
                MasterTextCommands.setBackgroundColor(it)
            } else {
                MasterTextCommands.setTextColor(it)
            }
            dispatch(intent)
            sheet = SmartTextSheet.NONE
        },
        onFontSize = { dispatch(MasterTextCommands.setFontSize(it)); sheet = SmartTextSheet.NONE },
        onLink = { dispatch(MasterTextCommands.setLink(it)); sheet = SmartTextSheet.NONE },
        onTable = { sheet = SmartTextSheet.NONE },
        onInsertTable = { _, _ -> sheet = SmartTextSheet.NONE },
        onSearchQuery = {},
        onReplacement = {},
        onFindNext = {},
        onFindPrevious = {},
        onReplaceCurrent = {},
        onReplaceAll = {}
    )
}

private const val LINE_HEIGHT = 0.5f
