package io.github.brad1014z.hanzi.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.brad1014z.hanzi.engine.data.CharacterData
import io.github.brad1014z.hanzi.engine.speech.SpeechService
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

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
    practiceLabel: String = "Next character",
    onToggleAutoPlay: (Boolean) -> Unit = {},
    onPractice: () -> Unit,
    onExit: () -> Unit,
) {
    val lesson = checkNotNull(character.lessonContent) {
        "Character detail requires the LessonContent contract"
    }
    var showMoreReadings by remember(character.character) { mutableStateOf(false) }
    // Auto-play the character's reading when the intro opens (spec: hear it on load).
    LaunchedEffect(character.character) {
        if (autoPlay && speechAvailable) {
            kotlinx.coroutines.delay(250)
            speech.speak(lesson.pronunciationAudio.spokenText, "zh-Hans")
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

        // Big character, tappable to replay its reading. The character and the 🔊 glyph
        // are ONE labelled control: the glyph is decorative (it carried no click action
        // of its own) and a bare clickable character announced nothing useful, so
        // TalkBack now reads a real button — "Play pronunciation for 人" (asserted by
        // CoreUiTest; spec 07 accessibility).
        val pronunciationLabel = "Play pronunciation for ${character.character}"
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (speechAvailable) {
                        Modifier
                            .clickable(onClickLabel = pronunciationLabel) {
                                speech.speak(lesson.pronunciationAudio.spokenText, "zh-Hans")
                            }
                            .semantics(mergeDescendants = true) {
                                contentDescription = pronunciationLabel
                                role = Role.Button
                            }
                    } else {
                        Modifier
                    },
                ),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = character.character, fontSize = 120.sp)
            if (speechAvailable) {
                Text("🔊", fontSize = 28.sp, modifier = Modifier.padding(start = 12.dp))
            }
        }

        Text(
            text = lesson.primaryReading.pinyin,
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        Text(
            text = lesson.learnerGloss,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp, bottom = 20.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )

        if (lesson.secondaryReadings.isNotEmpty()) {
            TextButton(onClick = { showMoreReadings = !showMoreReadings }) {
                Text(if (showMoreReadings) "Hide more readings" else "More readings")
            }
            if (showMoreReadings) Text(
                text = lesson.secondaryReadings.joinToString(", "),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        lesson.usageExample?.let { example ->
            Text(
                text = "In use",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 12.dp, bottom = 8.dp),
            )
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.secondaryContainer,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(14.dp)) {
                    Text(example.text, fontSize = 24.sp)
                    Text(example.segmentedPinyin, style = MaterialTheme.typography.bodyMedium)
                    Text(example.translation, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }

        Spacer(Modifier.padding(12.dp))
        Button(
            onClick = onPractice,
            modifier = Modifier.fillMaxWidth(),
        ) { Text(practiceLabel) }
    }
}
