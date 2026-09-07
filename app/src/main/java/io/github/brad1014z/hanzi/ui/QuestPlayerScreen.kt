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
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.brad1014z.hanzi.engine.play.QuestSession
import io.github.brad1014z.hanzi.engine.play.QuestStep
import io.github.brad1014z.hanzi.engine.speech.SpeechService

/** Renders controller state only; persistence and session advancement live in the ViewModel. */
@Composable
fun QuestPlayerScreen(
    controller: QuestController,
    sounds: SoundPlayer,
    speech: SpeechService,
    speechAvailable: Boolean,
    autoPlay: Boolean,
    strokeAttemptObserver: StrokeAttemptObserver = StrokeAttemptObserver.None,
    onSoundToggle: (Boolean) -> Unit,
    onFinished: () -> Unit,
    onExit: () -> Unit,
) {
    val state by controller.state.collectAsStateWithLifecycle()
    BackHandler { onExit() }

    LaunchedEffect(state.phase) {
        if (state.phase == QuestPhase.COMPLETE) onFinished()
    }

    when (state.phase) {
        QuestPhase.LOADING -> LoadingQuest()
        QuestPhase.CHEST -> ChestScreen(
            saving = state.commitStatus is CommitStatus.Saving,
            error = (state.commitStatus as? CommitStatus.Failed)?.message,
            onOpened = { controller.onAction(QuestAction.OpenChest) },
        )
        QuestPhase.BONUS_OFFER -> BonusChoiceScreen(
            onAccept = { controller.onAction(QuestAction.AcceptBonus) },
            onDecline = { controller.onAction(QuestAction.DeclineBonus) },
        )
        QuestPhase.COMPLETE -> LoadingQuest(
            message = if (state.isBonus) "Bonus complete" else "Quest complete",
        )
        QuestPhase.FAILED -> LoadingQuest(
            message = state.message ?: "This lesson could not be opened.",
        )
        QuestPhase.PLAYING -> {
            val step = state.currentStep
            val loaded = state.character
            if (step == null || loaded == null) {
                LoadingQuest()
            } else {
                Column(modifier = Modifier.fillMaxSize()) {
                    QuestStepBanner(
                        session = state.session,
                        step = step,
                        title = state.title,
                        isBonus = state.isBonus,
                    )
                    val isNew = step is QuestStep.NewChar
                    androidx.compose.runtime.key(state.session.index, state.isBonus) {
                        if (isNew && state.showIntroduction) {
                            CharacterDetailScreen(
                                character = loaded,
                                speech = speech,
                                speechAvailable = speechAvailable,
                                autoPlay = autoPlay,
                                practiceLabel = "Show me the strokes",
                                onPractice = {
                                    controller.onAction(QuestAction.IntroductionFinished)
                                },
                                onExit = onExit,
                            )
                        } else {
                            val failure = state.commitStatus as? CommitStatus.Failed
                            PracticeScreen(
                                character = loaded,
                                sounds = sounds,
                                speech = speech,
                                speechAvailable = speechAvailable,
                                autoPlay = autoPlay,
                                startInQuiz = !isNew,
                                allowGuide = step !is QuestStep.Boss,
                                allowRetry = false,
                                onRecord = {
                                    controller.onAction(QuestAction.CardFinished(it))
                                },
                                canAdvance = state.commitStatus is CommitStatus.Saved,
                                isSaving = state.commitStatus is CommitStatus.Saving,
                                saveError = failure?.message,
                                strokeAttemptObserver = strokeAttemptObserver,
                                onRetrySave = {
                                    controller.onAction(QuestAction.RetryCardCommit)
                                },
                                onSoundToggle = onSoundToggle,
                                onExit = onExit,
                                onNext = { controller.onAction(QuestAction.NextCard) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LoadingQuest(message: String? = null) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.fillMaxSize(),
    ) {
        if (message == null) CircularProgressIndicator() else {
            Text(message, style = MaterialTheme.typography.titleLarge)
        }
    }
}

@Composable
private fun QuestStepBanner(
    session: QuestSession,
    step: QuestStep,
    title: String,
    isBonus: Boolean,
) {
    val label = when (step) {
        is QuestStep.WarmUp -> "Warm-up"
        is QuestStep.Review -> "Review"
        is QuestStep.NewChar -> "New character"
        is QuestStep.ReTest -> "From memory"
        is QuestStep.Boss -> "Final memory check"
    }
    Surface(color = MaterialTheme.colorScheme.primaryContainer) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = if (isBonus) "Bonus · $label" else "$title · $label",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = "${(session.cardsDone + 1).coerceAtMost(session.cardsTotal)} / " +
                        session.cardsTotal,
                    style = MaterialTheme.typography.labelMedium,
                )
            }
            LinearProgressIndicator(
                progress = {
                    (session.cardsDone.toFloat() / session.cardsTotal.coerceAtLeast(1))
                        .coerceIn(0f, 1f)
                },
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
            )
        }
    }
}

@Composable
internal fun ChestScreen(saving: Boolean, error: String?, onOpened: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.fillMaxSize().safeDrawingPadding().padding(24.dp),
    ) {
        Text("◇", fontSize = 72.sp)
        Text(
            text = "Shape Shift complete",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 8.dp),
        )
        Text(
            text = "Your completed quest is saved in the collection.",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(top = 4.dp),
        )
        if (error != null) {
            Text(
                "The reward could not be stored yet. It is safe to try again.",
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = 12.dp),
            )
        }
        Spacer(Modifier.height(24.dp))
        Button(onClick = onOpened, enabled = !saving) {
            Text(
                if (saving) "Saving…" else if (error != null) "Try again" else "Open",
                fontSize = 18.sp,
            )
        }
    }
}

@Composable
internal fun BonusChoiceScreen(onAccept: () -> Unit, onDecline: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.fillMaxSize().safeDrawingPadding().padding(24.dp),
    ) {
        Text("Keep going?", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text(
            "Learn two more, or stop here. Today already counts either way.",
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(top = 8.dp, bottom = 24.dp),
        )
        Button(onClick = onAccept, modifier = Modifier.fillMaxWidth()) { Text("Learn two more") }
        OutlinedButton(
            onClick = onDecline,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        ) { Text("Not today") }
    }
}
