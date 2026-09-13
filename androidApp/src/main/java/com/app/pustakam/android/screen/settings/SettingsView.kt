package com.app.pustakam.android.screen.settings

import com.app.pustakam.android.theme.GranthCopper
import com.app.pustakam.android.theme.GranthForest
import com.app.pustakam.android.theme.GranthGold
import com.app.pustakam.android.theme.GranthIndigo
import com.app.pustakam.android.theme.GranthIvory
import com.app.pustakam.android.theme.GranthSaffron
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.MenuBook
import com.app.pustakam.android.screen.notebookReader.ReadingMode
import androidx.compose.material.icons.filled.Accessibility
import androidx.compose.material.icons.filled.CloudQueue
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.NightlightRound
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.SystemUpdateAlt
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.app.pustakam.android.MyApplicationTheme
import com.app.pustakam.android.screen.navigation.Route
import com.app.pustakam.android.widgets.CircleIconLoad
import com.app.pustakam.core.network.toAbsoluteMediaUrl
import com.app.pustakam.android.theme.ThemeMode
import com.app.pustakam.android.theme.ThemeTilePicker
import com.app.pustakam.android.theme.radiusLg
import com.app.pustakam.android.theme.radiusMd

// 🎨 22-Jul-2026 — Granth spec §6 Settings, matched to iOS SettingsView.swift. Replaces the
//   placeholder Text("Settings") with the profile header + grouped sections. Appearance is live-wired
//   through SettingsViewModel → shared DataStore; the remaining rows are presentational until their
//   features land.
@Composable
fun SettingsScreen(
    // 🔧 22-Jul-2026 — was `(Route)->Unit` importing okhttp3.Route by mistake; navigateTo takes a String
    onNavigate: (String) -> Unit,
    onBack: () -> Unit = {},
    viewModel: SettingsViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    SettingsContent(
        state = state,
        onThemeModeSelected = viewModel::onThemeModeSelected,
        onMarkdownShortcutsChange = viewModel::onMarkdownShortcutsChange,
        onFocusModeDimmingChange = viewModel::onFocusModeDimmingChange,
        onCloudSyncChange = viewModel::onCloudSyncChange,
        onAutoBackupChange = viewModel::onAutoBackupChange,
        onOfflineModeChange = viewModel::onOfflineModeChange,
        onReadingModeChange = viewModel::onReadingModeChange,
        onOpenProfile = { onNavigate(Route.Profile) },
        onLogout = viewModel::logout
    )
}

