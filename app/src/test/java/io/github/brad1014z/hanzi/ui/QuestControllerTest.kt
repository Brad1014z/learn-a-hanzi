package io.github.brad1014z.hanzi.ui

import io.github.brad1014z.hanzi.engine.data.CharacterData
import io.github.brad1014z.hanzi.engine.data.LessonContentRepository
import io.github.brad1014z.hanzi.engine.geometry.Point
import io.github.brad1014z.hanzi.engine.play.CardCommitResult
import io.github.brad1014z.hanzi.engine.play.CardCompletion
import io.github.brad1014z.hanzi.engine.play.ChestCommitResult
import io.github.brad1014z.hanzi.engine.play.ChestCompletion
import io.github.brad1014z.hanzi.engine.play.DailyQuestPlan
import io.github.brad1014z.hanzi.engine.play.QuestPlan
import io.github.brad1014z.hanzi.engine.play.QuestStep
import io.github.brad1014z.hanzi.engine.play.QuestStore
import io.github.brad1014z.hanzi.engine.play.RankState
import io.github.brad1014z.hanzi.engine.play.TodayProgress
import io.github.brad1014z.hanzi.engine.progress.PracticeRecord
import io.github.brad1014z.hanzi.engine.svg.SvgPathParser
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

@OptIn(ExperimentalCoroutinesApi::class)
class QuestControllerTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setMain() = Dispatchers.setMain(dispatcher)

    @After
    fun resetMain() = Dispatchers.resetMain()

    private val character = CharacterData(
        character = "人",
        strokeOutlines = listOf(SvgPathParser.parse("M 0 0 L 10 10")),
        medians = listOf(listOf(Point(0.0, 0.0), Point(10.0, 10.0))),
    )
    private val content = object : LessonContentRepository {
        override suspend fun load(character: String) = this@QuestControllerTest.character
    }

    private class FakeStore : QuestStore {
        val cards = mutableListOf<CardCompletion>()
        val chests = mutableListOf<ChestCompletion>()
        var gate: CompletableDeferred<Unit>? = null
        var failCards = 0

        override suspend fun loadToday(now: Long) = TodayProgress(emptyList(), emptyMap(), 0, 0, 0)

        override suspend fun commitCard(completion: CardCompletion): CardCommitResult {
            cards += completion
            gate?.await()
            if (failCards-- > 0) error("disk full")
            return CardCommitResult(true, RankState(io.github.brad1014z.hanzi.engine.play.Rank.NONE), RankState(io.github.brad1014z.hanzi.engine.play.Rank.NONE), 15)
        }

        override suspend fun openChest(completion: ChestCompletion): ChestCommitResult {
            chests += completion
            return ChestCommitResult(true, 30)
        }

        override suspend fun resetAllProgress() = Unit
    }

    private fun record() = PracticeRecord("人", 1_000, 5, true, 500, "ignored")

    @Test
    fun `next cannot advance while the card transaction is unfinished`() = runTest(dispatcher) {
        val store = FakeStore().apply { gate = CompletableDeferred() }
        val plan = DailyQuestPlan("Shape Shift", QuestPlan(listOf(QuestStep.Boss("人")), false), null)
        val controller = QuestController(plan, content, store, { 2_000 }, { "id" }, dispatcher)
        advanceUntilIdle()

        controller.onAction(QuestAction.CardFinished(record()))
        runCurrent()
        assertIs<CommitStatus.Saving>(controller.state.value.commitStatus)
        controller.onAction(QuestAction.NextCard)
        assertEquals(0, controller.state.value.session.index)

        store.gate!!.complete(Unit)
        advanceUntilIdle()
        assertIs<CommitStatus.Saved>(controller.state.value.commitStatus)
        controller.onAction(QuestAction.NextCard)
        assertEquals(1, controller.state.value.session.index)
        assertEquals(QuestPhase.CHEST, controller.state.value.phase)
    }

    @Test
    fun `retry reuses the same attempt id`() = runTest(dispatcher) {
        val store = FakeStore().apply { failCards = 1 }
        val plan = DailyQuestPlan("Shape Shift", QuestPlan(listOf(QuestStep.Boss("人")), false), null)
        val controller = QuestController(plan, content, store, { 2_000 }, { "stable-id" }, dispatcher)
        advanceUntilIdle()

        controller.onAction(QuestAction.CardFinished(record()))
        advanceUntilIdle()
        assertIs<CommitStatus.Failed>(controller.state.value.commitStatus)
        controller.onAction(QuestAction.RetryCardCommit)
        advanceUntilIdle()

        assertIs<CommitStatus.Saved>(controller.state.value.commitStatus)
        assertEquals(listOf("stable-id", "stable-id"), store.cards.map { it.attemptId })
    }

    @Test
    fun `declining the post-chest bonus completes without another write`() = runTest(dispatcher) {
        var id = 0
        val store = FakeStore()
        val plan = DailyQuestPlan(
            "Shape Shift",
            QuestPlan(listOf(QuestStep.Boss("人")), false),
            QuestPlan(listOf(QuestStep.NewChar("大")), false),
        )
        val controller = QuestController(plan, content, store, { 2_000 }, { "id-${id++}" }, dispatcher)
        advanceUntilIdle()
        controller.onAction(QuestAction.CardFinished(record()))
        advanceUntilIdle()
        controller.onAction(QuestAction.NextCard)
        controller.onAction(QuestAction.OpenChest)
        advanceUntilIdle()
        assertEquals(QuestPhase.BONUS_OFFER, controller.state.value.phase)

        controller.onAction(QuestAction.DeclineBonus)

        assertEquals(QuestPhase.COMPLETE, controller.state.value.phase)
        assertEquals(1, store.cards.size)
        assertEquals(1, store.chests.size)
    }
}
