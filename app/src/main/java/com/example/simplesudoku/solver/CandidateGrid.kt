package com.example.simplesudoku.solver

/**
 * Mutable working state for the Difficulty Rater's technique-based solver.
 * Unlike DlxSudokuSolver's exact-cover matrix, this models the grid the way
 * a human sees it: each unsolved cell holds a set of remaining candidate
 * digits (as a 9-bit mask), and techniques narrow those sets down or place
 * a digit outright.
 *
 * Bit i (0-indexed) of a cell's mask represents digit (i+1).
 */
class CandidateGrid private constructor(
    val values: Array<IntArray>,       // 0 = unsolved, 1-9 = placed
    val candidates: Array<IntArray>    // bitmask per cell; stale/unused once a cell is placed
) {
    companion object {
        const val N = 9
        const val BOX = 3
        const val FULL_MASK = 0b111111111 // digits 1-9

        fun bitFor(digit: Int): Int = 1 shl (digit - 1)
        fun digitsIn(mask: Int): List<Int> = (1..9).filter { mask and bitFor(it) != 0 }
        fun countBits(mask: Int): Int = Integer.bitCount(mask)

        /** Builds a fresh CandidateGrid from a puzzle's givens, with candidates
         *  derived purely from row/col/box constraints - no solution peeking. */
        fun fromGivens(givens: Array<IntArray>): CandidateGrid {
            require(givens.size == N && givens.all { it.size == N }) { "Grid must be 9x9" }
            val values = Array(N) { r -> givens[r].copyOf() }
            val candidates = Array(N) { r -> IntArray(N) { c -> if (values[r][c] != 0) 0 else FULL_MASK } }
            val grid = CandidateGrid(values, candidates)
            for (r in 0 until N) for (c in 0 until N) {
                if (values[r][c] != 0) grid.propagatePlacement(r, c, values[r][c])
            }
            return grid
        }
    }

    fun isSolved(): Boolean = values.all { row -> row.all { it != 0 } }

    /** True if some unsolved cell has zero candidates left - an invalid/contradictory state. */
    fun hasContradiction(): Boolean {
        for (r in 0 until N) for (c in 0 until N) {
            if (values[r][c] == 0 && candidates[r][c] == 0) return true
        }
        return false
    }

    fun boxOf(r: Int, c: Int): Int = (r / BOX) * BOX + (c / BOX)

    fun rowCells(r: Int): List<Pair<Int, Int>> = (0 until N).map { r to it }
    fun colCells(c: Int): List<Pair<Int, Int>> = (0 until N).map { it to c }
    fun boxCells(b: Int): List<Pair<Int, Int>> {
        val br = (b / BOX) * BOX
        val bc = (b % BOX) * BOX
        val cells = ArrayList<Pair<Int, Int>>(9)
        for (dr in 0 until BOX) for (dc in 0 until BOX) cells.add((br + dr) to (bc + dc))
        return cells
    }

    private fun peersOf(r: Int, c: Int): List<Pair<Int, Int>> {
        val peers = LinkedHashSet<Pair<Int, Int>>()
        for (i in 0 until N) {
            if (i != c) peers.add(r to i)
            if (i != r) peers.add(i to c)
        }
        peers.addAll(boxCells(boxOf(r, c)).filter { it != r to c })
        return peers.toList()
    }

    /** Places a digit and eliminates it from all peers' candidates. */
    fun place(r: Int, c: Int, digit: Int): Boolean {
        if (values[r][c] != 0) return values[r][c] == digit
        values[r][c] = digit
        candidates[r][c] = 0
        return propagatePlacement(r, c, digit)
    }

    private fun propagatePlacement(r: Int, c: Int, digit: Int): Boolean {
        val bit = bitFor(digit)
        for ((pr, pc) in peersOf(r, c)) {
            if (values[pr][pc] == 0) candidates[pr][pc] = candidates[pr][pc] and bit.inv()
        }
        return true
    }

    /** Removes [digit] from a cell's candidates. Returns true if it actually changed anything. */
    fun eliminate(r: Int, c: Int, digit: Int): Boolean {
        val bit = bitFor(digit)
        if (values[r][c] != 0 || candidates[r][c] and bit == 0) return false
        candidates[r][c] = candidates[r][c] and bit.inv()
        return true
    }
}
