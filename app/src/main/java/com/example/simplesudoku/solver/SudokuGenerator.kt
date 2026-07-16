package com.example.simplesudoku.solver

/**
 * Generates valid, uniquely-solvable Sudoku puzzles.
 *
 * IMPORTANT DESIGN NOTE: "Difficulty" here drives two *separate* things
 * that must not be conflated:
 *   1. digTarget  - a private tuning knob telling the digger roughly how
 *      many clues to aim for. This is just a shaping instruction, not a
 *      promise, and the digger may land a few clues off it.
 *   2. difficultyLabel - what actually gets shown to the player. This is
 *      now the real DifficultyRater's rating (technique-based, 5 values
 *      including LEGEND for puzzles needing a guess), NOT the clue-count
 *      target - a Pro-clue-count puzzle can rate as Medium if it happens
 *      to be easy to crack logically, and vice versa. clueTarget below
 *      preserves what digHoles originally aimed for, for tuning/telemetry.
 */

class SudokuGenerator(
    private val uniquenessCounter: DlxUniquenessCounter = DlxUniquenessCounter(),
    private val difficultyRater: DifficultyRater = DifficultyRater()
) {
    enum class Difficulty(val minClues: Int, val maxClues: Int) {
        EASY(36, 46),
        MEDIUM(32, 35),
        HARD(28, 31),
        PRO(22, 27)
    }

    data class GeneratedPuzzle(
        val puzzle: Array<IntArray>,
        val solution: Array<IntArray>,
        val clueCount: Int,
        val clueTarget: Difficulty,           // what digHoles was aiming for (clue-count knob only)
        val difficultyLabel: DifficultyLabel, // real rating from DifficultyRater - what players see
        val ratingScore: Int                  // raw weighted score - for progressive-journey ordering later
    )

    companion object {
        private const val N = 9
        private const val ABSOLUTE_MIN_CLUES = 17

        private val seedGrid = arrayOf(
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
    }

    /**
     * Generates a puzzle targeting the given difficulty's clue range.
     * Retries with a fresh shuffle if a single attempt misses the range,
     * since not every random arrangement digs down to the same clue count.
     * If every attempt misses, returns the closest attempt found - never
     * throws, never leaves the player with nothing.
     */
    fun generate(target: Difficulty, maxAttempts: Int = 15): GeneratedPuzzle {
        // Holds raw attempt data only (puzzle, solution, clueCount) - deliberately
        // NOT rated yet. Rating (especially any Bifurcation) does real work, so we
        // only want to pay that cost once, for whichever attempt actually ships.
        var best: Triple<Array<IntArray>, Array<IntArray>, Int>? = null
        var bestDistance = Int.MAX_VALUE

        repeat(maxAttempts) {
            val solved = randomizeGrid(seedGrid)
            val puzzle = digHoles(solved, target)
            val clueCount = countClues(puzzle)

            if (clueCount in target.minClues..target.maxClues) {
                return buildResult(puzzle, solved, clueCount, target)
            }

            val distance = if (clueCount > target.maxClues) clueCount - target.maxClues
            else target.minClues - clueCount
            if (distance < bestDistance) {
                bestDistance = distance
                best = Triple(puzzle, solved, clueCount)
            }
        }

        val (puzzle, solved, clueCount) = best!!
        println(
            "Note: ${target.name} generation didn't land exactly in " +
                    "[${target.minClues}, ${target.maxClues}] after $maxAttempts attempts " +
                    "- using closest result ($clueCount clues). This is expected " +
                    "occasionally at low clue counts and is not a bug."
        )
        return buildResult(puzzle, solved, clueCount, target)
    }

    /** Runs the real DifficultyRater exactly once, on the puzzle actually being returned. */
    private fun buildResult(
        puzzle: Array<IntArray>,
        solved: Array<IntArray>,
        clueCount: Int,
        target: Difficulty
    ): GeneratedPuzzle {
        val rated = difficultyRater.rate(puzzle)
        return GeneratedPuzzle(
            puzzle = puzzle,
            solution = solved,
            clueCount = clueCount,
            clueTarget = target,
            difficultyLabel = rated.label,
            ratingScore = rated.rawScore
        )
    }

    private fun countClues(grid: Array<IntArray>): Int =
        grid.sumOf { row -> row.count { it != 0 } }

    // ---- Phase 1: random valid solved grid via seed transformation ----

    private fun randomizeGrid(seed: Array<IntArray>): Array<IntArray> {
        var grid = seed.map { it.copyOf() }.toTypedArray()
        grid = relabelDigits(grid)
        grid = shuffleRowsWithinBands(grid)
        grid = shuffleColsWithinStacks(grid)
        grid = shuffleBands(grid)
        grid = shuffleStacks(grid)
        if (listOf(true, false).random()) grid = transpose(grid)
        return grid
    }

    private fun relabelDigits(grid: Array<IntArray>): Array<IntArray> {
        val mapping = (1..9).shuffled()
        return Array(N) { r -> IntArray(N) { c -> mapping[grid[r][c] - 1] } }
    }

    private fun shuffleRowsWithinBands(grid: Array<IntArray>): Array<IntArray> {
        val newRowOrder = (0 until N).toMutableList()
        for (band in 0..2) {
            val bandRows = (band * 3 until band * 3 + 3).shuffled()
            for (i in 0..2) newRowOrder[band * 3 + i] = bandRows[i]
        }
        return Array(N) { r -> grid[newRowOrder[r]].copyOf() }
    }

    private fun shuffleColsWithinStacks(grid: Array<IntArray>): Array<IntArray> {
        val newColOrder = (0 until N).toMutableList()
        for (stack in 0..2) {
            val stackCols = (stack * 3 until stack * 3 + 3).shuffled()
            for (i in 0..2) newColOrder[stack * 3 + i] = stackCols[i]
        }
        return Array(N) { r -> IntArray(N) { c -> grid[r][newColOrder[c]] } }
    }

    private fun shuffleBands(grid: Array<IntArray>): Array<IntArray> {
        val bandOrder = listOf(0, 1, 2).shuffled()
        val newRowOrder = bandOrder.flatMap { band -> (band * 3 until band * 3 + 3) }
        return Array(N) { r -> grid[newRowOrder[r]].copyOf() }
    }

    private fun shuffleStacks(grid: Array<IntArray>): Array<IntArray> {
        val stackOrder = listOf(0, 1, 2).shuffled()
        val newColOrder = stackOrder.flatMap { stack -> (stack * 3 until stack * 3 + 3) }
        return Array(N) { r -> IntArray(N) { c -> grid[r][newColOrder[c]] } }
    }

    private fun transpose(grid: Array<IntArray>): Array<IntArray> =
        Array(N) { r -> IntArray(N) { c -> grid[c][r] } }

    // ---- Phase 2: symmetric digging, gated by uniqueness ----

    private fun digHoles(solved: Array<IntArray>, difficulty: Difficulty): Array<IntArray> {
        val puzzle = solved.map { it.copyOf() }.toTypedArray()
        var cluesRemaining = N * N

        val cellOrder = (0 until N * N).shuffled()
        val tried = BooleanArray(N * N)

        for (idx in cellOrder) {
            if (tried[idx]) continue
            val r = idx / N
            val c = idx % N
            val mr = (N - 1) - r
            val mc = (N - 1) - c
            val mirrorIdx = mr * N + mc
            val isCenter = (r == mr && c == mc)

            tried[idx] = true
            if (!isCenter) tried[mirrorIdx] = true
            if (puzzle[r][c] == 0) continue

            val removedCount = if (isCenter) 1 else 2
            if (cluesRemaining - removedCount < ABSOLUTE_MIN_CLUES) continue
            if (cluesRemaining <= difficulty.maxClues) break

            val backupPrimary = puzzle[r][c]
            val backupMirror = puzzle[mr][mc]
            puzzle[r][c] = 0
            if (!isCenter) puzzle[mr][mc] = 0

            val count = uniquenessCounter.countSolutions(puzzle, cap = 2)
            if (count == 1) {
                cluesRemaining -= removedCount
            } else {
                puzzle[r][c] = backupPrimary
                puzzle[mr][mc] = backupMirror
            }
        }

        return puzzle
    }
}