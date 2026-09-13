package com.app.pustakam.android.widgets.chat

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.app.pustakam.android.theme.typography
import com.app.pustakam.core.common.util.getCurrentTimestamp
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

private const val DAY_MILLIS = 24 * 60 * 60 * 1000L
private const val DOT_COUNT = 3
private const val DOT_CYCLE_MILLIS = 900
private val MONTHS = listOf(
    "Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"
)

/** "14:05". Deliberately 24-hour so it never has to guess the user's locale conventions. */
fun Long.toClockTime(): String {
    if (this <= 0) return ""
    val time = Instant.fromEpochMilliseconds(this).toLocalDateTime(TimeZone.currentSystemDefault())
    return "${time.hour.padded()}:${time.minute.padded()}"
}

/** "Today", "Yesterday", then "12 Aug" — the separator above a run of messages. */
fun Long.toDayLabel(): String {
    if (this <= 0) return ""
    val zone = TimeZone.currentSystemDefault()
    val date = Instant.fromEpochMilliseconds(this).toLocalDateTime(zone).date
    val today = Instant.fromEpochMilliseconds(getCurrentTimestamp()).toLocalDateTime(zone).date
    val yesterday = Instant.fromEpochMilliseconds(getCurrentTimestamp() - DAY_MILLIS).toLocalDateTime(zone).date

    return when (date) {
        today -> "Today"
        yesterday -> "Yesterday"
        else -> date.readable()
    }
}

/** The list row's right-hand timestamp: the clock today, the date before that. */
fun Long.toInboxTimestamp(): String {
    if (this <= 0) return ""
    return if (toDayLabel() == "Today") toClockTime() else toDayLabel()
}

private fun LocalDate.readable(): String = "$dayOfMonth ${MONTHS[monthNumber - 1]}"

private fun Int.padded(): String = if (this < 10) "0$this" else "$this"

/** The three dots under a name that is typing, or inside a half-written assistant reply. */
@Composable
fun TypingDots(tint: Color = colorScheme.onSurfaceVariant, modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "typing")
    Row(
        modifier = modifier.padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(DOT_COUNT) { index ->
            val alpha by transition.animateFloat(
                initialValue = 0.25f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = DOT_CYCLE_MILLIS, delayMillis = index * 120),
                    repeatMode = RepeatMode.Reverse,
                ),
                label = "dot$index",
            )
            androidx.compose.foundation.layout.Box(
                modifier = Modifier.size(6.dp).alpha(alpha).background(tint, CircleShape)
            )
        }
    }
}

/** "Aarav is typing…" above the composer. */
@Composable
fun TypingIndicator(names: List<String>, modifier: Modifier = Modifier) {
    if (names.isEmpty()) return
    val label = when (names.size) {
        1 -> "${names.first()} is typing"
        else -> "${names.size} people are typing"
    }
    Row(
        modifier = modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(text = label, style = typography.labelSmall, color = colorScheme.onSurfaceVariant)
        TypingDots()
    }
}
