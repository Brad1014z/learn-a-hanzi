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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import io.github.brad1014z.hanzi.engine.data.CharacterRepository

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
            val sounds = remember { SoundPlayer(context) }
            var ttsReady by remember { mutableStateOf(false) }
            val speech = remember { AndroidSpeechService(context) { ttsReady = it } }
            DisposableEffect(Unit) {
                onDispose {
                    sounds.release()
                    speech.release()
                }
            }
            // Flow (spec 07 Flow A): grid → Character Detail (intro) → Practice.
            var detailChar by remember { mutableStateOf<String?>(null) }
            var practiceChar by remember { mutableStateOf<String?>(null) }
            // Auto-play the reading on intro/demo; a session-level toggle (default on).
            // Real persistence (DataStore) is Phase 2; in-memory is fine for the prototype.
            var autoPlay by remember { mutableStateOf(true) }
            val speechAvailable = ttsReady && speech.isAvailable("zh-Hans")

            fun nextChar(c: String) = characters[(characters.indexOf(c) + 1) % characters.size]

            when {
                practiceChar != null -> {
                    val c = practiceChar!!
                    BackHandler { practiceChar = null } // back to this character's detail
                    PracticeScreen(
                        character = remember(c) { repository.load(c) },
                        sounds = sounds,
                        speech = speech,
                        speechAvailable = speechAvailable,
                        autoPlay = autoPlay,
                        onExit = { practiceChar = null },
                        onNext = {
                            practiceChar = null
                            detailChar = nextChar(c)
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
                        onToggleAutoPlay = { autoPlay = it },
                        onPractice = {
                            detailChar = null
                            practiceChar = c
                        },
                        onExit = { detailChar = null },
                    )
                }
                else -> CharacterGridScreen(
                    characters = characters,
                    meanings = meanings,
                    onCharacterTap = { detailChar = it },
                )
            }
        }
    }
}
