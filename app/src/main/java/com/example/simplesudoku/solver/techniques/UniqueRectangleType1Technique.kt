package com.example.simplesudoku.solver.techniques

import com.example.simplesudoku.solver.CandidateGrid
import com.example.simplesudoku.solver.Technique
import com.example.simplesudoku.solver.TechniqueResult
import com.example.simplesudoku.solver.TechniqueTier

/**
 * Unique Rectangle, Type 1: four cells forming a rectangle (2 rows x 2
 * cols) spanning exactly 2 boxes, where 3 of the 4 cells are bivalue with
 * the identical pair {x, y} and the 4th has {x, y} plus extra candidates.
 * If that 4th cell ever resolved to exactly {x, y} too, the puzzle would
 * have two valid solutions (a "deadly pattern") - which contradicts the
 * generator's uniqueness guarantee. So x and y can be eliminated from
 * that 4th cell right away.
 */
class UniqueRectangleType1Technique : Technique {
    override val tier = TechniqueTier.UNIQUE_RECTANGLE

    override fun tryApply(grid: CandidateGrid): TechniqueResult? {
        val n = CandidateGrid.N

        for (r1 in 0 until n) for (r2 in r1 + 1 until n) {
            for (c1 in 0 until n) for (c2 in c1 + 1 until n) {
                val cells = listOf(r1 to c1, r1 to c2, r2 to c1, r2 to c2)
                if (cells.any { (r, c) -> grid.values[r][c] != 0 }) continue

                val boxes = cells.map { (r, c) -> grid.boxOf(r, c) }.toSet()
                if (boxes.size != 2) continue // must span exactly 2 boxes, not 1 or 4

                val masks = cells.map { (r, c) -> grid.candidates[r][c] }
                val grouped = cells.indices.groupBy { masks[it] }
                val pairEntry = grouped.entries.firstOrNull { (mask, idxs) ->
                    CandidateGrid.countBits(mask) == 2 && idxs.size == 3
                } ?: continue

                val pairMask = pairEntry.key
                val fourthIdx = cells.indices.first { it !in pairEntry.value }
                val fourthMask = masks[fourthIdx]
                if ((fourthMask and pairMask) != pairMask || CandidateGrid.countBits(fourthMask) <= 2) continue

                val (er, ec) = cells[fourthIdx]
                val changed = CandidateGrid.digitsIn(pairMask).count { grid.eliminate(er, ec, it) }
                if (changed > 0) {
                    return TechniqueResult(tier, "Unique Rectangle Type 1: clear pair at r$er c$ec", changed)
                }
            }
        }
        return null
    }
}