// 🎨 22-Jul-2026 — stateless body so previews can drive it with sample data (no ViewModel/Koin needed).
@Composable
fun SettingsContent(
    state: SettingsUiState,
    onThemeModeSelected: (ThemeMode) -> Unit = {},
    onMarkdownShortcutsChange: (Boolean) -> Unit = {},
    onFocusModeDimmingChange: (Boolean) -> Unit = {},
    onCloudSyncChange: (Boolean) -> Unit = {},
    onAutoBackupChange: (Boolean) -> Unit = {},
    onOfflineModeChange: (Boolean) -> Unit = {},
    onReadingModeChange: (Boolean) -> Unit = {},
    onOpenProfile: () -> Unit = {},
    onLogout : () -> Unit = {}
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {

        SettingsProfileHeader(
            initial = state.profileInitial,
            name = state.profileName,
            subtitle = state.profileSubtitle,
            badge = state.profileBadge,
            avatarUrl = state.user?.avatarUrl.toAbsoluteMediaUrl(),
            onClick = onOpenProfile
        )

        // 🎨 Appearance — the only live section
        SettingsSection(title = "Appearance") {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                ThemeTilePicker(
                    selection = state.themeMode,
                    onSelect = onThemeModeSelected
                )
                Text(
                    text = appearanceHint(state.themeMode),
                    style = MaterialTheme.typography.bodySmall,
                    color = colorScheme.outline
                )
            }
        }

        SettingsSection(title = "Editor & Reading") {
            SettingsCard {
                SettingsRow(
                    icon = Icons.Default.TextFields, tint = GranthSaffron,
                    title = "Editor font", subtitle = state.editorFontSubtitle,
                    value = state.editorFont
                )
                SettingsRow(
                    icon = Icons.Default.Keyboard, tint = GranthForest,
                    title = "Markdown shortcuts", subtitle = "Auto-format as you type",
                    isOn = state.markdownShortcuts, onCheckedChange = onMarkdownShortcutsChange
                )
                SettingsRow(
                    icon = Icons.Default.NightlightRound, tint = GranthCopper,
                    title = "Focus mode dimming", subtitle = "Fade inactive paragraphs",
                    isOn = state.focusModeDimming, onCheckedChange = onFocusModeDimmingChange
                )
                // 📖 23-Jul-2026: NEW — reader layout. Same persisted value as the reader's toolbar
                //   toggle, so changing it in either place updates the other.
                SettingsRow(
                    icon = Icons.AutoMirrored.Filled.MenuBook, tint = GranthGold,
                    title = "Scrolling mode",
                    subtitle = if (state.readingMode == ReadingMode.SCROLL)
                        "Documents scroll continuously" else "Documents turn like book pages",
                    isOn = state.readingMode == ReadingMode.SCROLL,
                    onCheckedChange = onReadingModeChange
                )
                SettingsRow(
                    icon = Icons.Default.History, tint = GranthIndigo,
                    title = "Version history", subtitle = state.versionHistorySubtitle,
                    value = "", isLast = true
                )
            }
        }

        SettingsSection(title = "Sync & Backup") {
            SettingsCard {
                SettingsRow(
                    icon = Icons.Default.CloudQueue, tint = GranthForest,
                    title = "Cloud sync", subtitle = state.cloudSyncSubtitle,
                    isOn = state.cloudSync, onCheckedChange = onCloudSyncChange
                )
                SettingsRow(
                    icon = Icons.Default.SystemUpdateAlt, tint = GranthSaffron,
                    title = "Auto backup", subtitle = "Daily · encrypted",
                    isOn = state.autoBackup, onCheckedChange = onAutoBackupChange
                )
                SettingsRow(
                    icon = Icons.Default.SwapHoriz, tint = GranthCopper,
                    title = "Offline mode", subtitle = "Keep all notes on device",
                    isOn = state.offlineMode, onCheckedChange = onOfflineModeChange, isLast = true
                )
            }
        }

        SettingsSection(title = "More") {
            SettingsCard {
                SettingsRow(
                    icon = Icons.Default.Language, tint = GranthIndigo,
                    title = "Language", value = state.language
                )
                SettingsRow(
                    icon = Icons.Default.Accessibility, tint = GranthGold,
                    title = "Accessibility", value = ""
                )
                SettingsRow(
                    icon = Icons.Default.Lock, tint = GranthCopper,
                    title = "Privacy & Security", subtitle = "App lock · Face ID", value = ""
                )
                SettingsRow(
                    icon = Icons.Default.Settings, tint = GranthForest,
                    title = "Developer options", value = "",
                )
                SettingsRow(
                    icon = Icons.Default.Settings, tint = Color.Red,
                    title = "Logout", value = "",
                    onClick = onLogout
                )
            }
        }
    }
}

// 🎨 tells the user what the current pick actually does (matches iOS copy)
private fun appearanceHint(mode: ThemeMode): String = when (mode) {
    ThemeMode.SYSTEM -> "Follows your device appearance, including scheduled Dark Mode."
    ThemeMode.LIGHT -> "Always uses the warm parchment palette."
    ThemeMode.DARK -> "Always uses the warm dark-ink palette."
    ThemeMode.AMOLED -> "True black surfaces — saves power on OLED displays."
}

// MARK: - Building blocks

// 🎨 22-Jul-2026 — uppercase group label + content, matching spec §6 grouping.
@Composable
fun SettingsSection(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = title.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.2.sp,
            color = colorScheme.outline,
            modifier = Modifier.padding(horizontal = 4.dp)
        )
        content()
    }
}

// 🎨 22-Jul-2026 — rounded surface that hosts a run of SettingsRow.
@Composable
fun SettingsCard(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(radiusMd))
            .background(colorScheme.surface)
            .border(1.dp, colorScheme.outlineVariant, RoundedCornerShape(radiusMd))
    ) { content() }
}

