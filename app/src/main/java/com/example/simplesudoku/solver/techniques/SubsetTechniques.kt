package com.example.simplesudoku.solver.techniques

import com.example.simplesudoku.solver.CandidateGrid
import com.example.simplesudoku.solver.Technique
import com.example.simplesudoku.solver.TechniqueResult
import com.example.simplesudoku.solver.TechniqueTier

/**
 * Naked subsets: [size] cells in a unit whose candidates, combined, span
 * exactly [size] digits - so those digits can be eliminated from every
 * other cell in the unit. size=2 is naked pairs, 3 is triples, etc.
 */
class NakedSubsetTechnique(private val size: Int) : Technique {
    override val tier = TechniqueTier.SUBSETS

    override fun tryApply(grid: CandidateGrid): TechniqueResult? {
        for (unit in allUnits(grid)) {
            val unsolved = unit.filter { (r, c) -> grid.values[r][c] == 0 }
            val pool = unsolved.filter { (r, c) -> CandidateGrid.countBits(grid.candidates[r][c]) in 2..size }
            if (pool.size < size) continue

            for (combo in pool.combinationsOfSize(size)) {
                val union = combo.fold(0) { acc, (r, c) -> acc or grid.candidates[r][c] }
                if (CandidateGrid.countBits(union) != size) continue

                val changed = unsolved.filter { it !in combo }
                    .count { (r, c) -> clearBits(grid, r, c, union) }
                if (changed > 0) {
                    return TechniqueResult(tier, "Naked $size-subset, digits ${CandidateGrid.digitsIn(union)}", changed)
                }
            }
        }
        return null
    }

    private fun clearBits(grid: CandidateGrid, r: Int, c: Int, union: Int): Boolean {
        var changed = false
        for (digit in CandidateGrid.digitsIn(union)) {
            if (grid.eliminate(r, c, digit)) changed = true
        }
        return changed
    }
}

/**
 * Hidden subsets: [size] digits confined to exactly [size] cells in a unit -
 * every other candidate in those cells can then be eliminated.
 */
class HiddenSubsetTechnique(private val size: Int) : Technique {
    override val tier = TechniqueTier.SUBSETS

    override fun tryApply(grid: CandidateGrid): TechniqueResult? {
        for (unit in allUnits(grid)) {
            val unsolved = unit.filter { (r, c) -> grid.values[r][c] == 0 }
            val digitPool = (1..9).filter { d ->
                val bit = CandidateGrid.bitFor(d)
                unsolved.count { (r, c) -> grid.candidates[r][c] and bit != 0 } in 2..size
            }
            if (digitPool.size < size) continue

            for (combo in digitPool.combinationsOfSize(size)) {
                val bits = combo.fold(0) { acc, d -> acc or CandidateGrid.bitFor(d) }
                val holderCells = unsolved.filter { (r, c) -> grid.candidates[r][c] and bits != 0 }
                if (holderCells.size != size) continue

                val changed = holderCells.count { (r, c) ->
                    val before = grid.candidates[r][c]
                    val after = before and bits
                    if (after != before) {
                        for (digit in CandidateGrid.digitsIn(before and bits.inv())) grid.eliminate(r, c, digit)
                        true
                    } else false
                }
                if (changed > 0) {
                    return TechniqueResult(tier, "Hidden $size-subset, digits $combo", changed)
                }
            }
        }
        return null
    }
}

private fun allUnits(grid: CandidateGrid): List<List<Pair<Int, Int>>> =
    (0 until CandidateGrid.N).map { grid.rowCells(it) } +
        (0 until CandidateGrid.N).map { grid.colCells(it) } +
        (0 until CandidateGrid.N).map { grid.boxCells(it) }

/** Small combinatorics helper - lists are tiny (max 9 cells/digits), so a naive approach is fine. */
private fun <T> List<T>.combinationsOfSize(k: Int): List<List<T>> {
    if (k == 0) return listOf(emptyList())
    if (isEmpty()) return emptyList()
    val head = this[0]
    val tail = drop(1)
    return tail.combinationsOfSize(k - 1).map { listOf(head) + it } + tail.combinationsOfSize(k)
}
