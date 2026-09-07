package io.github.brad1014z.hanzi.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.brad1014z.hanzi.BuildConfig
import io.github.brad1014z.hanzi.data.DatasetSeeder
import io.github.brad1014z.hanzi.data.HanziDatabase
import io.github.brad1014z.hanzi.data.RoomContentRepository
import io.github.brad1014z.hanzi.data.RoomProgressRepository
import io.github.brad1014z.hanzi.data.RoomQuestStore
import io.github.brad1014z.hanzi.data.SettingsStore
import io.github.brad1014z.hanzi.engine.data.CharacterData
import io.github.brad1014z.hanzi.engine.play.DailyQuestPlan
import io.github.brad1014z.hanzi.engine.play.QuestBuilder
import io.github.brad1014z.hanzi.engine.play.Ranks
import io.github.brad1014z.hanzi.engine.play.unlockedWorldCount
import io.github.brad1014z.hanzi.engine.progress.CharacterProgress
import java.util.UUID
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

private enum class Screen { HOME, COLLECTION, QUEST, SETTINGS, CREDITS }

/** Everything the Quest Hub shows, recomputed whenever progress changes. */
private data class HubState(
    val plan: DailyQuestPlan,
    val summary: QuestSummary,
    val unlockedWorlds: Int,
    val currentWorldName: String?,
    val currentWorldMastery: Double,
    val nextWorldName: String?,
    val daysPlayed: Int,
)

