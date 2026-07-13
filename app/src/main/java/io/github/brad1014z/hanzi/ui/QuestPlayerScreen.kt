package io.github.brad1014z.hanzi.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.brad1014z.hanzi.data.RoomContentRepository
import io.github.brad1014z.hanzi.data.RoomProgressRepository
import io.github.brad1014z.hanzi.data.Sessions
import io.github.brad1014z.hanzi.engine.data.CharacterData
import io.github.brad1014z.hanzi.engine.play.QuestPlan
import io.github.brad1014z.hanzi.engine.play.QuestSession
import io.github.brad1014z.hanzi.engine.play.QuestStep
import io.github.brad1014z.hanzi.engine.play.Rank
import io.github.brad1014z.hanzi.engine.play.Ranks
import io.github.brad1014z.hanzi.engine.play.XpConfig
import io.github.brad1014z.hanzi.engine.speech.SpeechService
import kotlinx.coroutines.launch

/** A rank-up earned during the quest, replayed in the chest (spec 10). */
data class RankUp(val character: String, val rank: Rank)

/**
 * The quest player (spec 04/10): drives warm-up → reviews → new → boss on the shared
 * practice canvas, submits every card to the SRS, pays XP per step, and hands over to
 * the chest. Reviews and the boss run in recall mode (no demo, guide off); new
 * characters get the full intro → demo → quiz flow (spec 07 Flow A).
 */
@Composable
fun QuestPlayerScreen(
    plan: QuestPlan,
    contentRepository: RoomContentRepository,
    progressRepository: RoomProgressRepository,
    sounds: SoundPlayer,
    speech: SpeechService,
    speechAvailable: Boolean,
    autoPlay: Boolean,
    onSoundToggle: (Boolean) -> Unit,
    onFinished: (xpEarned: Int, rankUps: List<RankUp>) -> Unit,
    onExit: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var session by remember { mutableStateOf(QuestSession.start(plan)) }
    var introSeen by remember { mutableStateOf(false) } // NewChar: intro before practice
    var pendingGrade by remember { mutableIntStateOf(-1) } // recorded, waiting for "Next"
    var rankUps by remember { mutableStateOf(listOf<RankUp>()) }

    BackHandler { onExit() } // every card is already saved; a re-entered quest rebuilds

    val step = session.current
    if (step == null) {
        // All cards written → the chest (spec 10: opens regardless of the boss result).
        ChestScreen(
            xpBeforeChest = session.xpEarned,
            rankUps = rankUps,
            onOpened = {
                val opened = session.openChest()
                session = opened
                scope.launch {
                    progressRepository.addXp(XpConfig.CHEST_OPENED)
                    onFinished(opened.xpEarned, rankUps)
                }
            },
        )
        return
    }

    val data by produceState<CharacterData?>(null, step.character) {
        value = contentRepository.load(step.character)
    }
    val loaded = data
    if (loaded == null) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxSize(),
        ) { CircularProgressIndicator() }
        return
    }

    fun advanceAfterNext() {
        if (pendingGrade < 0) return
        val before = session.xpEarned
        session = session.advance(pendingGrade)
        pendingGrade = -1
        introSeen = false
        scope.launch { progressRepository.addXp(session.xpEarned - before) }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        QuestStepBanner(session = session, step = step)
        val isNew = step is QuestStep.NewChar
        // Key by step index: consecutive steps can present the SAME character (new →
        // re-test → boss), and the practice state must reset for each sitting.
        androidx.compose.runtime.key(session.index) {
        if (isNew && !introSeen) {
            // Intro: read before you write (spec 07 Flow A).
            CharacterDetailScreen(
                character = loaded,
                speech = speech,
                speechAvailable = speechAvailable,
                autoPlay = autoPlay,
                practiceLabel = "Write it!",
                onPractice = { introSeen = true },
                onExit = onExit,
            )
        } else {
            PracticeScreen(
                character = loaded,
                sounds = sounds,
                speech = speech,
                speechAvailable = speechAvailable,
                autoPlay = autoPlay,
                startInQuiz = !isNew, // recall mode for everything already met
                allowGuide = step !is QuestStep.Boss, // boss: fully from memory (spec 04)
                allowRetry = false, // the quest's re-test mechanic owns retries
                sessionTag = when (step) {
                    is QuestStep.WarmUp -> Sessions.QUEST_WARMUP
                    is QuestStep.Review -> Sessions.QUEST_REVIEW
                    is QuestStep.NewChar -> Sessions.QUEST_NEW
                    is QuestStep.ReTest -> Sessions.QUEST_RETEST
                    is QuestStep.Boss -> Sessions.QUEST_BOSS
                },
                onRecord = { record ->
                    pendingGrade = record.grade
                    scope.launch {
                        val before = Ranks.of(progressRepository.progressOf(record.character))
                        progressRepository.recordPractice(record)
                        val after = Ranks.of(progressRepository.progressOf(record.character))
                        if (after.rank > before.rank && !after.dimmed) {
                            rankUps = rankUps + RankUp(record.character, after.rank)
                        }
                    }
                },
                onSoundToggle = onSoundToggle,
                onExit = onExit,
                onNext = { advanceAfterNext() },
            )
        }
        }
    }
}

@Composable
private fun QuestStepBanner(session: QuestSession, step: QuestStep) {
    val label = when (step) {
        is QuestStep.WarmUp -> "Warm-up — an easy one to start"
        is QuestStep.Review -> "Review"
        is QuestStep.NewChar -> "New character!"
        is QuestStep.ReTest -> "One more time — from memory"
        is QuestStep.Boss -> "BOSS — no guide, all you"
    }
    Surface(color = MaterialTheme.colorScheme.primaryContainer) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 6.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Spacer(Modifier.weight(1f))
                Text(
                    text = "${(session.cardsDone + 1).coerceAtMost(session.cardsTotal)} / ${session.cardsTotal} · ${session.xpEarned} XP",
                    style = MaterialTheme.typography.labelMedium,
                )
            }
            LinearProgressIndicator(
                progress = { (session.cardsDone.toFloat() / session.cardsTotal).coerceIn(0f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
            )
        }
    }
}

/**
 * The chest (spec 10): XP tally + rank-ups replayed. Big, short, skippable.
 * TODO(son, S5): this is your celebration — confetti, sounds, the works.
 */
@Composable
private fun ChestScreen(xpBeforeChest: Int, rankUps: List<RankUp>, onOpened: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .padding(24.dp),
    ) {
        Text("🎁", fontSize = 84.sp)
        Text(
            text = "Quest complete!",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 8.dp),
        )
        Text(
            text = "+$xpBeforeChest XP so far — the chest adds +${XpConfig.CHEST_OPENED}",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(top = 4.dp),
        )
        if (rankUps.isNotEmpty()) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.padding(top = 16.dp),
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(16.dp),
                ) {
                    Text("Rank-ups", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    for (up in rankUps) {
                        Text(
                            text = "${up.character}  →  ${up.rank.name.lowercase().replaceFirstChar { it.uppercase() }}",
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(24.dp))
        Button(onClick = onOpened) { Text("Open the chest", fontSize = 18.sp) }
    }
}
