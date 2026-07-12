package io.github.brad1014z.hanzi.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.padding
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.brad1014z.hanzi.data.DatasetSeeder
import io.github.brad1014z.hanzi.data.HanziDatabase
import io.github.brad1014z.hanzi.data.RoomContentRepository
import io.github.brad1014z.hanzi.data.RoomProgressRepository
import io.github.brad1014z.hanzi.data.SettingsStore
import io.github.brad1014z.hanzi.engine.data.CharacterData
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
            val context = LocalContext.current
            val scope = rememberCoroutineScope()
            val sounds = remember { SoundPlayer(context) }
            var ttsReady by remember { mutableStateOf(false) }
            // Pre-generated clips when bundled (M2, spec 01), device TTS as fallback.
            val speech = remember {
                PregenAudioSpeechService(context, AndroidSpeechService(context) { ttsReady = it })
            }
            DisposableEffect(Unit) {
                onDispose {
                    sounds.release()
                    speech.release()
                }
            }
            // M1 persistence + M2 content: one DB, user tables preserved across dataset
            // updates (spec 03 reseed via DatasetSeeder).
            val db = remember { HanziDatabase.get(context) }
            val progressRepository: ProgressRepository = remember { RoomProgressRepository(db) }
            val contentRepository = remember { RoomContentRepository(db) }
            val settings = remember { SettingsStore(context) }
            val progressFlow = remember(progressRepository) { progressRepository.observeAll() }
            val progress by progressFlow.collectAsStateWithLifecycle(emptyMap())
            val autoPlay by settings.autoPlay.collectAsStateWithLifecycle(true)
            val soundOn by settings.sound.collectAsStateWithLifecycle(true)
            LaunchedEffect(soundOn) { sounds.enabled = soundOn }

            // Seed/refresh content from the bundled dataset, then load the worlds.
            val worlds by produceState<List<RoomContentRepository.World>?>(null) {
                DatasetSeeder(context, db).ensureSeeded()
                value = contentRepository.worlds()
            }

            // Flow: worlds grid → Practice (demo → writing) → Detail (recap) → next.
            var detailChar by remember { mutableStateOf<String?>(null) }
            var practiceChar by remember { mutableStateOf<String?>(null) }
            // Recomputed when device TTS finishes init; bundled clips count regardless.
            val speechAvailable = remember(ttsReady) { speech.isAvailable("zh-Hans") }

            val loadedWorlds = worlds
            if (loadedWorlds == null) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    CircularProgressIndicator()
                    Text(
                        text = "Opening the worlds…",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                }
                return@Surface
            }
            // Teaching order across all worlds (sequence is world-major — spec 04).
            val teachingOrder = remember(loadedWorlds) {
                loadedWorlds.flatMap { world -> world.characters.map { it.character } }
            }

            fun nextChar(c: String) =
                teachingOrder[(teachingOrder.indexOf(c) + 1) % teachingOrder.size]

            @Composable
            fun loadCharacter(c: String): CharacterData? {
                val data by produceState<CharacterData?>(null, c) {
                    value = contentRepository.load(c)
                }
                return data
            }

            when {
                practiceChar != null -> {
                    val c = practiceChar!!
                    BackHandler { practiceChar = null } // back to grid
                    loadCharacter(c)?.let { data ->
                        PracticeScreen(
                            character = data,
                            sounds = sounds,
                            speech = speech,
                            speechAvailable = speechAvailable,
                            autoPlay = autoPlay,
                            onRecord = { record -> scope.launch { progressRepository.recordPractice(record) } },
                            onSoundToggle = { on -> scope.launch { settings.setSound(on) } },
                            onExit = { practiceChar = null },
                            onNext = {
                                practiceChar = null
                                detailChar = c // after writing, recap phrases + sentence
                            },
                        )
                    }
                }
                detailChar != null -> {
                    val c = detailChar!!
                    BackHandler { detailChar = null } // back to grid
                    loadCharacter(c)?.let { data ->
                        CharacterDetailScreen(
                            character = data,
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
                }
                else -> CharacterGridScreen(
                    worlds = loadedWorlds,
                    progress = progress,
                    onCharacterTap = { detailChar = null; practiceChar = it },
                )
            }
        }
    }
}