@Composable
fun HanziApp() {
    val dark = isSystemInDarkTheme()
    val scheme = if (dark) {
        darkColorScheme(
            primary = Color(0xFFE07A62),
            secondary = Color(0xFF86B8A7),
            background = Color(0xFF1D1A17),
            surface = Color(0xFF241F1B),
            onBackground = Color(0xFFF2E8D8),
            onSurface = Color(0xFFF2E8D8),
        )
    } else {
        lightColorScheme(
            primary = Color(0xFFB54832), // restrained vermilion
            secondary = Color(0xFF2E6B5C), // jade
            background = Color(0xFFF7F0E3), // warm paper
            surface = Color(0xFFFFFBF3),
            onBackground = Color(0xFF211C18),
            onSurface = Color(0xFF211C18),
        )
    }
    MaterialTheme(colorScheme = scheme) {
        Surface(color = MaterialTheme.colorScheme.background) {
            val context = LocalContext.current
            val scope = rememberCoroutineScope()
            val sounds = remember { SoundPlayer(context) }
            var ttsReady by remember { mutableStateOf(false) }
            val speech = remember {
                PregenAudioSpeechService(context, AndroidSpeechService(context) { ttsReady = it })
            }
            DisposableEffect(Unit) {
                onDispose {
                    sounds.release()
                    speech.release()
                }
            }
            val db = remember { HanziDatabase.get(context) }
            val progressRepository = remember { RoomProgressRepository(db) }
            val questStore = remember { RoomQuestStore(db) }
            val contentRepository = remember { RoomContentRepository(context, db) }
            val settings = remember { SettingsStore(context) }
            val progressFlow = remember(progressRepository) { progressRepository.observeAll() }
            val progress by progressFlow.collectAsStateWithLifecycle(emptyMap())
            val autoPlay by settings.autoPlay.collectAsStateWithLifecycle(true)
            val soundOn by settings.sound.collectAsStateWithLifecycle(true)
            val pilotExportConsent by settings.pilotExportConsent.collectAsStateWithLifecycle(false)
            val pilotFacilitatorLabel by settings.pilotFacilitatorLabel.collectAsStateWithLifecycle("unlabeled")
            val strokeExporter = remember(pilotExportConsent, pilotFacilitatorLabel) {
                PilotStrokeExporter(context, pilotExportConsent, pilotFacilitatorLabel)
            }
            LaunchedEffect(soundOn) { sounds.enabled = soundOn }
            val speechAvailable = remember(ttsReady) { speech.isAvailable("zh-Hans") }

            val worlds by produceState<List<RoomContentRepository.World>?>(null) {
                DatasetSeeder(context, db).ensureSeeded()
                value = contentRepository.worlds()
            }

            var screen by remember { mutableStateOf(Screen.HOME) }
            var refresh by remember { mutableIntStateOf(0) }
            var questPlan by remember { mutableStateOf<DailyQuestPlan?>(null) }
            var questKey by remember { mutableStateOf("quest-none") }
            // Browse overlays inside the Collection (spec 04 track 2 — cap-exempt).
            var detailChar by remember { mutableStateOf<String?>(null) }
            var practiceChar by remember { mutableStateOf<String?>(null) }
            var characterShelf by remember { mutableStateOf(CharacterShelf.COLLECTION) }

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

            // Hub state: due queue + cap headroom + world gating (specs 04/10).
            val hub by produceState<HubState?>(null, progress, refresh) {
                val now = System.currentTimeMillis()
                val today = questStore.loadToday(now)
                val due = today.due
                val masteryByWorld = loadedWorlds.map { world ->
                    Ranks.masteryFraction(world.characters.map { progress[it.character] })
                }
                val unlocked = unlockedWorldCount(masteryByWorld).coerceAtMost(loadedWorlds.size)
                val gatedCandidates = loadedWorlds.take(unlocked)
                    .flatMap { it.characters }
                    .map { it.character }
                    .filter { it !in progress }
                val newCandidates = (
                    QuestBuilder.STARTER_ORDER.filter { it !in progress } + gatedCandidates
                ).distinct()
                val plan = QuestBuilder.buildDaily(due, newCandidates, today.introducedToday)
                value = HubState(
                    plan = plan,
                    summary = QuestSummary(
                        dueCount = due.size,
                        newCount = plan.core.newCharacters.size,
                        backlogWarning = plan.core.backlogWarning,
                    ),
                    unlockedWorlds = unlocked,
                    currentWorldName = loadedWorlds.getOrNull(unlocked - 1)?.name,
                    currentWorldMastery = masteryByWorld.getOrNull(unlocked - 1) ?: 0.0,
                    nextWorldName = loadedWorlds.getOrNull(unlocked)?.name,
                    daysPlayed = today.daysPlayed,
                )
            }

            @Composable
            fun loadCharacter(c: String): CharacterData? {
                val data by produceState<CharacterData?>(null, c) {
                    value = contentRepository.load(c)
                }
                return data
            }

            when (screen) {
                Screen.HOME -> {
                    val h = hub
                    if (h == null) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                            modifier = Modifier.fillMaxSize(),
                        ) { CircularProgressIndicator() }
                    } else {
                        QuestHubScreen(
                            daysPlayed = h.daysPlayed,
                            quest = h.summary,
                            currentWorldName = h.currentWorldName,
                            currentWorldMastery = h.currentWorldMastery,
                            nextWorldName = h.nextWorldName,
                            onStartQuest = {
                                questPlan = h.plan
                                questKey = UUID.randomUUID().toString()
                                screen = Screen.QUEST
                            },
                            onCollection = { screen = Screen.COLLECTION },
                            onSettings = { screen = Screen.SETTINGS },
                        )
                    }
                }

                Screen.QUEST -> {
                    val plan = questPlan
                    if (plan == null || plan.core.isEmpty) {
                        screen = Screen.HOME
                    } else {
                        val controller: QuestController = viewModel(
                            key = questKey,
                            factory = QuestController.factory(plan, contentRepository, questStore),
                        )
                        QuestPlayerScreen(
                            controller = controller,
                            sounds = sounds,
                            speech = speech,
                            speechAvailable = speechAvailable,
                            autoPlay = autoPlay,
                            strokeAttemptObserver = strokeExporter,
                            onSoundToggle = { on -> scope.launch { settings.setSound(on) } },
                            onFinished = {
                                questPlan = null
                                refresh++
                                screen = Screen.HOME
                            },
                            onExit = {
                                questPlan = null
                                refresh++
                                screen = Screen.HOME
                            },
                        )
                    }
                }

                Screen.SETTINGS -> {
                    BackHandler { screen = Screen.HOME }
                    val datasetVersion by produceState<String?>(null) {
                        value = db.metaDao().get(DatasetSeeder.DATASET_VERSION_KEY)
                    }
                    SettingsScreen(
                        soundOn = soundOn,
                        autoPlay = autoPlay,
                        datasetVersion = datasetVersion,
                        pilotToolsEnabled = BuildConfig.DEBUG,
                        pilotExportConsent = pilotExportConsent,
                        pilotFacilitatorLabel = pilotFacilitatorLabel,
                        onSound = { scope.launch { settings.setSound(it) } },
                        onAutoPlay = { scope.launch { settings.setAutoPlay(it) } },
                        onResetProgress = {
                            scope.launch {
                                progressRepository.resetProgress()
                                refresh++
                            }
                        },
                        onPilotExportConsent = {
                            scope.launch { settings.setPilotExportConsent(it) }
                        },
                        onPilotFacilitatorLabel = {
                            scope.launch { settings.setPilotFacilitatorLabel(it) }
                        },
                        onCredits = { screen = Screen.CREDITS },
                        onBack = { screen = Screen.HOME },
                    )
                }

                Screen.CREDITS -> CreditsScreen(onBack = { screen = Screen.SETTINGS })

                Screen.COLLECTION -> when {
                    practiceChar != null -> {
                        val c = practiceChar!!
                        BackHandler { practiceChar = null }
                        loadCharacter(c)?.let { data ->
                            PracticeScreen(
                                character = data,
                                sounds = sounds,
                                speech = speech,
                                speechAvailable = speechAvailable,
                                autoPlay = autoPlay,
                                strokeAttemptObserver = strokeExporter,
                                onRecord = { record -> scope.launch { progressRepository.recordPractice(record) } },
                                onSoundToggle = { on -> scope.launch { settings.setSound(on) } },
                                onExit = { practiceChar = null },
                                onNext = {
                                    practiceChar = null
                                },
                            )
                        }
                    }
                    detailChar != null -> {
                        val c = detailChar!!
                        BackHandler { detailChar = null }
                        loadCharacter(c)?.let { data ->
                            CharacterDetailScreen(
                                character = data,
                                speech = speech,
                                speechAvailable = speechAvailable,
                                autoPlay = autoPlay,
                                practiceLabel = "Practice",
                                onToggleAutoPlay = { on -> scope.launch { settings.setAutoPlay(on) } },
                                onPractice = { practiceChar = c },
                                onExit = { detailChar = null },
                            )
                        }
                    }
                    else -> {
                        BackHandler { screen = Screen.HOME }
                        CharacterGridScreen(
                            worlds = loadedWorlds,
                            progress = progress,
                            unlockedWorlds = hub?.unlockedWorlds ?: Int.MAX_VALUE,
                            shelf = characterShelf,
                            onShelfChange = { characterShelf = it },
                            onBack = { screen = Screen.HOME },
                            onCharacterTap = { detailChar = it },
                        )
                    }
                }
            }
        }
    }
}
