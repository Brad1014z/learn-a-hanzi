package io.github.brad1014z.hanzi.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import io.github.brad1014z.hanzi.data.CurriculumRow
import io.github.brad1014z.hanzi.data.RoomContentRepository
import io.github.brad1014z.hanzi.engine.progress.CharacterProgress
import org.junit.Rule
import org.junit.Test

class CoreUiTest {
    @get:Rule
    val compose = createComposeRule()

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
}
