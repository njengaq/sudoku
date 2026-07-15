package com.example.simplesudoku.solver

import org.junit.Assert.assertTrue
import org.junit.Test

class DlxSudokuSolverEscargotTest {

    // "Escargot" by Arto Inkala (2006) - long considered one of the
    // hardest hand-crafted Sudoku puzzles, designed to force a solver
    // through maximum branching before it finds the answer.
    private val escargot = arrayOf(
        intArrayOf(1,0,0, 0,0,7, 0,9,0),
        intArrayOf(0,3,0, 0,2,0, 0,0,8),
        intArrayOf(0,0,9, 6,0,0, 5,0,0),

        intArrayOf(0,0,5, 3,0,0, 9,0,0),
        intArrayOf(0,1,0, 0,8,0, 0,0,2),
        intArrayOf(6,0,0, 0,0,4, 0,0,0),

        intArrayOf(3,0,0, 0,0,0, 0,1,0),
        intArrayOf(0,4,0, 0,0,0, 0,0,7),
        intArrayOf(0,0,7, 0,0,0, 3,0,0)
    )

    @Test
    fun `solves Escargot correctly and reports timing`() {
        val solver = DlxSudokuSolver()

        val start = System.nanoTime()
        val solution = solver.solve(escargot)
        val elapsedMs = (System.nanoTime() - start) / 1_000_000.0

        println("Escargot solved in ${elapsedMs}ms")

        assertTrue("Solver should find a solution", solution != null)
        assertTrue("Solution must respect all original givens", matchesGivens(escargot, solution!!))
        assertTrue("Every row/col/box must be a valid 1-9 permutation", isValidCompletedGrid(solution))

        // Generous guard rail against a real performance regression,
        // not a tight benchmark assertion - see design notes on why
        // hard millisecond thresholds are fragile across hardware.
        assertTrue("Should not take absurdly long (possible infinite loop / bad pruning)", elapsedMs < 2000)
    }

    private fun matchesGivens(given: Array<IntArray>, solved: Array<IntArray>): Boolean {
        for (r in 0..8) for (c in 0..8) {
            if (given[r][c] != 0 && given[r][c] != solved[r][c]) return false
        }
        return true
    }

    private fun isValidCompletedGrid(grid: Array<IntArray>): Boolean {
        fun isPermutation(nums: List<Int>) = nums.sorted() == (1..9).toList()

        for (r in 0..8) if (!isPermutation(grid[r].toList())) return false
        for (c in 0..8) if (!isPermutation((0..8).map { grid[it][c] })) return false
        for (br in 0..2) for (bc in 0..2) {
            val box = mutableListOf<Int>()
            for (r in 0..2) for (c in 0..2) box.add(grid[br*3+r][bc*3+c])
            if (!isPermutation(box)) return false
        }
        return true
    }
}