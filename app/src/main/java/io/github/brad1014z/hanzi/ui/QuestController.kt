package io.github.brad1014z.hanzi.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import io.github.brad1014z.hanzi.data.Sessions
import io.github.brad1014z.hanzi.engine.data.CharacterData
import io.github.brad1014z.hanzi.engine.data.LessonContentRepository
import io.github.brad1014z.hanzi.engine.play.CardCompletion
import io.github.brad1014z.hanzi.engine.play.ChestCompletion
import io.github.brad1014z.hanzi.engine.play.DailyQuestPlan
import io.github.brad1014z.hanzi.engine.play.QuestSession
import io.github.brad1014z.hanzi.engine.play.QuestStep
import io.github.brad1014z.hanzi.engine.play.QuestStore
import io.github.brad1014z.hanzi.engine.play.Rank
import io.github.brad1014z.hanzi.engine.play.XpConfig
import io.github.brad1014z.hanzi.engine.progress.PracticeRecord
import java.util.UUID
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class RankUp(val character: String, val rank: Rank)

enum class QuestPhase { LOADING, PLAYING, CHEST, BONUS_OFFER, COMPLETE, FAILED }

sealed interface CommitStatus {
    data object Idle : CommitStatus
    data object Saving : CommitStatus
    data object Saved : CommitStatus
    data class Failed(val message: String) : CommitStatus
}

data class QuestUiState(
    val title: String,
    val phase: QuestPhase = QuestPhase.LOADING,
    val session: QuestSession,
    val character: CharacterData? = null,
    val showIntroduction: Boolean = false,
    val commitStatus: CommitStatus = CommitStatus.Idle,
    val rankUps: List<RankUp> = emptyList(),
    val bonusAvailable: Boolean = false,
    val isBonus: Boolean = false,
    val message: String? = null,
) {
    val currentStep: QuestStep? get() = session.current
}

sealed interface QuestAction {
    data object IntroductionFinished : QuestAction
    data class CardFinished(val record: PracticeRecord) : QuestAction
    data object RetryCardCommit : QuestAction
    data object NextCard : QuestAction
    data object OpenChest : QuestAction
    data object AcceptBonus : QuestAction
    data object DeclineBonus : QuestAction
}

/**
 * Lifecycle owner for the complete quest loop. State advances only after [QuestStore]
 * confirms the matching idempotent write, so recomposition and fast taps cannot outrun
 * persistence.
 */
