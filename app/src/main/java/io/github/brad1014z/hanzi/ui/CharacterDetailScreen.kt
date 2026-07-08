package io.github.brad1014z.hanzi.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.brad1014z.hanzi.engine.data.CharacterData
import io.github.brad1014z.hanzi.engine.data.Phrase
import io.github.brad1014z.hanzi.engine.speech.SpeechService

/**
 * Character Detail — the recap step shown after writing practice (spec 07 screen 2).
 * Reinforces what was just written: big character (tap to hear), pinyin, meaning, and
 * 2-3 common phrases each with tap-to-play audio to teach reading. Then on to the next one.
 */
@Composable
fun CharacterDetailScreen(
    character: CharacterData,
    speech: SpeechService = SpeechService.Silent,
    speechAvailable: Boolean = false,
    autoPlay: Boolean = true,
    onToggleAutoPlay: (Boolean) -> Unit = {},
    onPractice: () -> Unit,
    onExit: () -> Unit,
) {
    // Auto-play the character's reading when the intro opens (spec: hear it on load).
    LaunchedEffect(character.character) {
        if (autoPlay && speechAvailable) {
            kotlinx.coroutines.delay(250)
            speech.speak(character.character, "zh-Hans")
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .safeDrawingPadding()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onExit) { Text("‹ All") }
            Spacer(Modifier.weight(1f))
            if (speechAvailable) {
                TextButton(onClick = { onToggleAutoPlay(!autoPlay) }) {
                    Text(if (autoPlay) "Auto-play: on" else "Auto-play: off")
                }
            }
        }

        // Big character, tappable to replay its reading.
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = character.character,
                fontSize = 120.sp,
                modifier = Modifier
                    .clickable(enabled = speechAvailable) {
                        speech.speak(character.character, "zh-Hans")
                    },
            )
            if (speechAvailable) {
                Text("🔊", fontSize = 28.sp, modifier = Modifier.padding(start = 12.dp))
            }
        }

        Text(
            text = character.pinyin.joinToString(", "),
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        Text(
            text = character.definition.ifBlank { character.shortDefinition },
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp, bottom = 20.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )

        if (character.phrases.isNotEmpty()) {
            Text(
                text = "Words with ${character.character}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 8.dp),
            )
            for (phrase in character.phrases) {
                PhraseRow(
                    phrase = phrase,
                    speechAvailable = speechAvailable,
                    onPlay = { speech.speak(phrase.phrase, "zh-Hans") },
                )
                Spacer(Modifier.padding(4.dp))
            }
        }

        Spacer(Modifier.padding(12.dp))
        Button(
            onClick = onPractice,
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Next character") }
    }
}

@Composable
private fun PhraseRow(phrase: Phrase, speechAvailable: Boolean, onPlay: () -> Unit) {
    // Tapping anywhere on the row plays it — a big, kid-friendly target (spec 07).
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = speechAvailable, onClick = onPlay),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(phrase.phrase, fontSize = 26.sp)
                    Text(
                        text = phrase.pinyin,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 10.dp, bottom = 3.dp),
                    )
                }
                Text(
                    text = phrase.english,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                )
            }
            if (speechAvailable) {
                Spacer(Modifier.width(8.dp))
                Text("🔊", fontSize = 24.sp)
            }
        }
    }
}
