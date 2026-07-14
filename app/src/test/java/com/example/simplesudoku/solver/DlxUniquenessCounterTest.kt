package com.example.simplesudoku.solver

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DlxUniquenessCounterTest {

    // Same known-good puzzle from DlxSudokuSolverTest - has exactly one solution.
    private val uniquePuzzle = arrayOf(
        intArrayOf(5,3,0, 0,7,0, 0,0,0),
        intArrayOf(6,0,0, 1,9,5, 0,0,0),
        intArrayOf(0,9,8, 0,0,0, 0,6,0),

        intArrayOf(8,0,0, 0,6,0, 0,0,3),
        intArrayOf(4,0,0, 8,0,3, 0,0,1),
        intArrayOf(7,0,0, 0,2,0, 0,0,6),

        intArrayOf(0,6,0, 0,0,0, 2,8,0),
        intArrayOf(0,0,0, 4,1,9, 0,0,5),
        intArrayOf(0,0,0, 0,8,0, 0,7,9)
    )

    @Test
    fun `known unique puzzle reports exactly one solution`() {
        val counter = DlxUniquenessCounter()
        assertTrue(counter.hasUniqueSolution(uniquePuzzle))
        assertEquals(1, counter.countSolutions(uniquePuzzle))
    }

    @Test
    fun `blank grid has many solutions, not unique`() {
        // An empty 9x9 grid has thousands of valid completions.
        val blank = Array(9) { IntArray(9) }
        val counter = DlxUniquenessCounter()

        assertFalse(counter.hasUniqueSolution(blank))
        assertEquals(2, counter.countSolutions(blank, cap = 2)) // stops early at cap
    }

    @Test
    fun `contradictory puzzle has zero solutions`() {
        val broken = Array(9) { IntArray(9) }
        broken[0][0] = 5
        broken[0][1] = 5 // duplicate in same row - impossible

        val counter = DlxUniquenessCounter()
        assertEquals(0, counter.countSolutions(broken))
        assertFalse(counter.hasUniqueSolution(broken))
    }

    @Test
    fun `removing one clue from a unique puzzle can create multiple solutions`() {
        // Sanity check that the counter actually distinguishes cases, not
        // just always returning 1. Punching extra holes in a valid puzzle
        // often (not always) breaks uniqueness - this puzzle with several
        // clues removed is expected to have more than one solution.
        val loosened = uniquePuzzle.map { it.copyOf() }.toTypedArray()
        loosened[0][2] = 0 // clear a cell that was empty already - no-op guard
        // Remove several actual givens to loosen constraints meaningfully.
        loosened[0][0] = 0
        loosened[1][0] = 0
        loosened[3][0] = 0

        val counter = DlxUniquenessCounter()
        val count = counter.countSolutions(loosened, cap = 2)

        // With this many clues removed from just one region, this puzzle
        // is expected to admit more than one completion.
        assertTrue("Expected multiple solutions after loosening clues", count >= 2)
    }

    @Test
    fun `same instance can be reused across multiple puzzles`() {
        val counter = DlxUniquenessCounter()

        val firstCount = counter.countSolutions(uniquePuzzle)
        assertEquals(1, firstCount)

        val secondCount = counter.countSolutions(uniquePuzzle)
        assertEquals(1, secondCount)

        val blank = Array(9) { IntArray(9) }
        val thirdCount = counter.countSolutions(blank, cap = 2)
        assertEquals(2, thirdCount)
    }
}