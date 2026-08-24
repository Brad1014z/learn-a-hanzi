package io.github.brad1014z.hanzi.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** What today's quest holds, precomputed by HanziApp for the hub card. */
data class QuestSummary(
    val dueCount: Int,
    val newCount: Int,
    val backlogWarning: Boolean,
) {
    val allDone: Boolean get() = dueCount == 0 && newCount == 0
}

/**
 * Home — the Quest Hub (spec 07 screen 1): today's quest card, days practiced, the
 * current world's progress toward the next unlock, and doors to Collection and Settings.
 * TODO(son): the hub is a prime art-direction surface — card art and quest naming.
 */
@Composable
fun QuestHubScreen(
    daysPlayed: Int,
    quest: QuestSummary,
    currentWorldName: String?,
    currentWorldMastery: Double,
    nextWorldName: String?,
    onStartQuest: () -> Unit,
    onCollection: () -> Unit,
    onSettings: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
    ) {
        Text("Inkbook", style = MaterialTheme.typography.headlineMedium)
        Text(
            text = "$daysPlayed ${if (daysPlayed == 1) "day" else "days"} practiced",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(20.dp))

        // Today's quest card (always completable, never shaming — spec 10).
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    text = "Shape Shift",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = when {
                        quest.allDone -> "All done for today! 🎉  Free practice awaits in the collection."
                        else -> buildString {
                            if (quest.dueCount > 0) append("${quest.dueCount} to review")
                            if (quest.dueCount > 0 && quest.newCount > 0) append(" · ")
                            if (quest.newCount > 0) append("${quest.newCount} new")
                            append(" · memory check")
                        }
                    },
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(top = 4.dp),
                )
                if (quest.backlogWarning) {
                    Text(
                        text = "Big review day — new characters wait until the pile shrinks.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
                if (!quest.allDone) {
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = onStartQuest, modifier = Modifier.fillMaxWidth()) {
                        Text("Start quest", fontSize = 18.sp)
                    }
                }
            }
        }

        // Current world strip (spec 07): mastery toward the next unlock.
        if (currentWorldName != null) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "World: $currentWorldName",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        Spacer(Modifier.weight(1f))
                        Text(
                            text = "${(currentWorldMastery * 100).toInt()}%",
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }
                    LinearProgressIndicator(
                        progress = { currentWorldMastery.toFloat().coerceIn(0f, 1f) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                    )
                    if (nextWorldName != null) Text(
                        text = "Keep practicing to open $nextWorldName.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = onCollection, modifier = Modifier.weight(1f)) {
                Text("Collection")
            }
            OutlinedButton(onClick = onSettings, modifier = Modifier.weight(1f)) {
                Text("Settings")
            }
        }
    }
}
