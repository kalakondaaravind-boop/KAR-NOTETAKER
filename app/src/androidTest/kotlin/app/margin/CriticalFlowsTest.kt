package app.margin

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * End-to-end UI flows on a device/emulator: ./gradlew :app:connectedDebugAndroidTest
 * Uses the real app (real database), so each test uses unique text.
 */
@RunWith(AndroidJUnit4::class)
class CriticalFlowsTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    private fun skipOnboardingIfShown() {
        val skip = rule.onAllNodesWithText("Skip")
        if (skip.fetchSemanticsNodes().isNotEmpty()) skip.onFirst().performClick()
        rule.waitForIdle()
    }

    @Test fun createTypeLeaveReopen() {
        skipOnboardingIfShown()
        val marker = "uitest-" + System.nanoTime()
        rule.onNodeWithText("New note").performClick()
        rule.waitForIdle()
        rule.onNodeWithContentDescription("Note text").performTextInput("Hello $marker")
        rule.onNodeWithContentDescription("Back").performClick()
        rule.waitUntil(5_000) { rule.onAllNodesWithText("Hello $marker", substring = true).fetchSemanticsNodes().isNotEmpty() }
        rule.onAllNodesWithText("Hello $marker", substring = true).onFirst().performClick()
        rule.waitUntil(5_000) { rule.onAllNodesWithText("Hello $marker", substring = true).fetchSemanticsNodes().isNotEmpty() }
    }

    @Test fun searchFindsNewNote() {
        skipOnboardingIfShown()
        val marker = "findme" + System.nanoTime()
        rule.onNodeWithText("New note").performClick()
        rule.waitForIdle()
        rule.onNodeWithContentDescription("Note text").performTextInput(marker)
        rule.onNodeWithContentDescription("Back").performClick()
        rule.onNodeWithContentDescription("Search").performClick()
        rule.onAllNodes(hasSetTextAction()).onFirst().performTextInput(marker.take(10))
        rule.waitUntil(5_000) { rule.onAllNodesWithText(marker, substring = true).fetchSemanticsNodes().isNotEmpty() }
        rule.onAllNodesWithText(marker, substring = true).onFirst().assertIsDisplayed()
    }
}
