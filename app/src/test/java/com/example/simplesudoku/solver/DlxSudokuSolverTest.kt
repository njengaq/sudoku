package com.example.simplesudoku.solver

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Unit tests for DlxSudokuSolver.
 * Lives in the (test) source set - runs on the JVM, no emulator needed.
 */
class DlxSudokuSolverTest {

    // A well-known "easy" puzzle with a unique solution.
    private val puzzle = arrayOf(
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

    // The known correct solution to the puzzle above.
    private val expectedSolution = arrayOf(
        intArrayOf(5,3,4, 6,7,8, 9,1,2),
        intArrayOf(6,7,2, 1,9,5, 3,4,8),
        intArrayOf(1,9,8, 3,4,2, 5,6,7),

        intArrayOf(8,5,9, 7,6,1, 4,2,3),
        intArrayOf(4,2,6, 8,5,3, 7,9,1),
        intArrayOf(7,1,3, 9,2,4, 8,5,6),

        intArrayOf(9,6,1, 5,3,7, 2,8,4),
        intArrayOf(2,8,7, 4,1,9, 6,3,5),
        intArrayOf(3,4,5, 2,8,6, 1,7,9)
    )

    @Test
    fun `solves a valid puzzle and matches the known solution`() {
        val solver = DlxSudokuSolver()
        val result = solver.solve(puzzle)

        assertNotNull("Solver returned null for a solvable puzzle", result)
        for (r in 0 until 9) {
            assertArrayEquals(
                "Row $r did not match expected solution",
                expectedSolution[r],
                result!![r]
            )
        }
    }

    @Test
    fun `returns null for an unsolvable puzzle`() {
        // Two 5s in the same row - contradicts Sudoku rules, no solution possible.
        val brokenPuzzle = Array(9) { IntArray(9) }
        brokenPuzzle[0][0] = 5
        brokenPuzzle[0][1] = 5

        val solver = DlxSudokuSolver()
        val result = solver.solve(brokenPuzzle)

        assertNull("Solver should return null for a contradictory puzzle", result)
    }

    @Test
    fun `same solver instance can solve multiple puzzles in a row`() {
        // Regression check: cover/uncover must fully restore state between calls.
        val solver = DlxSudokuSolver()

        val first = solver.solve(puzzle)
        assertNotNull(first)

        val second = solver.solve(puzzle)
        assertNotNull(second)

        for (r in 0 until 9) {
            assertArrayEquals(first!![r], second!![r])
        }
    }

    @Test
    fun `already-solved grid is returned unchanged`() {
        val solver = DlxSudokuSolver()
        val result = solver.solve(expectedSolution)

        assertNotNull(result)
        for (r in 0 until 9) {
            assertArrayEquals(expectedSolution[r], result!![r])
        }
    }
}