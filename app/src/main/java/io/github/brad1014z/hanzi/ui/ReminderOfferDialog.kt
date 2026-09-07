package io.github.brad1014z.hanzi.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * The one-time reminder offer (spec 10 guardrail 5; modelled on spec 12's sign-in entry
 * point: "a calm... card after a completed quest... never repeated after dismissal").
 * Shown at most once, right after the very first chest — a real moment of "I did the
 * thing," not a cold ask on first launch. [initialHour]/[initialMinute] should be
 * whatever the clock says right now, so the default is never an arbitrary "6pm for
 * everyone".
 */
@Composable
fun ReminderOfferDialog(
    initialHour: Int,
    initialMinute: Int,
    onEnable: (hour: Int, minute: Int) -> Unit,
    onDismiss: () -> Unit,
) {
    var hour by remember { mutableIntStateOf(initialHour) }
    var minute by remember { mutableIntStateOf(initialMinute) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Want a daily reminder?") },
        text = {
            Column {
                Text(
                    "One quiet notification a day — only on days you haven't practiced " +
                        "yet. Turn it off anytime in Settings.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                ReminderTimePicker(
                    hour = hour,
                    minute = minute,
                    onChange = { h, m -> hour = h; minute = m },
                    modifier = Modifier.padding(top = 16.dp),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onEnable(hour, minute) }) { Text("Turn on reminders") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Not now") }
        },
    )
}