// 🎨 22-Jul-2026 — one settings row: tinted icon, title/subtitle, then a switch OR a value chevron.
@Composable
fun SettingsRow(
    icon: ImageVector,
    tint: Color,
    title: String,
    subtitle: String? = null,
    value: String? = null,
    isOn: Boolean? = null,
    onCheckedChange: (Boolean) -> Unit = {},
    onClick: () -> Unit = {},
    isLast: Boolean = false
) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .then(if (isOn == null) Modifier.clickable(onClick = onClick) else Modifier)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(9.dp))
                    .background(tint.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = tint,
                    modifier = Modifier.size(18.dp)
                )
            }

            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    color = colorScheme.onSurface
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = colorScheme.outline,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            if (isOn != null) {
                Switch(
                    checked = isOn,
                    onCheckedChange = onCheckedChange,
                    colors = SwitchDefaults.colors(
                        checkedTrackColor = colorScheme.primary,
                        checkedThumbColor = GranthIvory
                    )
                )
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (!value.isNullOrEmpty()) {
                        Text(
                            text = value,
                            style = MaterialTheme.typography.bodySmall,
                            color = colorScheme.outline
                        )
                        Spacer(Modifier.width(4.dp))
                    }
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        tint = colorScheme.outline,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        // 🎨 inset divider aligns under the text, not the icon (matches iOS 60pt leading inset)
        if (!isLast) {
            HorizontalDivider(
                modifier = Modifier.padding(start = 60.dp),
                color = colorScheme.outlineVariant
            )
        }
    }
}

// 🎨 22-Jul-2026 — profile header (spec §6): gradient avatar, identity, PRO badge.
@Composable
fun SettingsProfileHeader(
    initial: String,
    name: String,
    subtitle: String,
    badge: String? = null,
    avatarUrl: String? = null,
    onClick: () -> Unit = {}
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(radiusLg))
            .background(colorScheme.surface)
            .border(1.dp, colorScheme.outlineVariant, RoundedCornerShape(radiusLg))
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 🖼️ the gradient initial is the placeholder, so a user with no picture still reads as themselves
        if (avatarUrl.isNullOrBlank()) {
            Box(
                modifier = Modifier
                    .size(54.dp)
                    .clip(CircleShape)
                    .background(Brush.linearGradient(listOf(GranthIndigo, GranthForest))),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = initial,
                    style = MaterialTheme.typography.displaySmall,
                    color = Color.White
                )
            }
        } else {
            CircleIconLoad(url = avatarUrl, size = 54.dp, onClick = onClick)
        }

        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                text = name,
                style = MaterialTheme.typography.headlineSmall,
                color = colorScheme.onSurface
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = colorScheme.outline,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        if (badge != null) {
            Text(
                text = badge,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Black,
                letterSpacing = 0.8.sp,
                color = Color(0xFF7E3F20),
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(Brush.linearGradient(listOf(GranthSaffron, GranthGold)))
                    .padding(horizontal = 9.dp, vertical = 5.dp)
            )
        }
    }
}

// MARK: - Previews (sample data)

// 🎨 22-Jul-2026 — sample-data previews, one per theme mode (project convention).
private val sampleSettingsState = SettingsUiState(themeMode = ThemeMode.LIGHT)

@Preview(name = "Settings — Light", showBackground = true, heightDp = 1200)
@Composable
private fun SettingsLightPreview() {
    MyApplicationTheme(isDarkTheme = false) {
        Surface(color = colorScheme.background) {
            SettingsContent(state = sampleSettingsState)
        }
    }
}

@Preview(name = "Settings — Dark", showBackground = true, heightDp = 1200)
@Composable
private fun SettingsDarkPreview() {
    MyApplicationTheme(isDarkTheme = true) {
        Surface(color = colorScheme.background) {
            SettingsContent(state = sampleSettingsState.copy(themeMode = ThemeMode.DARK))
        }
    }
}

@Preview(name = "Settings — AMOLED", showBackground = true, heightDp = 1200)
@Composable
private fun SettingsAmoledPreview() {
    MyApplicationTheme(isDarkTheme = true, isAmoled = true) {
        Surface(color = colorScheme.background) {
            SettingsContent(state = sampleSettingsState.copy(themeMode = ThemeMode.AMOLED))
        }
    }
}
