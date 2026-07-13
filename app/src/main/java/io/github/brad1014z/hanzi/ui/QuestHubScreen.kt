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
import io.github.brad1014z.hanzi.engine.play.LevelProgress

/** What today's quest holds, precomputed by HanziApp for the hub card. */
data class QuestSummary(
    val dueCount: Int,
    val newCount: Int,
    val backlogWarning: Boolean,
) {
    val allDone: Boolean get() = dueCount == 0 && newCount == 0
}

/**
 * Home — the Quest Hub (spec 07 screen 1, M3 slice): today's quest card, honest
 * XP/level and days-played, the current world's progress toward the next unlock, and
 * doors to the Collection and Settings. TODO(son): the hub is a prime art-direction
 * surface — card art, quest naming ("quest"? "mission"? your call, spec 10/11).
 */
@Composable
fun QuestHubScreen(
    level: LevelProgress,
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
        Text("Hanzi Prototype", style = MaterialTheme.typography.headlineMedium)
        Text(
            text = "Level ${level.level} · ${level.intoLevel}/${level.neededForNext} XP · " +
                "$daysPlayed ${if (daysPlayed == 1) "day" else "days"} played",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        LinearProgressIndicator(
            progress = { (level.intoLevel.toFloat() / level.neededForNext).coerceIn(0f, 1f) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 6.dp, bottom = 20.dp),
        )

        // Today's quest card (always completable, never shaming — spec 10).
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    text = "Today's quest",
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
                            append(" · boss · chest")
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
                    if (nextWorldName != null) {
                        Text(
                            text = "80% Bronze unlocks: $nextWorldName",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 6.dp),
                        )
                    }
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
