package com.tradelikeahedgefund.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.tradelikeahedgefund.app.ui.PortfolioScreen
import org.junit.Rule
import org.junit.Test

class PortfolioUiTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun testPortfolioScreenDisplaysTitleAndSections() {
        composeTestRule.setContent {
            PortfolioScreen()
        }

        composeTestRule.onNodeWithText("Portfolio").assertIsDisplayed()
        composeTestRule.onNodeWithText("Total value").assertIsDisplayed()
        composeTestRule.onNodeWithText("Positions").assertIsDisplayed()
        composeTestRule.onNodeWithText("Watchlist").assertIsDisplayed()
    }

    @Test
    fun testTickerSuggestionsAppearOnTyping() {
        composeTestRule.setContent {
            PortfolioScreen()
        }

        // Open Add Watchlist dialog
        composeTestRule.onNodeWithText("Add to watchlist").performClick()

        // Type "AA" in Ticker input
        composeTestRule.onNodeWithText("Ticker").performTextInput("AA")

        // Suggestion "AAPL" "Apple Inc." should be displayed
        composeTestRule.onNodeWithText("AAPL").assertIsDisplayed()
        composeTestRule.onNodeWithText("Apple Inc.").assertIsDisplayed()
    }
}
