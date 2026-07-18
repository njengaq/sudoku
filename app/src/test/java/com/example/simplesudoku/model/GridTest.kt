package com.example.simplesudoku.model

import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Assert.fail

class GridTest {

    /** A 9x9 array with every cell empty (0) — a blank canvas for tests. */
    private fun emptyGivens(): Array<IntArray> = Array(9) { IntArray(9) }

    @Test
    fun `fromGivens marks non-zero cells as GIVEN and zero cells as EDITABLE`() {
        val givens = emptyGivens()
        givens[0][0] = 5
        val grid = Grid.fromGivens(givens)

        val givenCell = grid.cellAt(0, 0)
        assertEquals(5, givenCell.value)
        assertEquals(CellState.GIVEN, givenCell.state)

        val emptyCell = grid.cellAt(0, 1)
        assertEquals(0, emptyCell.value)
        assertEquals(CellState.EDITABLE, emptyCell.state)
    }

    @Test
    fun `fromGivens rejects a non-9x9 array`() {
        try {
            Grid.fromGivens(Array(8) { IntArray(9) })
            fail("expected IllegalArgumentException for wrong row count")
        } catch (e: IllegalArgumentException) {
            // expected
        }
    }

    @Test
    fun `peersOf returns exactly 20 cells with no duplicates and excludes self`() {
        val grid = Grid.fromGivens(emptyGivens())
        val peers = grid.peersOf(4, 4)

        assertEquals(20, peers.size)
        assertEquals(20, peers.toSet().size) // no duplicates
        assertFalse(peers.any { it.row == 4 && it.col == 4 }) // excludes self
    }

    @Test
    fun `peersOf includes full row, column, and box`() {
        val grid = Grid.fromGivens(emptyGivens())
        val peers = grid.peersOf(0, 0)

        // Same row (row 0, cols 1-8): 8 cells
        assertTrue((1..8).all { col -> peers.any { it.row == 0 && it.col == col } })
        // Same column (col 0, rows 1-8): 8 cells
        assertTrue((1..8).all { row -> peers.any { it.row == row && it.col == 0 } })
        // Same box (box 0: rows 0-2, cols 0-2), excluding row/col already
        // counted and self -> (1,1),(1,2),(2,1),(2,2)
        val boxOnlyPeers = listOf(1 to 1, 1 to 2, 2 to 1, 2 to 2)
        assertTrue(boxOnlyPeers.all { (r, c) -> peers.any { it.row == r && it.col == c } })
    }

    @Test
    fun `peersOf does not cross box boundaries incorrectly`() {
        // Cell (2,2) is in box 0 (rows 0-2, cols 0-2). (3,3) is in box 4.
        // They share neither row, col, nor box, so should not be peers.
        val grid = Grid.fromGivens(emptyGivens())
        val peers = grid.peersOf(2, 2)
        assertFalse(peers.any { it.row == 3 && it.col == 3 })
    }

    @Test
    fun `withAnswerEntered sets value and keeps cell EDITABLE`() {
        val grid = Grid.fromGivens(emptyGivens()).withAnswerEntered(0, 0, 7)
        val cell = grid.cellAt(0, 0)
        assertEquals(7, cell.value)
        assertEquals(CellState.EDITABLE, cell.state)
    }

    @Test
    fun `withAnswerEntered throws on a GIVEN cell`() {
        val givens = emptyGivens()
        givens[0][0] = 5
        val grid = Grid.fromGivens(givens)
        try {
            grid.withAnswerEntered(0, 0, 7)
            fail("expected IllegalStateException when writing to a GIVEN cell")
        } catch (e: IllegalStateException) {
            // expected
        }
    }

    @Test
    fun `withCellSealed locks the cell and blocks further edits`() {
        val grid = Grid.fromGivens(emptyGivens())
            .withAnswerEntered(0, 0, 7)
            .withCellSealed(0, 0)

        assertEquals(CellState.SEALED, grid.cellAt(0, 0).state)

        try {
            grid.withCellCleared(0, 0)
            fail("expected IllegalStateException when clearing a SEALED cell")
        } catch (e: IllegalStateException) {
            // expected
        }
    }

    @Test
    fun `withCellSealed throws on an empty cell`() {
        val grid = Grid.fromGivens(emptyGivens())
        try {
            grid.withCellSealed(0, 0)
            fail("expected IllegalStateException when sealing an empty cell")
        } catch (e: IllegalStateException) {
            // expected
        }
    }

    @Test
    fun `withCellCleared clears value but preserves candidates`() {
        val grid = Grid.fromGivens(emptyGivens())
            .withCandidateToggled(0, 0, 3)
            .withCandidateToggled(0, 0, 7)
            .withAnswerEntered(0, 0, 9)
            .withCellCleared(0, 0)

        val cell = grid.cellAt(0, 0)
        assertEquals(0, cell.value)
        assertEquals(setOf(3, 7), cell.candidates)
    }

    @Test
    fun `withCandidateToggled adds then removes a note`() {
        var grid = Grid.fromGivens(emptyGivens())
        grid = grid.withCandidateToggled(0, 0, 4)
        assertEquals(setOf(4), grid.cellAt(0, 0).candidates)

        grid = grid.withCandidateToggled(0, 0, 4) // toggle off
        assertEquals(emptySet<Int>(), grid.cellAt(0, 0).candidates)
    }

    @Test
    fun `progress and counts follow the design doc formula (givens excluded)`() {
        val givens = emptyGivens()
        givens[0][0] = 1 // one given cell
        var grid = Grid.fromGivens(givens)

        assertEquals(1, grid.givenCount)
        assertEquals(80, grid.editableCellCount) // 81 - 1 given
        assertEquals(0, grid.sealedCount)
        assertEquals(0f, grid.progress, 0.0001f)
        assertFalse(grid.isComplete)

        grid = grid.withAnswerEntered(0, 1, 5).withCellSealed(0, 1)
        assertEquals(1, grid.sealedCount)
        assertEquals(1f / 80f, grid.progress, 0.0001f)
    }

    @Test
    fun `original grid is unchanged after a transform (immutability)`() {
        val original = Grid.fromGivens(emptyGivens())
        val modified = original.withAnswerEntered(0, 0, 5)

        assertEquals(0, original.cellAt(0, 0).value)
        assertEquals(5, modified.cellAt(0, 0).value)
    }
}