package io.github.brad1014z.hanzi.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import io.github.brad1014z.hanzi.data.CurriculumRow
import io.github.brad1014z.hanzi.data.RoomContentRepository
import io.github.brad1014z.hanzi.engine.play.Rank
import io.github.brad1014z.hanzi.engine.play.RankState
import io.github.brad1014z.hanzi.engine.play.Ranks
import io.github.brad1014z.hanzi.engine.progress.CharacterProgress

/**
 * The character grid, now grouped into worlds (M2 — spec 04/10): every HSK 1 character
 * in teaching order, sectioned by its curated world. TODO(son, S3): this is still "The
 * Shelf" seed — the world headers, tile art, and practiced-state look are yours.
 * (World *unlock gating* arrives with mastery in M3; for now every world is open.)
 */
@Composable
fun CharacterGridScreen(
    worlds: List<RoomContentRepository.World>,
    progress: Map<String, CharacterProgress> = emptyMap(),
    unlockedWorlds: Int = Int.MAX_VALUE,
    onBack: (() -> Unit)? = null,
    onCharacterTap: (String) -> Unit,
) {
    val total = worlds.sumOf { it.characters.size }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        androidx.compose.foundation.layout.Row(verticalAlignment = Alignment.CenterVertically) {
            if (onBack != null) {
                androidx.compose.material3.TextButton(onClick = onBack) { Text("‹ Home") }
            }
            Text(
                text = "Collection",
                style = MaterialTheme.typography.headlineMedium,
            )
        }
        Text(
            text = "$total characters in ${worlds.size} worlds · ${progress.size} practiced",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 8.dp),
        )
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 72.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            worlds.forEachIndexed { worldIndex, world ->
                item(key = "world-${world.id}", span = { GridItemSpan(maxLineSpan) }) {
                    WorldHeader(
                        name = world.name,
                        locked = worldIndex >= unlockedWorlds,
                        previousName = worlds.getOrNull(worldIndex - 1)?.name,
                        mastery = Ranks.masteryFraction(world.characters.map { progress[it.character] }),
                    )
                }
                items(world.characters, key = { it.character }) { row ->
                    CharacterTile(
                        row = row,
                        progress = progress[row.character],
                        onTap = { onCharacterTap(row.character) },
                    )
                }
            }
        }
    }
}

@Composable
private fun WorldHeader(name: String, locked: Boolean, previousName: String?, mastery: Double) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(top = 12.dp, bottom = 2.dp),
    ) {
        Text(
            text = if (locked) "🔒 $name" else name,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.padding(4.dp))
        Text(
            // Locked gates the guided track only — tiles stay browsable (spec 04).
            text = if (locked && previousName != null) {
                "unlocks at 80% Bronze in $previousName · free practice ok"
            } else {
                "${(mastery * 100).toInt()}% Bronze+"
            },
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun CharacterTile(row: CurriculumRow, progress: CharacterProgress?, onTap: () -> Unit) {
    // Rank colors are placeholders — collection art is the co-designer's M5 pass.
    val rank: RankState = Ranks.of(progress)
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = when {
            rank.rank != Rank.NONE -> MaterialTheme.colorScheme.secondaryContainer
            progress != null -> MaterialTheme.colorScheme.surfaceVariant // met, silhouette
            else -> MaterialTheme.colorScheme.surfaceVariant
        },
        modifier = Modifier
            .aspectRatio(1f)
            .clickable(onClick = onTap),
    ) {
        Box {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxSize(),
            ) {
                Text(text = row.character, fontSize = 32.sp)
                Text(
                    text = row.definition.substringBefore(";").substringBefore(",").trim(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
            }
            val badge = when (rank.rank) {
                Rank.GOLD -> "★" to Color(0xFFDAA520)
                Rank.SILVER -> "●" to Color(0xFF8E9BA6)
                Rank.BRONZE -> "●" to Color(0xFFB07B4F)
                Rank.NONE -> if (progress != null) "·" to MaterialTheme.colorScheme.onSurfaceVariant else null
            }
            badge?.let { (glyph, color) ->
                Text(
                    text = glyph,
                    color = color,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        // A lapse dims the rank until re-proven — asks, never scolds (spec 04).
                        .alpha(if (rank.dimmed) 0.35f else 1f),
                )
            }
        }
    }
}
