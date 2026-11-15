package app.hush

import app.hush.SpotPicker.Box
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SpotPickerTest {
    // A 1080 x 2400 phone at 2.625 px per dp: the Bubble sizes in pixels.
    private val sizes = SpotPicker.Sizes(ghost = 79, button = 63, gap = 158, space = 11, edge = 26, step = 32)
    private val screen = Box(0, 63, 1080, 2400)
    private val keyboard = 1643

    // Google Messages on the API 33 emulator (uiautomator dump): the compose field with four lines of text.
    private val messagesField = Box(187, 1360, 805, 1620)
    private val attach = Box(11, 1496, 137, 1622)
    private val emoji = Box(805, 1494, 931, 1620)
    private val send = Box(931, 1494, 1057, 1620)

    private fun scene(field: Box, buttons: List<Box>, cursorLine: Box? = null) =
        SpotPicker.Scene(screen, keyboard, field, cursorLine, buttons)

    private fun ghost(spot: Pair<Int, Int>) = Box.around(spot.first, spot.second, sizes.ghost)
    private fun cancel(spot: Pair<Int, Int>) = Box.around(spot.first - sizes.gap, spot.second, sizes.button)
    private fun confirm(spot: Pair<Int, Int>) = Box.around(spot.first + sizes.gap, spot.second, sizes.button)

    private fun assertFree(spot: Pair<Int, Int>, field: Box, buttons: List<Box>) {
        for (box in buttons + field) assertFalse("ghost at $spot covers $box", ghost(spot).overlaps(box))
        assertTrue("ghost at $spot is under the keyboard", ghost(spot).bottom <= keyboard)
        assertTrue("ghost at $spot is under the status bar", ghost(spot).top >= screen.top)
        assertTrue("X at $spot is off the screen", cancel(spot).left >= screen.left)
        assertTrue("check at $spot is off the screen", confirm(spot).right <= screen.right)
    }

    @Test
    fun messagesGoesJustAboveTheFieldAtItsRightEnd() {
        val buttons = listOf(attach, emoji, send)
        val spot = SpotPicker.pick(scene(messagesField, buttons), sizes)
        assertEquals(805 - 79 to 1360 - 11 - 79, spot)
        assertFree(spot, messagesField, buttons)
        // X and the check open above the field too, so they cover no button.
        for (box in buttons) {
            assertFalse(cancel(spot).overlaps(box))
            assertFalse(confirm(spot).overlaps(box))
        }
    }

    @Test
    fun aWideRowAboveTheFieldDoesNotPushTheGhostAway() {
        // Messages with an empty field: the "Texting with" row of the message list is clickable.
        val field = Box(302, 1494, 805, 1620)
        val row = Box(0, 1209, 1080, 1461)
        val buttons = listOf(Box(0, 1496, 126, 1622), Box(126, 1496, 263, 1622), emoji, row)
        val spot = SpotPicker.pick(scene(field, buttons), sizes)
        assertEquals(805 - 79 to 1494 - 11 - 79, spot)
    }

    @Test
    fun theOldSpotCoveredTheEmojiButton() {
        // 0.4.1 put the ghost above the keyboard on the right.
        val old = 1080 - 26 - (158 + 63) to keyboard - 58 - 63
        assertTrue(ghost(old).overlaps(emoji))
    }

    @Test
    fun aButtonAboveTheFieldMovesTheGhostAside() {
        val button = Box(650, 1220, 790, 1350)
        val buttons = listOf(attach, emoji, send, button)
        val spot = SpotPicker.pick(scene(messagesField, buttons), sizes)
        assertFree(spot, messagesField, buttons)
        assertTrue("the ghost stays near the field: $spot", spot.second > 1100)
    }

    @Test
    fun xAndCheckKeepOffButtonsWhenThereIsRoom() {
        // A button where the check would open at the preferred spot, but not under the ghost.
        val button = Box(840, 1230, 940, 1320)
        val buttons = listOf(attach, emoji, send, button)
        val spot = SpotPicker.pick(scene(messagesField, buttons), sizes)
        assertFree(spot, messagesField, buttons)
        assertFalse(confirm(spot).overlaps(button))
        assertFalse(cancel(spot).overlaps(button))
    }

    @Test
    fun aFieldAtTheTopGoesBelowIt() {
        // An address bar right under the status bar, with a button on each side.
        val field = Box(150, 80, 900, 210)
        val buttons = listOf(Box(20, 90, 140, 200), Box(910, 90, 1060, 200))
        val spot = SpotPicker.pick(scene(field, buttons), sizes)
        assertFree(spot, field, buttons)
        assertTrue("below the field: $spot", ghost(spot).top >= field.bottom)
    }

    @Test
    fun wideRowsMayBeCoveredButNotSmallButtons() {
        // Suggestion rows fill the space below an address bar. The ghost may cover a row, but no small button.
        val field = Box(150, 80, 900, 210)
        val rows = (0 until 8).map { Box(0, 230 + it * 150, 1080, 370 + it * 150) }
        val arrows = (0 until 8).map { Box(950, 250 + it * 150, 1060, 350 + it * 150) }
        val spot = SpotPicker.pick(scene(field, rows + arrows), sizes)
        for (box in arrows + field) assertFalse("ghost at $spot covers $box", ghost(spot).overlaps(box))
    }

    @Test
    fun aTallFieldTakesTheGhostInsideButNotOnTheCursorLine() {
        // A note: the field fills the screen above the keyboard. The cursor is on a line near the bottom right.
        val field = Box(0, 200, 1080, 1640)
        val cursorLine = Box(0, 1480, 1080, 1540)
        val spot = SpotPicker.pick(scene(field, emptyList(), cursorLine), sizes)
        assertFalse("ghost at $spot hides the cursor line", ghost(spot).overlaps(cursorLine))
        assertTrue("inside the field: $spot", field.contains(ghost(spot)))
        assertTrue(ghost(spot).bottom <= keyboard)
    }

    @Test
    fun aTallFieldWithAnUnknownCursorKeepsTheGhostOutside() {
        val field = Box(0, 400, 1080, 1640)
        val spot = SpotPicker.pick(scene(field, emptyList(), cursorLine = null), sizes)
        assertFalse(ghost(spot).overlaps(field))
    }

    @Test
    fun containersAndListsDoNotCount() {
        // The compose bar holds the field and is clickable. The message list fills the top of the screen.
        val bar = Box(0, 1340, 1080, 1640)
        val list = Box(0, 63, 1080, 1340)
        val spot = SpotPicker.pick(scene(messagesField, listOf(bar, list, attach, emoji, send)), sizes)
        assertEquals(805 - 79 to 1360 - 11 - 79, spot)
    }

    @Test
    fun withNoFreeSpotTheGhostGoesJustAboveTheField() {
        // Unlabelled small buttons everywhere.
        val grid = (0 until 12).flatMap { col -> (0 until 25).map { row -> Box(col * 90, 63 + row * 90, col * 90 + 80, 63 + row * 90 + 80) } }
        val buttons = grid.filter { !it.overlaps(messagesField) }
        val spot = SpotPicker.pick(scene(messagesField, buttons), sizes)
        assertEquals(805 - 79 to 1360 - 11 - 79, spot)
    }

    @Test
    fun theFallbackStaysOnTheScreen() {
        val field = Box(0, 63, 1080, 1640)
        val grid = (0 until 12).flatMap { col -> (0 until 25).map { row -> Box(col * 90, 63 + row * 90, col * 90 + 80, 63 + row * 90 + 80) } }
        val spot = SpotPicker.pick(scene(field, grid, cursorLine = Box(0, 63, 1080, 2400)), sizes)
        assertTrue(ghost(spot).top >= screen.top)
        assertTrue(ghost(spot).bottom <= keyboard)
    }

    @Test
    fun onlyFieldsThreeGhostsHighAreTall() {
        assertFalse(SpotPicker.isTall(messagesField, sizes))
        assertTrue(SpotPicker.isTall(Box(0, 200, 1080, 200 + 6 * 79), sizes))
    }
}
