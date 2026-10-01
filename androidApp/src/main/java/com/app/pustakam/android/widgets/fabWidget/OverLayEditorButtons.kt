package com.app.pustakam.android.widgets.fabWidget
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AttachFile   // 🔧 18-Jul-2026: file-import action
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.app.pustakam.android.MyApplicationTheme
@Composable
fun OverLayEditorButtons(
    modifier: Modifier = Modifier,
    onAddTextField : ()-> Unit ={},
    onCameraAction: () -> Unit = {},
    onRecordMic: () -> Unit = {},
    onArrowButton: ()-> Unit ={},
    onLocation : () -> Unit = {},
    onImportFile : () -> Unit = {},   // 🔧 18-Jul-2026: NEW — opens the import sheet (device/link)
    onDrawing: () -> Unit = {}
) {
    var showArrow = remember { mutableStateOf(false) }
    val cardColors = CardDefaults.cardColors(containerColor = colorScheme.secondary)
    val showOrHide = { showArrow.value = !showArrow.value }
    val iconModifier = Modifier.padding(8.dp)
    val cardElevation = CardDefaults.elevatedCardElevation(defaultElevation = 12.dp)

    Column(
            horizontalAlignment = Alignment.CenterHorizontally, modifier = modifier)
        {
                AnimatedVisibility(
                    visible = showArrow.value, // Controls when content is visible
                    enter =fadeIn(
                        animationSpec = tween(durationMillis = 500)
                    ),
                    exit =fadeOut(
                        animationSpec = tween(durationMillis = 500)
                    )
                )  {
                    Card(
                        shape = CardDefaults.elevatedShape,
                        elevation = CardDefaults.cardElevation(),
                        colors = CardDefaults.cardColors()
                    ) {
                        Column(
                            modifier = Modifier
                                .padding(4.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        )
                        {
                            Card(
                                onClick = {
                                    onArrowButton()
                                    onRecordMic()
                                    showOrHide()
                                },
                                colors = cardColors,
                                elevation = cardElevation
                            )
                            {
                                Icon(
                                    imageVector = Icons.Default.Mic,
                                    contentDescription = "Mic",
                                    modifier = iconModifier
                                )
                            }
                            Card(
                                onClick = {
                                    onArrowButton()
                                    onCameraAction()
                                    showOrHide()
                                },
                                colors = cardColors,
                                elevation = cardElevation
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PhotoCamera,
                                    contentDescription = "Click photo or record video",
                                    modifier = iconModifier
                                )
                            }
                            Card(
                                onClick = {
                                    onArrowButton()
                                    onAddTextField()
                                    showOrHide()
                                },
                                colors = cardColors,
                                elevation = cardElevation
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.TextFields,
                                    contentDescription = "Add new text note",
                                    modifier = iconModifier
                                )
                            }
                            Card(
                                onClick = {
                                    onArrowButton()
                                    onLocation()
                                    showOrHide()
                                },
                                colors = cardColors,
                                elevation = cardElevation
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.LocationOn,
                                    contentDescription = "location",
                                    modifier = iconModifier
                                )
                            }
                            // 🔧 18-Jul-2026: NEW — import files (device picker / any link)
                            Card(
                                onClick = {
                                    onArrowButton()
                                    onImportFile()
                                    showOrHide()
                                },
                                colors = cardColors,
                                elevation = cardElevation
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.AttachFile,
                                    contentDescription = "Import files from device or link",
                                    modifier = iconModifier
                                )
                            }
                            Card(
                                onClick = {
                                    onArrowButton()
                                    onDrawing()
                                    showOrHide()
                                },
                                colors = cardColors,
                                elevation = cardElevation
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Brush,
                                    contentDescription = "Add a drawing page",
                                    modifier = iconModifier
                                )
                            }
                        }
                    }
                }
            Card(
                colors = cardColors,
                elevation = cardElevation,
                onClick = {
                    onArrowButton()
                    showArrow.value = !showArrow.value
                },
                modifier = Modifier.padding(horizontal = 16.dp)
            ) {
                AnimatedContent(
                    targetState = showArrow.value,
                    transitionSpec = {
                        // Simple crossfade and slight scale for the icon itself
                        (fadeIn(animationSpec = tween(200)))
                            .togetherWith(fadeOut(animationSpec = tween(200)))
                    },
                    label = "arrow_icon_animation"
                )  { expanded ->
                Icon(if (expanded) Icons.AutoMirrored.Filled.ArrowBack
                else Icons.AutoMirrored.Filled.ArrowForward
                    , contentDescription = "", modifier = iconModifier
                )
                }
            }
        }
}

@Preview
@Composable
private fun OverLayEditorButtons_Prev() {
    MyApplicationTheme { OverLayEditorButtons() }
}