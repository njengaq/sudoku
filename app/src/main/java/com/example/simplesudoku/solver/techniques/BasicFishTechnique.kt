package com.example.simplesudoku.solver.techniques

import com.example.simplesudoku.solver.CandidateGrid
import com.example.simplesudoku.solver.Technique
import com.example.simplesudoku.solver.TechniqueResult
import com.example.simplesudoku.solver.TechniqueTier

/**
 * Basic fish family: X-Wing (size 2) and Swordfish (size 3). A digit
 * confined, across `size` rows, to a total of exactly `size` columns
 * (each of those rows having between 1 and `size` candidate cells for the
 * digit) means the digit must occupy exactly one cell per row within
 * those columns - so it can be eliminated from those columns in every
 * OTHER row. Same logic mirrored for columns confined to rows.
 *
 * Jellyfish (size 4) is intentionally left out - "basic fish" in the
 * design doc means X-Wing/Swordfish, not the full fish family.
 */
class BasicFishTechnique : Technique {
    override val tier = TechniqueTier.BASIC_FISH

    override fun tryApply(grid: CandidateGrid): TechniqueResult? {
        for (size in 2..3) {
            rowBased(grid, size)?.let { return it }
            colBased(grid, size)?.let { return it }
        }
        return null
    }

    private fun rowBased(grid: CandidateGrid, size: Int): TechniqueResult? {
        val n = CandidateGrid.N
        for (digit in 1..9) {
            val bit = CandidateGrid.bitFor(digit)
            val rowCols = (0 until n)
                .map { r -> r to (0 until n).filter { c -> grid.values[r][c] == 0 && grid.candidates[r][c] and bit != 0 } }
                .filter { it.second.isNotEmpty() && it.second.size <= size }

            for (combo in rowCols.fishCombinations(size)) {
                val unionCols = combo.flatMap { it.second }.toSortedSet()
                if (unionCols.size != size) continue

                val rowsInCombo = combo.map { it.first }
                var changed = 0
                for (r in 0 until n) {
                    if (r in rowsInCombo) continue
                    for (c in unionCols) if (grid.eliminate(r, c, digit)) changed++
                }
                if (changed > 0) {
                    val name = if (size == 2) "X-Wing" else "Swordfish"
                    return TechniqueResult(tier, "$name (rows $rowsInCombo / cols $unionCols): eliminate digit $digit", changed)
                }
            }
        }
        return null
    }

    private fun colBased(grid: CandidateGrid, size: Int): TechniqueResult? {
        val n = CandidateGrid.N
        for (digit in 1..9) {
            val bit = CandidateGrid.bitFor(digit)
            val colRows = (0 until n)
                .map { c -> c to (0 until n).filter { r -> grid.values[r][c] == 0 && grid.candidates[r][c] and bit != 0 } }
                .filter { it.second.isNotEmpty() && it.second.size <= size }

            for (combo in colRows.fishCombinations(size)) {
                val unionRows = combo.flatMap { it.second }.toSortedSet()
                if (unionRows.size != size) continue

                val colsInCombo = combo.map { it.first }
                var changed = 0
                for (c in 0 until n) {
                    if (c in colsInCombo) continue
                    for (r in unionRows) if (grid.eliminate(r, c, digit)) changed++
                }
                if (changed > 0) {
                    val name = if (size == 2) "X-Wing" else "Swordfish"
                    return TechniqueResult(tier, "$name (cols $colsInCombo / rows $unionRows): eliminate digit $digit", changed)
                }
            }
        }
        return null
    }
}

/** Small combinatorics helper - at most 9 rows/cols, so a naive approach is fine. */
private fun <T> List<T>.fishCombinations(k: Int): List<List<T>> {
    if (k == 0) return listOf(emptyList())
    if (isEmpty()) return emptyList()
    val head = this[0]
    val tail = drop(1)
    return tail.fishCombinations(k - 1).map { listOf(head) + it } + tail.fishCombinations(k)
}