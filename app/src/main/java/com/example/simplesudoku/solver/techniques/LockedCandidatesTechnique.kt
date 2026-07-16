package com.example.simplesudoku.solver.techniques

import com.example.simplesudoku.solver.CandidateGrid
import com.example.simplesudoku.solver.Technique
import com.example.simplesudoku.solver.TechniqueResult
import com.example.simplesudoku.solver.TechniqueTier

/**
 * Locked candidates:
 *  - Pointing: a digit confined to one row/col within a box eliminates that
 *    digit from the rest of that row/col outside the box.
 *  - Claiming: a digit confined to one box within a row/col eliminates that
 *    digit from the rest of the box.
 */
class LockedCandidatesTechnique : Technique {
    override val tier = TechniqueTier.LOCKED_CANDIDATES

    override fun tryApply(grid: CandidateGrid): TechniqueResult? {
        pointing(grid)?.let { return it }
        claiming(grid)?.let { return it }
        return null
    }

    private fun pointing(grid: CandidateGrid): TechniqueResult? {
        for (b in 0 until CandidateGrid.N) {
            val boxCells = grid.boxCells(b)
            for (digit in 1..9) {
                val bit = CandidateGrid.bitFor(digit)
                val cells = boxCells.filter { (r, c) -> grid.values[r][c] == 0 && grid.candidates[r][c] and bit != 0 }
                if (cells.size < 2) continue

                val rows = cells.map { it.first }.distinct()
                if (rows.size == 1) {
                    val changed = grid.rowCells(rows[0]).filter { it !in boxCells }
                        .count { (r, c) -> grid.eliminate(r, c, digit) }
                    if (changed > 0) return TechniqueResult(tier, "Pointing: box $b digit $digit locked to row ${rows[0]}", changed)
                }

                val cols = cells.map { it.second }.distinct()
                if (cols.size == 1) {
                    val changed = grid.colCells(cols[0]).filter { it !in boxCells }
                        .count { (r, c) -> grid.eliminate(r, c, digit) }
                    if (changed > 0) return TechniqueResult(tier, "Pointing: box $b digit $digit locked to col ${cols[0]}", changed)
                }
            }
        }
        return null
    }

    private fun claiming(grid: CandidateGrid): TechniqueResult? {
        for (r in 0 until CandidateGrid.N) {
            claimingForUnit(grid, grid.rowCells(r), "row $r")?.let { return it }
        }
        for (c in 0 until CandidateGrid.N) {
            claimingForUnit(grid, grid.colCells(c), "col $c")?.let { return it }
        }
        return null
    }

    private fun claimingForUnit(grid: CandidateGrid, unit: List<Pair<Int, Int>>, unitName: String): TechniqueResult? {
        for (digit in 1..9) {
            val bit = CandidateGrid.bitFor(digit)
            val cells = unit.filter { (r, c) -> grid.values[r][c] == 0 && grid.candidates[r][c] and bit != 0 }
            if (cells.size < 2) continue

            val boxes = cells.map { (r, c) -> grid.boxOf(r, c) }.distinct()
            if (boxes.size == 1) {
                val changed = grid.boxCells(boxes[0]).filter { it !in cells }
                    .count { (r, c) -> grid.eliminate(r, c, digit) }
                if (changed > 0) return TechniqueResult(tier, "Claiming: $unitName digit $digit locked to box ${boxes[0]}", changed)
            }
        }
        return null
    }
}
