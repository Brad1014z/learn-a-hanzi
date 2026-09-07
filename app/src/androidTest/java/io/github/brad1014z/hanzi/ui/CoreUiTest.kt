package io.github.brad1014z.hanzi.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import io.github.brad1014z.hanzi.data.CurriculumRow
import io.github.brad1014z.hanzi.data.RoomContentRepository
import io.github.brad1014z.hanzi.engine.data.CharacterData
import io.github.brad1014z.hanzi.engine.data.LessonContent
import io.github.brad1014z.hanzi.engine.data.PrimaryReading
import io.github.brad1014z.hanzi.engine.data.PronunciationAudio
import io.github.brad1014z.hanzi.engine.data.TeacherReview
import io.github.brad1014z.hanzi.engine.geometry.Point
import io.github.brad1014z.hanzi.engine.progress.CharacterProgress
import io.github.brad1014z.hanzi.engine.speech.SpeechService
import io.github.brad1014z.hanzi.engine.svg.SvgPathParser
import org.junit.Rule
import org.junit.Test

class CoreUiTest {
    @get:Rule
    val compose = createComposeRule()

    private val person = CharacterData(
        character = "人",
        strokeOutlines = listOf(SvgPathParser.parse("M 100 100 L 900 900")),
        medians = listOf(listOf(Point(100.0, 100.0), Point(900.0, 900.0))),
        lessonContent = LessonContent(
            character = "人",
            primaryReading = PrimaryReading("rén", 2),
            learnerGloss = "person",
            pronunciationAudio = PronunciationAudio("人", "audio/person.mp3"),
            curriculumSequence = 1,
            contentVersion = "test",
            review = TeacherReview(true, "Teacher", "2026-08-24"),
        ),
    )

    @Test
    fun hubHasHonestCoreRewardAndNoFamilyOrXpPromise() {
        compose.setContent {
            MaterialTheme {
                QuestHubScreen(
                    daysPlayed = 0,
                    quest = QuestSummary(dueCount = 0, newCount = 3, backlogWarning = false),
                    currentWorldName = null,
                    currentWorldMastery = 0.0,
                    nextWorldName = null,
                    onStartQuest = {},
                    onCollection = {},
                    onSettings = {},
                )
            }
        }

        compose.onNodeWithText("Shape Shift").assertIsDisplayed()
        compose.onNodeWithText("Family").assertDoesNotExist()
        compose.onNodeWithText("XP", substring = true).assertDoesNotExist()
    }

    @Test
    fun collectionShowsEncounteredOnlyAndBrowseShowsAll() {
        val rows = listOf(
            CurriculumRow("人", "starter", "Shape Shift", 1, "[\"rén\"]", "person"),
            CurriculumRow("大", "starter", "Shape Shift", 2, "[\"dà\"]", "big"),
        )
        val world = RoomContentRepository.World("starter", "Shape Shift", rows)
        val encountered = mapOf("人" to CharacterProgress.initial("人", 0))
        compose.setContent {
            var shelf by remember { mutableStateOf(CharacterShelf.COLLECTION) }
            MaterialTheme {
                CharacterGridScreen(
                    worlds = listOf(world),
                    progress = encountered,
                    shelf = shelf,
                    onShelfChange = { shelf = it },
                    onCharacterTap = {},
                )
            }
        }

        compose.onNodeWithText("人").assertIsDisplayed()
        compose.onNodeWithText("大").assertDoesNotExist()
        compose.onNodeWithText("Browse").performClick()
        compose.onNodeWithText("大").assertIsDisplayed()
    }

    @Test
    fun coreRewardFlowsToAnExplicitAcceptOrDeclineBonusChoice() {
        compose.setContent {
            var screen by remember { mutableStateOf("chest") }
            MaterialTheme {
                when (screen) {
                    "chest" -> ChestScreen(saving = false, error = null, onOpened = { screen = "bonus" })
                    "bonus" -> BonusChoiceScreen(
                        onAccept = { screen = "accepted" },
                        onDecline = { screen = "declined" },
                    )
                    "accepted" -> Button(onClick = { screen = "bonus" }) { Text("Try stopping") }
                    else -> Text("Stopped after the completed core")
                }
            }
        }

        compose.onNodeWithText("Shape Shift complete").assertIsDisplayed()
        compose.onNodeWithText("Open").performClick()
        compose.onNodeWithText("Today already counts either way.", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Learn two more").performClick()
        compose.onNodeWithText("Try stopping").performClick()
        compose.onNodeWithText("Not today").performClick()
        compose.onNodeWithText("Stopped after the completed core").assertIsDisplayed()
    }

    @Test
    fun firstCharacterExplainsMeaningAndOffersAccessiblePronunciationBeforePractice() {
        compose.setContent {
            var practiceOpened by remember { mutableStateOf(false) }
            MaterialTheme {
                if (practiceOpened) {
                    Text("Stroke practice opened")
                } else {
                    CharacterDetailScreen(
                        character = person,
                        speech = SpeechService.Silent,
                        speechAvailable = true,
                        autoPlay = false,
                        practiceLabel = "Show me the strokes",
                        onPractice = { practiceOpened = true },
                        onExit = {},
                    )
                }
            }
        }

        compose.onNodeWithText("rén").assertIsDisplayed()
        compose.onNodeWithText("person").assertIsDisplayed()
        compose.onNodeWithContentDescription("Play pronunciation for 人").assertHasClickAction()
        compose.onNodeWithText("Show me the strokes").performClick()
        compose.onNodeWithText("Stroke practice opened").assertIsDisplayed()
    }
}
