package com.example.simplesudoku.solver.techniques

import com.example.simplesudoku.solver.CandidateGrid
import com.example.simplesudoku.solver.Technique
import com.example.simplesudoku.solver.TechniqueResult
import com.example.simplesudoku.solver.TechniqueTier

/**
 * X-Wing: if a digit's remaining candidate cells in two rows are confined
 * to the exact same two columns, the digit must occupy one of the two
 * diagonals of that rectangle - so it can be eliminated from those two
 * columns in every other row. Same logic applies swapped (columns forming
 * the pair, eliminating from rows).
 *
 * This is the only "fish" pattern in scope per the design doc (§11 lists
 * "basic fish" as one technique, not the full fish family) - no Swordfish
 * or Jellyfish (which would be the size-3 / size-4 generalizations).
 */
class BasicFishTechnique : Technique {
    override val tier = TechniqueTier.BASIC_FISH

    override fun tryApply(grid: CandidateGrid): TechniqueResult? {
        rowBased(grid)?.let { return it }
        colBased(grid)?.let { return it }
        return null
    }

    /** Digit confined to the same 2 columns across 2 rows -> eliminate from those columns elsewhere. */
    private fun rowBased(grid: CandidateGrid): TechniqueResult? {
        val n = CandidateGrid.N
        for (digit in 1..9) {
            val bit = CandidateGrid.bitFor(digit)
            // Only rows where this digit has exactly 2 candidate cells can form an X-Wing corner.
            val pairRows = (0 until n).mapNotNull { r ->
                val cols = (0 until n).filter { c -> grid.values[r][c] == 0 && grid.candidates[r][c] and bit != 0 }
                if (cols.size == 2) r to cols else null
            }

            for (i in pairRows.indices) {
                for (j in i + 1 until pairRows.size) {
                    val (r1, cols1) = pairRows[i]
                    val (r2, cols2) = pairRows[j]
                    if (cols1 != cols2) continue

                    val (c1, c2) = cols1
                    var changed = 0
                    for (r in 0 until n) {
                        if (r == r1 || r == r2) continue
                        if (grid.eliminate(r, c1, digit)) changed++
                        if (grid.eliminate(r, c2, digit)) changed++
                    }
                    if (changed > 0) {
                        return TechniqueResult(tier, "X-Wing (rows $r1,$r2 / cols $c1,$c2): eliminate digit $digit", changed)
                    }
                }
            }
        }
        return null
    }

    /** Mirror of rowBased: digit confined to the same 2 rows across 2 columns. */
    private fun colBased(grid: CandidateGrid): TechniqueResult? {
        val n = CandidateGrid.N
        for (digit in 1..9) {
            val bit = CandidateGrid.bitFor(digit)
            val pairCols = (0 until n).mapNotNull { c ->
                val rows = (0 until n).filter { r -> grid.values[r][c] == 0 && grid.candidates[r][c] and bit != 0 }
                if (rows.size == 2) c to rows else null
            }

            for (i in pairCols.indices) {
                for (j in i + 1 until pairCols.size) {
                    val (c1, rows1) = pairCols[i]
                    val (c2, rows2) = pairCols[j]
                    if (rows1 != rows2) continue

                    val (r1, r2) = rows1
                    var changed = 0
                    for (c in 0 until n) {
                        if (c == c1 || c == c2) continue
                        if (grid.eliminate(r1, c, digit)) changed++
                        if (grid.eliminate(r2, c, digit)) changed++
                    }
                    if (changed > 0) {
                        return TechniqueResult(tier, "X-Wing (cols $c1,$c2 / rows $r1,$r2): eliminate digit $digit", changed)
                    }
                }
            }
        }
        return null
    }
}