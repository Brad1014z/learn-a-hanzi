package io.github.brad1014z.hanzi.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.brad1014z.hanzi.engine.progress.CharacterProgress

/**
 * Phase 0 stand-in for the real character list. TODO(son, S3): this grid grows into
 * "The Shelf" — your collection screen. Since M1 it remembers: practiced characters
 * arrive tinted with a badge (★ for a perfect last run, ✓ otherwise) — but that's just
 * dad's placeholder styling. How your collection *looks* is yours.
 */
@Composable
fun CharacterGridScreen(
    characters: List<String>,
    meanings: Map<String, String>,
    progress: Map<String, CharacterProgress> = emptyMap(),
    onCharacterTap: (String) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .padding(16.dp),
    ) {
        Text(
            text = "Hanzi Prototype",
            style = MaterialTheme.typography.headlineMedium,
        )
        Text(
            text = "${characters.size} characters · ${progress.size} practiced · tap one to practice",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 16.dp),
        )
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 72.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(characters) { char ->
                val charProgress = progress[char]
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = if (charProgress != null) {
                        MaterialTheme.colorScheme.secondaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    },
                    modifier = Modifier
                        .aspectRatio(1f)
                        .clickable { onCharacterTap(char) },
                ) {
                    Box {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                            modifier = Modifier.fillMaxSize(),
                        ) {
                            Text(text = char, fontSize = 32.sp)
                            meanings[char]?.let { meaning ->
                                Text(
                                    text = meaning,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(horizontal = 4.dp),
                                )
                            }
                        }
                        if (charProgress != null) {
                            Text(
                                text = if (charProgress.lastGrade == 5) "★" else "✓",
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
        }
    }
}
