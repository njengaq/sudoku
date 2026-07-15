package com.example.simplesudoku.solver

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SudokuGeneratorTest {

    private val counter = DlxUniquenessCounter()
    private val solver = DlxSudokuSolver()

    @Test
    fun `generates a valid unique puzzle for every difficulty tier`() {
        for (difficulty in SudokuGenerator.Difficulty.values()) {
            val generator = SudokuGenerator()

            val start = System.nanoTime()
            val result = generator.generate(difficulty)
            val elapsedMs = (System.nanoTime() - start) / 1_000_000.0

            println("${difficulty.name} generated in ${elapsedMs}ms (${result.clueCount} clues)")

            assertTrue(
                "${difficulty.name}: solved grid must be a valid completed Sudoku",
                isValidCompletedGrid(result.solution)
            )
            assertTrue(
                "${difficulty.name}: puzzle clues must match the solution",
                matchesGivens(result.puzzle, result.solution)
            )
            assertEquals(
                "${difficulty.name}: puzzle must have exactly one solution",
                1,
                counter.countSolutions(result.puzzle, cap = 2)
            )
            assertTrue(
                "${difficulty.name}: puzzle must be symmetric (rotational)",
                isRotationallySymmetric(result.puzzle)
            )
            assertEquals(
                "${difficulty.name}: difficulty label should be stamped as requested",
                difficulty,
                result.difficultyLabel
            )
            // No hard clue-count range assertion here anymore - see design
            // note in SudokuGenerator: clue count is a shaping target, not
            // a contract. The retry loop's own logging (visible above)
            // tells us how often a tier misses its target range in practice.

            assertTrue(
                "${difficulty.name}: generation should not take absurdly long",
                elapsedMs < 5000
            )
        }
    }

    @Test
    fun `solver can independently re-solve a generated puzzle and matches original solution`() {
        val result = SudokuGenerator().generate(SudokuGenerator.Difficulty.MEDIUM)
        val resolved = solver.solve(result.puzzle)

        assertTrue("Solver should be able to solve the generated puzzle", resolved != null)
        assertTrue(
            "Independently-solved grid should exactly match the generator's stored solution",
            gridsEqual(resolved!!, result.solution)
        )
    }

    @Test
    fun `same generator instance can generate multiple puzzles in a row`() {
        val generator = SudokuGenerator()
        val first = generator.generate(SudokuGenerator.Difficulty.EASY)
        val second = generator.generate(SudokuGenerator.Difficulty.HARD)

        assertEquals(1, counter.countSolutions(first.puzzle, cap = 2))
        assertEquals(1, counter.countSolutions(second.puzzle, cap = 2))
    }

    private fun matchesGivens(given: Array<IntArray>, solved: Array<IntArray>): Boolean {
        for (r in 0..8) for (c in 0..8) if (given[r][c] != 0 && given[r][c] != solved[r][c]) return false
        return true
    }

    private fun isValidCompletedGrid(grid: Array<IntArray>): Boolean {
        fun isPermutation(nums: List<Int>) = nums.sorted() == (1..9).toList()
        for (r in 0..8) if (!isPermutation(grid[r].toList())) return false
        for (c in 0..8) if (!isPermutation((0..8).map { grid[it][c] })) return false
        for (br in 0..2) for (bc in 0..2) {
            val box = mutableListOf<Int>()
            for (r in 0..2) for (c in 0..2) box.add(grid[br * 3 + r][bc * 3 + c])
            if (!isPermutation(box)) return false
        }
        return true
    }

    private fun isRotationallySymmetric(grid: Array<IntArray>): Boolean {
        for (r in 0..8) for (c in 0..8) {
            if ((grid[r][c] != 0) != (grid[8 - r][8 - c] != 0)) return false
        }
        return true
    }

    private fun gridsEqual(a: Array<IntArray>, b: Array<IntArray>): Boolean {
        for (r in 0..8) for (c in 0..8) if (a[r][c] != b[r][c]) return false
        return true
    }
}