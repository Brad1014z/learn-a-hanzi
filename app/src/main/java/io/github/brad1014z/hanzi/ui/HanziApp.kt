package io.github.brad1014z.hanzi.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.brad1014z.hanzi.data.HanziDatabase
import io.github.brad1014z.hanzi.data.RoomProgressRepository
import io.github.brad1014z.hanzi.data.SettingsStore
import io.github.brad1014z.hanzi.engine.data.CharacterRepository
import io.github.brad1014z.hanzi.engine.progress.ProgressRepository
import kotlinx.coroutines.launch

/**
 * Semantic colors for the practice engine (spec 07). TODO(son, S1): these are yours —
 * pick the palette you want and make it feel like your game.
 */
object PracticeColors {
    val ink = Color(0xFF2B2B33)
    val inkDark = Color(0xFFE8E6E3)
    val strokeCorrect = Color(0xFF3E8E5A)
    val strokeSloppy = Color(0xFFC99A2E)
    val strokeWrong = Color(0xFFC24A4A)
    val guide = Color(0x22888888)
    val faintTarget = Color(0x1A888888)
}

@Composable
fun HanziApp() {
    val dark = isSystemInDarkTheme()
    val scheme = if (dark) darkColorScheme() else lightColorScheme()
    MaterialTheme(colorScheme = scheme) {
        Surface(color = MaterialTheme.colorScheme.background) {
            val repository = remember { CharacterRepository() }
            val characters = remember { repository.listCharacters() }
            val meanings = remember { characters.associateWith { repository.load(it).shortDefinition } }
            val context = LocalContext.current
            val scope = rememberCoroutineScope()
            val sounds = remember { SoundPlayer(context) }
            var ttsReady by remember { mutableStateOf(false) }
            val speech = remember { AndroidSpeechService(context) { ttsReady = it } }
            DisposableEffect(Unit) {
                onDispose {
                    sounds.release()
                    speech.release()
                }
            }
            // M1 "It remembers you": progress + settings survive restarts (specs 03, 08).
            val progressRepository: ProgressRepository =
                remember { RoomProgressRepository(HanziDatabase.get(context)) }
            val settings = remember { SettingsStore(context) }
            val progressFlow = remember(progressRepository) { progressRepository.observeAll() }
            val progress by progressFlow.collectAsStateWithLifecycle(emptyMap())
            val autoPlay by settings.autoPlay.collectAsStateWithLifecycle(true)
            val soundOn by settings.sound.collectAsStateWithLifecycle(true)
            LaunchedEffect(soundOn) { sounds.enabled = soundOn }

            // Flow: grid → Practice (demo → writing) → Character Detail (phrases recap) → next.
            var detailChar by remember { mutableStateOf<String?>(null) }
            var practiceChar by remember { mutableStateOf<String?>(null) }
            val speechAvailable = ttsReady && speech.isAvailable("zh-Hans")

            fun nextChar(c: String) = characters[(characters.indexOf(c) + 1) % characters.size]

            when {
                practiceChar != null -> {
                    val c = practiceChar!!
                    BackHandler { practiceChar = null } // back to grid
                    PracticeScreen(
                        character = remember(c) { repository.load(c) },
                        sounds = sounds,
                        speech = speech,
                        speechAvailable = speechAvailable,
                        autoPlay = autoPlay,
                        onRecord = { record -> scope.launch { progressRepository.recordPractice(record) } },
                        onSoundToggle = { on -> scope.launch { settings.setSound(on) } },
                        onExit = { practiceChar = null },
                        onNext = {
                            practiceChar = null
                            detailChar = c // after writing, recap the phrases for this character
                        },
                    )
                }
                detailChar != null -> {
                    val c = detailChar!!
                    BackHandler { detailChar = null } // back to grid
                    CharacterDetailScreen(
                        character = remember(c) { repository.load(c) },
                        speech = speech,
                        speechAvailable = speechAvailable,
                        autoPlay = autoPlay,
                        onToggleAutoPlay = { on -> scope.launch { settings.setAutoPlay(on) } },
                        onPractice = {
                            detailChar = null
                            practiceChar = nextChar(c)
                        },
                        onExit = { detailChar = null },
                    )
                }
                else -> CharacterGridScreen(
                    characters = characters,
                    meanings = meanings,
                    progress = progress,
                    onCharacterTap = { detailChar = null; practiceChar = it },
                )
            }
        }
    }
}
