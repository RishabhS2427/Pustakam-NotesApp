package com.app.pustakam.android.screen.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.app.pustakam.android.theme.typography
import com.app.pustakam.android.widgets.POutLinedTextFieldColors

/**
 * 🆔 Choosing a handle. Availability is checked before saving, always — which is what keeps a 409
 * meaningful, since baseApiCall discards the error body and cannot tell "taken" from "cooldown".
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UsernameSheet(
    username: String,
    hint: String,
    isAvailable: Boolean,
    isNeutral: Boolean,
    suggestions: List<String>,
    canSave: Boolean,
    error: String? = null,
    onUsernameChange: (String) -> Unit,
    onPickSuggestion: (String) -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Choose a username", style = typography.titleMedium, color = colorScheme.onSurface)
        Text(
            text = "This is how people find you. Letters, numbers and single hyphens, 3–30 characters.",
            style = typography.bodySmall,
            color = colorScheme.onSurfaceVariant,
        )

        OutlinedTextField(
            value = username,
            onValueChange = onUsernameChange,
            prefix = { Text("@", style = typography.bodyLarge, color = colorScheme.onSurfaceVariant) },
            singleLine = true,
            colors = POutLinedTextFieldColors(),
            shape = RoundedCornerShape(12.dp),
            // No autocapitalise: a username is canonicalised to lowercase anyway
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.None,
                imeAction = ImeAction.Done,
            ),
            supportingText = {
                if (hint.isNotBlank()) {
                    Text(
                        text = hint,
                        style = typography.labelMedium,
                        color = when {
                            isNeutral -> colorScheme.onSurfaceVariant
                            isAvailable -> colorScheme.primary
                            else -> colorScheme.error
                        },
                    )
                }
            },
            isError = hint.isNotBlank() && !isAvailable && !isNeutral,
            modifier = Modifier.fillMaxWidth(),
        )

        if (suggestions.isNotEmpty()) {
            Text("Try instead", style = typography.labelMedium, color = colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                suggestions.take(3).forEach { suggestion ->
                    Text(
                        text = "@$suggestion",
                        style = typography.labelLarge,
                        color = colorScheme.onSecondaryContainer,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(colorScheme.secondaryContainer)
                            .clickable { onPickSuggestion(suggestion) }
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                    )
                }
            }
        }

        // 🔧 17-Sep-2026 — the screen's error line sits BEHIND this sheet, so a failed check or a
        //   rejected claim used to leave the user staring at a dead button with no explanation.
        if (!error.isNullOrBlank()) {
            Text(text = error, style = typography.labelMedium, color = colorScheme.error)
        }

        Button(onClick = onSave, enabled = canSave, modifier = Modifier.fillMaxWidth()) {
            Text("Save")
        }
    }
}
