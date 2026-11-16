package app.hush

import org.junit.Assert.assertEquals
import org.junit.Test

class FlowLayoutTest {
    // Chips of 100 px in rows of 250 px with 10 px between them: 2 chips in each row.
    private fun flow(widths: List<Int>, maxRows: Int, placed: MutableList<Triple<Int, Int, Int>> = mutableListOf()) =
        FlowLayout.flow(widths.size, 250, 10, maxRows, { widths[it] }, { 48 }) { i, x, y -> placed += Triple(i, x, y) }

    @Test
    fun childrenGoInRowsFromTheStart() {
        val placed = mutableListOf<Triple<Int, Int, Int>>()
        assertEquals(FlowLayout.Flow(rows = 3, shown = 5, height = 144), flow(List(5) { 100 }, 0, placed))
        assertEquals(listOf(Triple(0, 0, 0), Triple(1, 110, 0), Triple(2, 0, 48), Triple(3, 110, 48), Triple(4, 0, 96)), placed)
    }

    @Test
    fun onlyTheFirstRowsShowWithMaxRows() {
        val placed = mutableListOf<Triple<Int, Int, Int>>()
        // The list still needs 3 rows, so the "Show all" button can show.
        assertEquals(FlowLayout.Flow(rows = 3, shown = 4, height = 96), flow(List(5) { 100 }, 2, placed))
        assertEquals(listOf(0, 1, 2, 3), placed.map { it.first })
    }

    @Test
    fun aListThatFitsShowsAllChildren() {
        assertEquals(FlowLayout.Flow(rows = 2, shown = 3, height = 96), flow(List(3) { 100 }, 2))
        assertEquals(FlowLayout.Flow(rows = 0, shown = 0, height = 0), flow(emptyList(), 2))
    }

    @Test
    fun aWideChildHasARowOfItsOwn() {
        assertEquals(FlowLayout.Flow(rows = 3, shown = 2, height = 96), flow(listOf(100, 300, 100), 2))
    }
}
