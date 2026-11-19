package com.mapledger.app

import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.printToLog
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Launches the real activity and visits every top-level screen to catch runtime/composition crashes. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp-xxhdpi") // Galaxy S-series phone size
@org.robolectric.annotation.LooperMode(org.robolectric.annotation.LooperMode.Mode.PAUSED)
class UiSmokeTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    private fun waitFor(text: String) = try {
        rule.waitUntil(15_000) { rule.onAllNodes(hasText(text, substring = true)).fetchSemanticsNodes().isNotEmpty() }
    } catch (e: Throwable) {
        rule.onRoot().printToLog("UiSmokeTest")
        throw AssertionError("Text not found: '$text'", e)
    }
    private fun tab(label: String) { rule.onAllNodesWithText(label).onFirst().performClick(); rule.waitForIdle() }

    @Test fun everyScreenRenders() {
        waitFor("mileage")              // dashboard
        tab("Drive"); waitFor("START MAPPING SESSION")
        tab("Trips"); waitFor("No trips yet")
        tab("Bee Maps"); waitFor("Bursts")
        tab("Goals"); waitFor("road goals")
        tab("Reconcile"); waitFor("expected vs actual")
        tab("Routes"); waitFor("Import planned route")
        tab("Money"); waitFor("Totals")
        tab("Expenses"); waitFor("by category")
        tab("Reports"); waitFor("Cash view")
        tab("Tax"); waitFor("Tax Center")
        tab("Settings"); waitFor("Vehicles"); waitFor("Elantra Hybrid")
    }
}

/** Same journey on an unfolded Galaxy Z Fold inner display (navigation rail layout). */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w673dp-h841dp-xhdpi")
class FoldableSmokeTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    @Test fun driveScreenOnFoldable() {
        rule.waitUntil(10_000) { rule.onAllNodes(hasText("mileage", substring = true)).fetchSemanticsNodes().isNotEmpty() }
        rule.onAllNodesWithText("Drive").onFirst().performClick()
        rule.waitUntil(10_000) { rule.onAllNodes(hasText("START MAPPING SESSION")).fetchSemanticsNodes().isNotEmpty() }
    }
}
