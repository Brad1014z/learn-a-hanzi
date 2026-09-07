package io.github.brad1014z.hanzi.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import io.github.brad1014z.hanzi.BuildConfig
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The release content gate (docs/milestones/m4.1-release-gates.md).
 *
 * A release build may only put teacher-approved characters in front of a learner. These
 * tests run in BOTH variants and assert the variant-appropriate behaviour, because the
 * interesting property is the *difference*: a debug build teaches from the engineering
 * draft, a release build teaches only what has been signed off.
 *
 * Regression guard: the gate used to live only inside `lessonContentFor`, which meant a
 * release build offered all ~181 dataset characters and then threw
 * IllegalStateException the moment one was opened — the Collection's Browse shelf shows
 * every character, so this was reachable in one tap and killed the app mid-session.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ContentGateTest {

    private lateinit var db: HanziDatabase

    @Before
    fun setUp() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(ctx, HanziDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val dao = db.contentDao()
        // 人 is in the shipped dataset but NOT in the (currently empty) signed manifest.
        dao.insertCharacters(
            listOf(
                CharacterEntity(
                    character = "人", lang = "zh-Hans", pinyin = """["rén"]""",
                    definition = "person; people", radical = "人", strokeCount = 2,
                    freqRank = 1, decomposition = null, etymologyHint = null,
                ),
            ),
        )
        dao.insertCurriculum(
            listOf(
                CurriculumEntryEntity(
                    curriculumId = "hsk", character = "人", level = 1, sequence = 1,
                    world = "starter", worldName = "Shape Shift",
                ),
            ),
        )
        dao.insertStrokes(
            listOf(
                StrokePathEntity(
                    character = "人", strokeIndex = 0,
                    pathData = "M 100 100 L 900 900", median = "[[100,100],[900,900]]",
                ),
            ),
        )
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun repository() = RoomContentRepository(
        ApplicationProvider.getApplicationContext(),
        db,
    )

    @Test
    fun onlyTeacherApprovedCharactersAreOffered() = runBlocking {
        val offered = repository().worlds().flatMap { it.characters }.map { it.character }
        if (BuildConfig.DEBUG) {
            assertEquals(
                "a debug build teaches from the engineering draft",
                listOf("人"),
                offered,
            )
        } else {
            assertTrue(
                "a release build must not offer a character with no signed lesson, " +
                    "but offered $offered",
                offered.isEmpty(),
            )
        }
    }

    @Test
    fun emptyWorldsAreNotShownAsEmptyShelves() = runBlocking {
        val worlds = repository().worlds()
        if (!BuildConfig.DEBUG) {
            assertTrue(
                "a world whose characters are all unapproved must disappear, not render empty",
                worlds.isEmpty(),
            )
        }
    }

    @Test
    fun loadingAnUnapprovedCharacterIsAnInternalErrorNotALearnerPath() = runBlocking {
        // `worlds()` never offers it, so this can only happen if a caller bypasses the
        // gate. Debug still resolves it via the engineering-draft fallback.
        val outcome = runCatching { repository().load("人") }
        if (BuildConfig.DEBUG) {
            assertTrue("debug should resolve the draft", outcome.isSuccess)
            assertEquals("rén", outcome.getOrNull()?.lessonContent?.primaryReading?.pinyin)
        } else {
            assertTrue(
                "release must refuse rather than show unreviewed content",
                outcome.exceptionOrNull() is IllegalStateException,
            )
        }
    }
}
