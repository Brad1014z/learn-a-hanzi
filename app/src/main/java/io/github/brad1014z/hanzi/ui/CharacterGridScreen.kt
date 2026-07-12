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
import io.github.brad1014z.hanzi.data.CurriculumRow
import io.github.brad1014z.hanzi.data.RoomContentRepository
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
    onCharacterTap: (String) -> Unit,
) {
    val total = worlds.sumOf { it.characters.size }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Text(
            text = "Hanzi Prototype",
            style = MaterialTheme.typography.headlineMedium,
        )
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
            for (world in worlds) {
                item(key = "world-${world.id}", span = { GridItemSpan(maxLineSpan) }) {
                    WorldHeader(
                        name = world.name,
                        practiced = world.characters.count { it.character in progress },
                        total = world.characters.size,
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
private fun WorldHeader(name: String, practiced: Int, total: Int) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(top = 12.dp, bottom = 2.dp),
    ) {
        Text(
            text = name,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.padding(4.dp))
        Text(
            text = "$practiced / $total",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun CharacterTile(row: CurriculumRow, progress: CharacterProgress?, onTap: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = if (progress != null) {
            MaterialTheme.colorScheme.secondaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceVariant
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
            if (progress != null) {
                Text(
                    text = if (progress.lastGrade == 5) "★" else "✓",
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp),
                )
            }
        }
    }
}
