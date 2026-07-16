package com.example.simplesudoku.solver.techniques

import com.example.simplesudoku.solver.CandidateGrid
import com.example.simplesudoku.solver.DlxSudokuSolver
import com.example.simplesudoku.solver.Technique
import com.example.simplesudoku.solver.TechniqueResult
import com.example.simplesudoku.solver.TechniqueTier

/**
 * Bifurcation ("Legend" tier): when nothing easier applies, try a candidate
 * and check feasibility by reusing the existing, already-tested
 * DlxSudokuSolver rather than writing a second backtracker.
 *
 * Key insight: Generator only ever ships puzzles DlxUniquenessCounter
 * confirmed have a UNIQUE solution, and every logical elimination made so
 * far preserves that same solution. So for any still-empty cell, at most
 * ONE remaining candidate digit can lead to a solvable grid - every other
 * candidate must be a dead end. That means we can test candidates one at a
 * time: the first one DlxSudokuSolver confirms solvable IS the correct
 * digit (no need to test the rest), and any candidate that comes back
 * unsolvable is confirmed wrong and can be eliminated outright even if this
 * particular call doesn't land on a placement.
 */
class BifurcationTechnique(
    private val solver: DlxSudokuSolver = DlxSudokuSolver()
) : Technique {
    override val tier = TechniqueTier.BIFURCATION

    override fun tryApply(grid: CandidateGrid): TechniqueResult? {
        val (r, c) = pickTargetCell(grid) ?: return null
        val digits = CandidateGrid.digitsIn(grid.candidates[r][c])

        for (digit in digits) {
            val trialGivens = grid.values.map { it.copyOf() }.toTypedArray()
            trialGivens[r][c] = digit

            if (solver.solve(trialGivens) != null) {
                grid.place(r, c, digit)
                return TechniqueResult(tier, "Bifurcation: guessed r$r c$c = $digit (confirmed solvable)", 1)
            } else {
                grid.eliminate(r, c, digit)
                return TechniqueResult(tier, "Bifurcation: eliminated r$r c$c = $digit (leads to no solution)", 1)
            }
        }
        return null
    }

    /** Cell with the fewest remaining candidates - cheapest to test. */
    private fun pickTargetCell(grid: CandidateGrid): Pair<Int, Int>? {
        var best: Pair<Int, Int>? = null
        var bestCount = Int.MAX_VALUE
        for (r in 0 until CandidateGrid.N) for (c in 0 until CandidateGrid.N) {
            if (grid.values[r][c] != 0) continue
            val count = CandidateGrid.countBits(grid.candidates[r][c])
            if (count in 2 until bestCount) {
                bestCount = count
                best = r to c
            }
        }
        return best
    }
}
