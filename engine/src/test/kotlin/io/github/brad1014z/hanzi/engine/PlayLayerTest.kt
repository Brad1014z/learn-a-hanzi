package io.github.brad1014z.hanzi.engine

import io.github.brad1014z.hanzi.engine.play.Levels
import io.github.brad1014z.hanzi.engine.play.QuestBuilder
import io.github.brad1014z.hanzi.engine.play.QuestSession
import io.github.brad1014z.hanzi.engine.play.QuestStep
import io.github.brad1014z.hanzi.engine.play.Rank
import io.github.brad1014z.hanzi.engine.play.Ranks
import io.github.brad1014z.hanzi.engine.play.XpConfig
import io.github.brad1014z.hanzi.engine.play.unlockedWorldCount
import io.github.brad1014z.hanzi.engine.progress.CharacterProgress
import io.github.brad1014z.hanzi.engine.progress.SrsEngine
import io.github.brad1014z.hanzi.engine.progress.SrsState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class PlayLayerTest {

    private val t0 = 1_700_000_000_000L

    private fun learned(char: String, reviews: Int): CharacterProgress {
        var p = SrsEngine.apply(null, char, 5, t0)
        p = SrsEngine.apply(p, char, 5, p.dueAt)
        p = SrsEngine.apply(p, char, 5, p.dueAt) // graduated (Bronze)
        repeat(reviews) { p = SrsEngine.apply(p, char, 5, p.dueAt) }
        return p
    }

    // ----- Ranks (spec 04: derived only from SRS state) ------------------------

    @Test
    fun `ranks derive from SRS state exactly as spec 04 pins them`() {
        assertEquals(Rank.NONE, Ranks.of(null).rank)
        val learning = SrsEngine.apply(null, "火", 5, t0)
        assertEquals(Rank.NONE, Ranks.of(learning).rank)

        val bronze = learned("火", reviews = 0)
        assertEquals(Rank.BRONZE, Ranks.of(bronze).rank)

        val silver = learned("火", reviews = 2)
        assertEquals(Rank.SILVER, Ranks.of(silver).rank)

        val gold = learned("火", reviews = 6) // intervals: 1,6,6·EF… crosses 21d
        assertTrue(gold.intervalDays >= Ranks.GOLD_INTERVAL_DAYS)
        assertEquals(Rank.GOLD, Ranks.of(gold).rank)
    }

    @Test
    fun `a lapse dims the rank until re-proven`() {
        val gold = learned("火", reviews = 6)
        val lapsed = SrsEngine.apply(gold, "火", 1, gold.dueAt)
        assertEquals(SrsState.RELEARNING, lapsed.state)
        val rank = Ranks.of(lapsed)
        assertTrue(rank.dimmed)
        assertEquals(Rank.BRONZE, rank.rank)
    }

    @Test
    fun `world unlocking needs 80 percent Bronze-or-better in the previous world`() {
        assertEquals(1, unlockedWorldCount(listOf(0.5, 0.0, 0.0)))
        assertEquals(2, unlockedWorldCount(listOf(0.85, 0.0, 0.0)))
        assertEquals(3, unlockedWorldCount(listOf(1.0, 0.8, 0.1)))
        assertEquals(1, unlockedWorldCount(emptyList()))
    }

    // ----- XP & levels (spec 10: effort economy) --------------------------------

    @Test
    fun `levels ramp gently and never go backwards`() {
        assertEquals(1, Levels.levelFor(0))
        assertEquals(1, Levels.levelFor(99))
        assertEquals(2, Levels.levelFor(100))
        assertEquals(3, Levels.levelFor(225)) // 100 + 125
        val p = Levels.progressFor(250)
        assertEquals(3, p.level)
        assertEquals(25, p.intoLevel)
        assertEquals(150, p.neededForNext)
    }

    // ----- Quest builder (spec 04 session shape) ---------------------------------

    private fun due(char: String, interval: Double, dueAt: Long) =
        CharacterProgress.initial(char, t0).copy(
            state = SrsState.REVIEW, intervalDays = interval, dueAt = dueAt, reps = 3,
        )

    @Test
    fun `quest is warm-up then reviews then new then boss`() {
        val plan = QuestBuilder.build(
            due = listOf(due("一", 6.0, t0 - 2), due("火", 21.0, t0 - 1), due("我", 1.0, t0 - 3)),
            newCandidates = listOf("你", "好"),
            remainingNewCap = 2,
        )
        val steps = plan.steps
        assertIs<QuestStep.WarmUp>(steps[0])
        assertEquals("火", steps[0].character) // best-known due card opens the session
        assertEquals(listOf("我", "一"), steps.subList(1, 3).map { it.character }) // by dueAt
        assertIs<QuestStep.NewChar>(steps[3])
        assertIs<QuestStep.NewChar>(steps[4])
        val boss = steps.last()
        assertIs<QuestStep.Boss>(boss)
        assertEquals("好", boss.character) // freshest material
        assertFalse(plan.backlogWarning)
    }

    @Test
    fun `no due cards means a new-characters-plus-boss quest`() {
        val plan = QuestBuilder.build(due = emptyList(), newCandidates = listOf("你"), remainingNewCap = 5)
        assertEquals(2, plan.steps.size)
        assertIs<QuestStep.NewChar>(plan.steps[0])
        assertIs<QuestStep.Boss>(plan.steps[1])
    }

    @Test
    fun `nothing due and no headroom means an empty quest`() {
        assertTrue(QuestBuilder.build(emptyList(), emptyList(), 10).isEmpty)
    }

    @Test
    fun `a big backlog suppresses new characters and warns`() {
        val backlog = (1..101).map { due("字$it", 1.0, t0 - it) }
        val plan = QuestBuilder.build(backlog, listOf("你"), remainingNewCap = 10)
        assertTrue(plan.backlogWarning)
        assertTrue(plan.steps.none { it is QuestStep.NewChar })
    }

    // ----- Quest session (re-tests at the tail, XP, chest) ------------------------

    @Test
    fun `new characters re-test at the tail but before the boss`() {
        val plan = QuestBuilder.build(emptyList(), listOf("你"), remainingNewCap = 1)
        var s = QuestSession.start(plan)
        assertIs<QuestStep.NewChar>(s.current)
        s = s.advance(grade = 5)
        // Re-test inserted before the boss.
        assertIs<QuestStep.ReTest>(s.current)
        s = s.advance(grade = 5)
        assertIs<QuestStep.Boss>(s.current)
        s = s.advance(grade = 5)
        assertTrue(s.readyForChest)
    }

    @Test
    fun `failed reviews re-queue, capped so a hard card cannot loop forever`() {
        val plan = QuestBuilder.build(listOf(due("一", 6.0, t0 - 1)), emptyList(), 0)
        var s = QuestSession.start(plan)
        assertIs<QuestStep.WarmUp>(s.current)
        s = s.advance(grade = 1) // fail → re-test
        assertIs<QuestStep.ReTest>(s.current)
        s = s.advance(grade = 1) // fail again → second (last) re-test
        assertIs<QuestStep.ReTest>(s.current)
        s = s.advance(grade = 1) // capped: no third re-test
        assertIs<QuestStep.Boss>(s.current)
    }

    @Test
    fun `every step pays XP and the chest pays on top - hard days still pay`() {
        val plan = QuestBuilder.build(listOf(due("一", 6.0, t0 - 1)), listOf("你"), 1)
        var s = QuestSession.start(plan)
        while (!s.readyForChest) s = s.advance(grade = 1) // grind through failing everything
        val beforeChest = s.xpEarned
        assertTrue(beforeChest >= XpConfig.WARM_UP + XpConfig.NEW_CHARACTER + XpConfig.BOSS_ATTEMPTED)
        s = s.openChest()
        assertEquals(beforeChest + XpConfig.CHEST_OPENED, s.xpEarned)
        assertTrue(s.chestOpened)
    }
}
