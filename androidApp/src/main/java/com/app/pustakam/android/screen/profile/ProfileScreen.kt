package com.app.pustakam.android.screen.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AlternateEmail
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.app.pustakam.android.screen.settings.SettingsCard
import com.app.pustakam.android.screen.settings.SettingsRow
import com.app.pustakam.android.screen.settings.SettingsSection
import com.app.pustakam.android.theme.typography
import com.app.pustakam.android.widgets.CircleIconLoad
import com.app.pustakam.android.widgets.LoadingUI
import com.app.pustakam.android.widgets.POutLinedTextFieldColors
import com.app.pustakam.core.network.toAbsoluteMediaUrl

private const val BIO_MAX_LENGTH = 160

/**
 * 👤 Your own profile. Deliberately built from the SettingsSection / SettingsCard / SettingsRow
 * primitives that already exist — SettingsRow with isOn == null is a chevron row and with a value
 * is a switch, which is exactly the shape an identity list and a privacy toggle need.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ProfileViewModel = viewModel(),
    onPickAvatar: () -> Unit = {},
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val sheetState = rememberModalBottomSheetState()
    // 👇 the Identity summary row jumps to the field that actually edits the name
    val nameFocus = remember { FocusRequester() }

    LaunchedEffect(Unit) { viewModel.load() }

    // 🆔 a server-assigned handle opens the picker once, so nobody is stuck as reader-8f3k2q
    LaunchedEffect(state.shouldPromptForUsername) {
        if (state.shouldPromptForUsername && !state.isEditingUsername) viewModel.openUsernameEditor()
    }

    Scaffold(
        modifier = modifier,
        containerColor = colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Profile", style = typography.titleMedium) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = colorScheme.background),
            )
        },
    ) { padding ->
        if (state.isLoading && state.user == null) {
            LoadingUI(modifier = Modifier.padding(padding))
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            // 🖼️ avatar, name and handle read as one block, so they keep their own tight spacing
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.padding(top = 8.dp),
            ) {
                CircleIconLoad(
                    url = state.user?.avatarUrl.toAbsoluteMediaUrl(),
                    size = 108.dp,
                    onClick = onPickAvatar,
                )
                Text(
                    text = state.user?.displayName().orEmpty(),
                    style = typography.titleLarge,
                    color = colorScheme.onSurface,
                )
                Text(
                    text = state.handle.ifBlank { "No username yet" },
                    style = typography.bodyMedium,
                    color = colorScheme.primary,
                )
            }

            state.error?.let {
                Text(
                    text = it,
                    style = typography.labelMedium,
                    color = colorScheme.error,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp),
                )
            }

            SettingsSection(title = "Identity") {
                SettingsCard {
                    SettingsRow(
                        icon = Icons.Filled.AlternateEmail,
                        tint = colorScheme.primary,
                        title = "Username",
                        subtitle = "How people find you and start a chat",
                        value = state.handle.ifBlank { "Choose one" },
                        onClick = viewModel::openUsernameEditor,
                    )
                    SettingsRow(
                        icon = Icons.Filled.Badge,
                        tint = colorScheme.secondary,
                        title = "Display name",
                        value = state.draftName.ifBlank { "Not set" },
                        onClick = { nameFocus.requestFocus() },
                        isLast = true,
                    )
                }
            }

            SettingsSection(title = "About you") {
                SettingsCard {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = state.draftName,
                            onValueChange = viewModel::onNameChange,
                            label = { Text("Name") },
                            singleLine = true,
                            colors = POutLinedTextFieldColors(),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth().focusRequester(nameFocus),
                        )
                        OutlinedTextField(
                            value = state.draftBio,
                            onValueChange = { if (it.length <= BIO_MAX_LENGTH) viewModel.onBioChange(it) },
                            label = { Text("Bio") },
                            supportingText = { Text("${state.draftBio.length}/$BIO_MAX_LENGTH") },
                            colors = POutLinedTextFieldColors(),
                            shape = RoundedCornerShape(12.dp),
                            maxLines = 3,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Button(
                            onClick = viewModel::saveDetails,
                            enabled = state.hasUnsavedDetails && !state.isSaving,
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text(if (state.isSaving) "Saving…" else "Save") }
                    }
                }
            }

            SettingsSection(title = "Discovery") {
                SettingsCard {
                    // 🔒 the copy must not overstate this: it governs SEARCH only
                    SettingsRow(
                        icon = Icons.Filled.Visibility,
                        tint = colorScheme.tertiary,
                        title = "Let people find me",
                        subtitle = "Anyone with your exact username can always open your profile",
                        isOn = state.isDiscoverable,
                        onCheckedChange = viewModel::setDiscoverable,
                        isLast = true,
                    )
                }
            }
        }
    }

    if (state.isEditingUsername) {
        ModalBottomSheet(
            onDismissRequest = viewModel::closeUsernameEditor,
            sheetState = sheetState,
            containerColor = colorScheme.surface,
        ) {
            UsernameSheet(
                username = state.draftUsername,
                hint = state.usernameHint,
                isAvailable = state.availability?.available == true,
                isNeutral = state.usernameHintIsNeutral,
                suggestions = state.availability?.suggestions.orEmpty(),
                canSave = state.canSaveUsername,
                error = state.error,
                onUsernameChange = viewModel::onUsernameChange,
                onPickSuggestion = viewModel::onUsernameChange,
                onSave = viewModel::saveUsername,
            )
        }
    }
}
