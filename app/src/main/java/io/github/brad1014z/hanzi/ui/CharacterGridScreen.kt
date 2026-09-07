package io.github.brad1014z.hanzi.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import io.github.brad1014z.hanzi.data.CurriculumRow
import io.github.brad1014z.hanzi.data.RoomContentRepository
import io.github.brad1014z.hanzi.engine.progress.CharacterProgress

enum class CharacterShelf { COLLECTION, BROWSE }

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
    shelf: CharacterShelf = CharacterShelf.COLLECTION,
    onShelfChange: (CharacterShelf) -> Unit = {},
    onBack: (() -> Unit)? = null,
    onCharacterTap: (String) -> Unit,
) {
    var search by remember { mutableStateOf("") }
    val visibleWorlds = worlds.mapNotNull { world ->
        val rows = world.characters.filter { row ->
            val isVisible = shelf == CharacterShelf.BROWSE || row.character in progress
            isVisible && (search.isBlank() || row.character.contains(search.trim()))
        }
        if (rows.isEmpty()) null else world.copy(characters = rows)
    }
    val total = visibleWorlds.sumOf { it.characters.size }
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
                text = if (shelf == CharacterShelf.COLLECTION) "Collection" else "Browse",
                style = MaterialTheme.typography.headlineMedium,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = { onShelfChange(CharacterShelf.COLLECTION) },
                enabled = shelf != CharacterShelf.COLLECTION,
                modifier = Modifier.weight(1f),
            ) { Text("Collection") }
            OutlinedButton(
                onClick = { onShelfChange(CharacterShelf.BROWSE) },
                enabled = shelf != CharacterShelf.BROWSE,
                modifier = Modifier.weight(1f),
            ) { Text("Browse") }
        }
        if (shelf == CharacterShelf.BROWSE) {
            TextField(
                value = search,
                onValueChange = { search = it.take(8) },
                label = { Text("Search characters") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )
        }
        Text(
            text = if (shelf == CharacterShelf.COLLECTION) {
                "$total encountered"
            } else {
                "$total available · ${progress.size} encountered"
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 8.dp),
        )
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 72.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            visibleWorlds.forEach { world ->
                val worldIndex = worlds.indexOfFirst { it.id == world.id }
                item(key = "world-${world.id}", span = { GridItemSpan(maxLineSpan) }) {
                    WorldHeader(
                        name = world.name,
                        locked = worldIndex >= unlockedWorlds,
                        previousName = worlds.getOrNull(worldIndex - 1)?.name,
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
private fun WorldHeader(name: String, locked: Boolean, previousName: String?) {
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
                "guided lessons open after $previousName · free practice is available"
            } else {
                "available"
            },
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun CharacterTile(row: CurriculumRow, progress: CharacterProgress?, onTap: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = when {
            progress != null -> MaterialTheme.colorScheme.surfaceVariant // met, silhouette
            else -> MaterialTheme.colorScheme.surfaceVariant
        },
        modifier = Modifier
            .aspectRatio(1f)
            .semantics {
                contentDescription = if (progress == null) {
                    "${row.character}, not encountered, open details"
                } else {
                    "${row.character}, encountered, open details"
                }
            }
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
                    text = if (progress == null) "Not met yet" else "Practiced",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (progress != null) {
                Text(
                    text = "✓",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp),
                )
            }
        }
    }
}