class QuestController internal constructor(
    private val plan: DailyQuestPlan,
    private val contentRepository: LessonContentRepository,
    private val questStore: QuestStore,
    private val now: () -> Long,
    private val newId: () -> String,
    private val dispatcher: CoroutineDispatcher,
) : ViewModel() {

    constructor(
        plan: DailyQuestPlan,
        contentRepository: LessonContentRepository,
        questStore: QuestStore,
    ) : this(
        plan = plan,
        contentRepository = contentRepository,
        questStore = questStore,
        now = System::currentTimeMillis,
        newId = { UUID.randomUUID().toString() },
        dispatcher = Dispatchers.IO,
    )

    private val mutableState = MutableStateFlow(
        QuestUiState(title = plan.title, session = QuestSession.start(plan.core)),
    )
    val state: StateFlow<QuestUiState> = mutableState.asStateFlow()

    private var attemptId: String = newId()
    private var pendingCompletion: CardCompletion? = null
    private val chestCompletionId: String = "core-" + Instant.ofEpochMilli(now())
        .atZone(ZoneId.systemDefault())
        .toLocalDate()

    init {
        loadCurrentCard()
    }

    fun onAction(action: QuestAction) {
        when (action) {
            QuestAction.IntroductionFinished -> mutableState.update { it.copy(showIntroduction = false) }
            is QuestAction.CardFinished -> beginCardCommit(action.record)
            QuestAction.RetryCardCommit -> pendingCompletion?.let(::commitCard)
            QuestAction.NextCard -> advanceAfterCommit()
            QuestAction.OpenChest -> openChest()
            QuestAction.AcceptBonus -> startBonus()
            QuestAction.DeclineBonus -> mutableState.update {
                it.copy(phase = QuestPhase.COMPLETE, bonusAvailable = false)
            }
        }
    }

    private fun beginCardCommit(record: PracticeRecord) {
        val current = mutableState.value
        val step = current.currentStep ?: return
        if (current.commitStatus !is CommitStatus.Idle || record.character != step.character) return
        val completion = CardCompletion(
            attemptId = attemptId,
            character = record.character,
            grade = record.grade,
            drawnCorrectly = record.drawnCorrectly,
            durationMs = record.durationMs,
            sessionTag = step.sessionTag(),
            reviewedAt = record.reviewedAt,
            xpDelta = current.session.xpForCurrentStep(),
        )
        pendingCompletion = completion
        commitCard(completion)
    }

    private fun commitCard(completion: CardCompletion) {
        mutableState.update { it.copy(commitStatus = CommitStatus.Saving, message = null) }
        viewModelScope.launch(dispatcher) {
            runCatching { questStore.commitCard(completion) }
                .onSuccess { result ->
                    mutableState.update { current ->
                        val rankUps = if (
                            result.currentRank.rank > result.previousRank.rank && !result.currentRank.dimmed
                        ) {
                            current.rankUps + RankUp(completion.character, result.currentRank.rank)
                        } else {
                            current.rankUps
                        }
                        current.copy(commitStatus = CommitStatus.Saved, rankUps = rankUps)
                    }
                }
                .onFailure { error ->
                    mutableState.update {
                        it.copy(
                            commitStatus = CommitStatus.Failed(
                                error.message ?: "Progress could not be saved.",
                            ),
                            message = "Your progress is still here. Try saving again.",
                        )
                    }
                }
        }
    }

    private fun advanceAfterCommit() {
        val current = mutableState.value
        val completion = pendingCompletion ?: return
        if (current.commitStatus !is CommitStatus.Saved) return
        val advanced = current.session.advance(completion.grade)
        pendingCompletion = null
        attemptId = newId()
        if (advanced.current == null) {
            mutableState.update {
                it.copy(
                    session = advanced,
                    character = null,
                    commitStatus = CommitStatus.Idle,
                    phase = if (it.isBonus) QuestPhase.COMPLETE else QuestPhase.CHEST,
                )
            }
        } else {
            mutableState.update {
                it.copy(session = advanced, character = null, commitStatus = CommitStatus.Idle)
            }
            loadCurrentCard()
        }
    }

    private fun loadCurrentCard() {
        val step = mutableState.value.currentStep ?: return
        mutableState.update { it.copy(phase = QuestPhase.LOADING, character = null, message = null) }
        viewModelScope.launch(dispatcher) {
            runCatching { contentRepository.load(step.character) }
                .onSuccess { data ->
                    mutableState.update {
                        it.copy(
                            phase = QuestPhase.PLAYING,
                            character = data,
                            showIntroduction = step is QuestStep.NewChar,
                        )
                    }
                }
                .onFailure { error ->
                    mutableState.update {
                        it.copy(
                            phase = QuestPhase.FAILED,
                            message = error.message ?: "Lesson content could not be opened.",
                        )
                    }
                }
        }
    }

    private fun openChest() {
        val current = mutableState.value
        if (current.phase != QuestPhase.CHEST || current.commitStatus is CommitStatus.Saving) return
        mutableState.update { it.copy(commitStatus = CommitStatus.Saving, message = null) }
        val completion = ChestCompletion(
            completionId = chestCompletionId,
            completedAt = now(),
            xpDelta = XpConfig.CHEST_OPENED,
        )
        viewModelScope.launch(dispatcher) {
            runCatching { questStore.openChest(completion) }
                .onSuccess {
                    val opened = mutableState.value.session.openChest()
                    val hasBonus = plan.bonus?.isEmpty == false
                    mutableState.update { state ->
                        state.copy(
                            session = opened,
                            phase = if (hasBonus) QuestPhase.BONUS_OFFER else QuestPhase.COMPLETE,
                            bonusAvailable = hasBonus,
                            commitStatus = CommitStatus.Idle,
                        )
                    }
                }
                .onFailure { error ->
                    mutableState.update {
                        it.copy(
                            commitStatus = CommitStatus.Failed(error.message ?: "Reward could not be saved."),
                            message = "The reward is safe to retry.",
                        )
                    }
                }
        }
    }

    private fun startBonus() {
        val bonus = plan.bonus ?: return
        if (mutableState.value.phase != QuestPhase.BONUS_OFFER) return
        pendingCompletion = null
        attemptId = newId()
        mutableState.update {
            it.copy(
                session = QuestSession.start(bonus),
                phase = QuestPhase.LOADING,
                character = null,
                showIntroduction = false,
                commitStatus = CommitStatus.Idle,
                bonusAvailable = false,
                isBonus = true,
            )
        }
        loadCurrentCard()
    }

    companion object {
        fun factory(
            plan: DailyQuestPlan,
            contentRepository: LessonContentRepository,
            questStore: QuestStore,
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                QuestController(plan, contentRepository, questStore) as T
        }
    }
}

private fun QuestStep.sessionTag(): String = when (this) {
    is QuestStep.WarmUp -> Sessions.QUEST_WARMUP
    is QuestStep.Review -> Sessions.QUEST_REVIEW
    is QuestStep.NewChar -> Sessions.QUEST_NEW
    is QuestStep.ReTest -> Sessions.QUEST_RETEST
    is QuestStep.Boss -> Sessions.QUEST_BOSS
}
