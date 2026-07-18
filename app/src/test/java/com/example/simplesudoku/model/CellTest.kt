package com.example.simplesudoku.model

import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Assert.fail

class CellTest {

    @Test
    fun `boxIndex is correct for all nine boxes`() {
        // One representative cell per box, checked against the expected
        // row-major box index (0 = top-left box, 8 = bottom-right box).
        val expected = mapOf(
            (0 to 0) to 0, (0 to 4) to 1, (0 to 8) to 2,
            (4 to 0) to 3, (4 to 4) to 4, (4 to 8) to 5,
            (8 to 0) to 6, (8 to 4) to 7, (8 to 8) to 8
        )
        expected.forEach { (rc, box) ->
            val (row, col) = rc
            val cell = Cell(row = row, col = col)
            assertEquals("cell ($row,$col) should be in box $box", box, cell.boxIndex)
        }
    }

    @Test
    fun `boxIndex is consistent across every cell in a box, not just corners`() {
        // Every cell in box 4 (the center box, rows 3-5, cols 3-5) should
        // report boxIndex == 4. This is the case most likely to expose an
        // off-by-one, since box 4 doesn't touch row 0 or col 0.
        for (row in 3..5) {
            for (col in 3..5) {
                val cell = Cell(row = row, col = col)
                assertEquals("cell ($row,$col) should be in box 4", 4, cell.boxIndex)
            }
        }
    }

    @Test
    fun `isEmpty reflects value`() {
        assertTrue(Cell(row = 0, col = 0, value = 0).isEmpty)
        assertFalse(Cell(row = 0, col = 0, value = 5).isEmpty)
    }

    @Test
    fun `isLocked is true only for GIVEN and SEALED`() {
        assertTrue(Cell(row = 0, col = 0, value = 5, state = CellState.GIVEN).isLocked)
        assertTrue(Cell(row = 0, col = 0, value = 5, state = CellState.SEALED).isLocked)
        assertFalse(Cell(row = 0, col = 0, value = 5, state = CellState.EDITABLE).isLocked)
    }

    @Test
    fun `hasPendingWrongEntry is true only for a non-empty EDITABLE cell`() {
        assertTrue(Cell(row = 0, col = 0, value = 5, state = CellState.EDITABLE).hasPendingWrongEntry)
        assertFalse(Cell(row = 0, col = 0, value = 0, state = CellState.EDITABLE).hasPendingWrongEntry)
        assertFalse(Cell(row = 0, col = 0, value = 5, state = CellState.GIVEN).hasPendingWrongEntry)
        assertFalse(Cell(row = 0, col = 0, value = 5, state = CellState.SEALED).hasPendingWrongEntry)
    }

    @Test
    fun `rejects out-of-range row`() {
        try {
            Cell(row = 9, col = 0)
            fail("expected IllegalArgumentException for row = 9")
        } catch (e: IllegalArgumentException) {
            // expected
        }
    }

    @Test
    fun `rejects out-of-range col`() {
        try {
            Cell(row = 0, col = -1)
            fail("expected IllegalArgumentException for col = -1")
        } catch (e: IllegalArgumentException) {
            // expected
        }
    }

    @Test
    fun `rejects out-of-range value`() {
        try {
            Cell(row = 0, col = 0, value = 10)
            fail("expected IllegalArgumentException for value = 10")
        } catch (e: IllegalArgumentException) {
            // expected
        }
    }

    @Test
    fun `rejects out-of-range candidates`() {
        try {
            Cell(row = 0, col = 0, candidates = setOf(1, 5, 10))
            fail("expected IllegalArgumentException for candidate = 10")
        } catch (e: IllegalArgumentException) {
            // expected
        }
    }

    @Test
    fun `accepts value 0 as valid empty marker`() {
        // 0 is a legal value (empty), shouldn't throw.
        Cell(row = 0, col = 0, value = 0)
    }
}