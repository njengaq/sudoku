package com.example.simplesudoku.solver.techniques

import com.example.simplesudoku.solver.CandidateGrid
import com.example.simplesudoku.solver.Technique
import com.example.simplesudoku.solver.TechniqueResult
import com.example.simplesudoku.solver.TechniqueTier

/**
 * XY-Wing: a pivot cell with exactly 2 candidates {x, y}, and two "wing"
 * cells that each see the pivot and are themselves bivalue: one wing is
 * {x, z}, the other is {y, z} (same third digit z in both). Whichever of
 * x or y the pivot turns out to be, one of the two wings must be z - so
 * any cell that sees BOTH wings can have z eliminated.
 */
class BasicWingsTechnique : Technique {
    override val tier = TechniqueTier.BASIC_WINGS

    override fun tryApply(grid: CandidateGrid): TechniqueResult? {
        val n = CandidateGrid.N

        for (pr in 0 until n) for (pc in 0 until n) {
            if (grid.values[pr][pc] != 0) continue
            val pivotMask = grid.candidates[pr][pc]
            if (CandidateGrid.countBits(pivotMask) != 2) continue
            val pivot = pr to pc

            val bivaluePeers = allCells(n)
                .filter { it != pivot && grid.values[it.first][it.second] == 0 }
                .filter { CandidateGrid.countBits(grid.candidates[it.first][it.second]) == 2 }
                .filter { seesEachOther(grid, pivot, it) }

            for (i in bivaluePeers.indices) {
                for (j in i + 1 until bivaluePeers.size) {
                    val wingA = bivaluePeers[i]
                    val wingB = bivaluePeers[j]
                    val maskA = grid.candidates[wingA.first][wingA.second]
                    val maskB = grid.candidates[wingB.first][wingB.second]

                    val sharedA = maskA and pivotMask
                    val sharedB = maskB and pivotMask
                    if (CandidateGrid.countBits(sharedA) != 1 || CandidateGrid.countBits(sharedB) != 1) continue
                    if (sharedA == sharedB) continue // must lock onto different halves of the pivot pair

                    val zA = maskA and sharedA.inv()
                    val zB = maskB and sharedB.inv()
                    if (zA != zB) continue // both wings must point at the same third digit

                    val z = CandidateGrid.digitsIn(zA).first()

                    val targets = allCells(n).filter { t ->
                        t != pivot && t != wingA && t != wingB &&
                                grid.values[t.first][t.second] == 0 &&
                                grid.candidates[t.first][t.second] and CandidateGrid.bitFor(z) != 0 &&
                                seesEachOther(grid, t, wingA) && seesEachOther(grid, t, wingB)
                    }

                    val changed = targets.count { (r, c) -> grid.eliminate(r, c, z) }
                    if (changed > 0) {
                        return TechniqueResult(tier, "XY-Wing: pivot r$pr c$pc, wings $wingA/$wingB, eliminate digit $z", changed)
                    }
                }
            }
        }
        return null
    }

    private fun allCells(n: Int): List<Pair<Int, Int>> =
        (0 until n).flatMap { r -> (0 until n).map { c -> r to c } }
}