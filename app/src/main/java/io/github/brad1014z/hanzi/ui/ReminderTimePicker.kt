package io.github.brad1014z.hanzi.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** "6:00 PM", "12:15 AM" — the same friendly form the offer sheet and Settings share. */
fun formatTimeOfDay(hour: Int, minute: Int): String {
    val period = if (hour < 12) "AM" else "PM"
    val hour12 = when (val h = hour % 12) { 0 -> 12; else -> h }
    return "$hour12:${minute.toString().padStart(2, '0')} $period"
}

/**
 * A simple hour/quarter-hour stepper (spec 10: "a user-chosen time"). Deliberately not a
 * full clock dial — this app's whole UI is stepper-and-button, not dialogs-on-dialogs
 * (see the daily-cap and facilitator-label pickers in Settings), and quarter-hour
 * granularity is all a habit reminder needs.
 */
@Composable
fun ReminderTimePicker(
    hour: Int,
    minute: Int,
    onChange: (hour: Int, minute: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Text(formatTimeOfDay(hour, minute), style = MaterialTheme.typography.titleMedium)
        Row(
            modifier = Modifier.padding(start = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            OutlinedButton(onClick = { onChange((hour + 23) % 24, minute) }) { Text("Hour −") }
            OutlinedButton(onClick = { onChange((hour + 1) % 24, minute) }) { Text("Hour +") }
            OutlinedButton(onClick = { onChange(hour, (minute + 45) % 60) }) { Text("Min −") }
            OutlinedButton(onClick = { onChange(hour, (minute + 15) % 60) }) { Text("Min +") }
        }
    }
}
