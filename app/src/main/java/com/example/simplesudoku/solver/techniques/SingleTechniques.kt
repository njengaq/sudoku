package com.example.simplesudoku.solver.techniques

import com.example.simplesudoku.solver.CandidateGrid
import com.example.simplesudoku.solver.Technique
import com.example.simplesudoku.solver.TechniqueResult
import com.example.simplesudoku.solver.TechniqueTier

/** A cell with exactly one remaining candidate must be that digit. */
class NakedSingleTechnique : Technique {
    override val tier = TechniqueTier.SINGLES

    override fun tryApply(grid: CandidateGrid): TechniqueResult? {
        for (r in 0 until CandidateGrid.N) for (c in 0 until CandidateGrid.N) {
            if (grid.values[r][c] != 0) continue
            val mask = grid.candidates[r][c]
            if (CandidateGrid.countBits(mask) == 1) {
                val digit = CandidateGrid.digitsIn(mask).first()
                grid.place(r, c, digit)
                return TechniqueResult(tier, "Naked single: r$r c$c = $digit", 1)
            }
        }
        return null
    }
}

/** A digit that only fits in one cell within a row/col/box must go there. */
class HiddenSingleTechnique : Technique {
    override val tier = TechniqueTier.SINGLES

    override fun tryApply(grid: CandidateGrid): TechniqueResult? {
        val units = (0 until CandidateGrid.N).map { grid.rowCells(it) } +
            (0 until CandidateGrid.N).map { grid.colCells(it) } +
            (0 until CandidateGrid.N).map { grid.boxCells(it) }

        for (unit in units) {
            for (digit in 1..9) {
                val bit = CandidateGrid.bitFor(digit)
                val holders = unit.filter { (r, c) -> grid.values[r][c] == 0 && grid.candidates[r][c] and bit != 0 }
                if (holders.size == 1) {
                    val (r, c) = holders[0]
                    grid.place(r, c, digit)
                    return TechniqueResult(tier, "Hidden single: r$r c$c = $digit", 1)
                }
            }
        }
        return null
    }
}
