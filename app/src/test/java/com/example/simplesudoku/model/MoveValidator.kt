package com.example.simplesudoku.model

import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse

class MoveValidatorTest {

    private fun emptyGrid(): Grid = Grid.fromGivens(Array(9) { IntArray(9) })

    @Test
    fun `detects conflict in the same row`() {
        val grid = emptyGrid()
            .withAnswerEntered(0, 0, 5)
            .withAnswerEntered(0, 3, 5)

        val result = MoveValidator.checkConflicts(grid, 0, 0)
        assertTrue(result.hasConflict)
        assertEquals(1, result.conflictingCells.size)
        assertEquals(0, result.conflictingCells.first().row)
        assertEquals(3, result.conflictingCells.first().col)
    }

    @Test
    fun `detects conflict in the same column`() {
        val grid = emptyGrid()
            .withAnswerEntered(0, 0, 5)
            .withAnswerEntered(6, 0, 5)

        val result = MoveValidator.checkConflicts(grid, 0, 0)
        assertTrue(result.hasConflict)
        assertEquals(1, result.conflictingCells.size)
        assertEquals(6, result.conflictingCells.first().row)
    }

    @Test
    fun `detects conflict in the same box despite different row and column`() {
        // (0,0) and (2,2) are both in box 0 but share no row or column.
        val grid = emptyGrid()
            .withAnswerEntered(0, 0, 5)
            .withAnswerEntered(2, 2, 5)

        val result = MoveValidator.checkConflicts(grid, 0, 0)
        assertTrue(result.hasConflict)
        assertEquals(1, result.conflictingCells.size)
        assertEquals(2 to 2, result.conflictingCells.first().let { it.row to it.col })
    }

    @Test
    fun `no conflict for same digit in a different box, row, and column`() {
        // (0,0) and (3,3) share no row, no column, and different boxes
        // (box 0 vs box 4) - same digit here is legal.
        val grid = emptyGrid()
            .withAnswerEntered(0, 0, 5)
            .withAnswerEntered(3, 3, 5)

        val result = MoveValidator.checkConflicts(grid, 0, 0)
        assertFalse(result.hasConflict)
    }

    @Test
    fun `no conflict reported for an empty cell`() {
        val grid = emptyGrid()
        val result = MoveValidator.checkConflicts(grid, 4, 4)
        assertFalse(result.hasConflict)
    }

    @Test
    fun `ignores empty peers when checking conflicts`() {
        // Only one digit placed on the whole board - nothing to conflict with.
        val grid = emptyGrid().withAnswerEntered(4, 4, 9)
        val result = MoveValidator.checkConflicts(grid, 4, 4)
        assertFalse(result.hasConflict)
    }

    @Test
    fun `can report multiple simultaneous conflicts`() {
        // Same digit placed in both the row and the column of the target cell.
        val grid = emptyGrid()
            .withAnswerEntered(0, 0, 5)
            .withAnswerEntered(0, 5, 5) // row conflict
            .withAnswerEntered(5, 0, 5) // column conflict

        val result = MoveValidator.checkConflicts(grid, 0, 0)
        assertEquals(2, result.conflictingCells.size)
    }

    @Test
    fun `wouldConflict checks a hypothetical digit before commit`() {
        val grid = emptyGrid().withAnswerEntered(0, 0, 5)

        assertTrue(MoveValidator.wouldConflict(grid, 0, 3, 5)) // same row, same digit
        assertFalse(MoveValidator.wouldConflict(grid, 0, 3, 6)) // same row, different digit
        assertFalse(MoveValidator.wouldConflict(grid, 3, 3, 5)) // different row/col/box
    }
}