package com.example.simplesudoku.solver.techniques

import com.example.simplesudoku.solver.CandidateGrid
import com.example.simplesudoku.solver.Technique
import com.example.simplesudoku.solver.TechniqueResult
import com.example.simplesudoku.solver.TechniqueTier

/**
 * Simple coloring: for a single digit, chain together "conjugate pairs"
 * (units where the digit has exactly 2 candidate cells) and 2-color each
 * resulting chain. Two elimination rules follow:
 *  - Rule 2 (color trap): if two same-colored cells in one chain see each
 *    other, that color is impossible everywhere - eliminate the digit from
 *    every cell of that color.
 *  - Rule 4 (color wrap / "two colors elsewhere"): if a cell outside the
 *    chain sees both a colorA cell and a colorB cell from the SAME chain,
 *    it can't be the digit either way - eliminate it there.
 */
class SimpleColoringTechnique : Technique {
    override val tier = TechniqueTier.SIMPLE_COLORING

    override fun tryApply(grid: CandidateGrid): TechniqueResult? {
        val n = CandidateGrid.N
        val units = (0 until n).map { grid.rowCells(it) } +
                (0 until n).map { grid.colCells(it) } +
                (0 until n).map { grid.boxCells(it) }

        for (digit in 1..9) {
            val bit = CandidateGrid.bitFor(digit)

            val adjacency = HashMap<Pair<Int, Int>, MutableSet<Pair<Int, Int>>>()
            for (unit in units) {
                val cells = unit.filter { (r, c) -> grid.values[r][c] == 0 && grid.candidates[r][c] and bit != 0 }
                if (cells.size == 2) {
                    val (a, b) = cells
                    adjacency.getOrPut(a) { mutableSetOf() }.add(b)
                    adjacency.getOrPut(b) { mutableSetOf() }.add(a)
                }
            }
            if (adjacency.isEmpty()) continue

            for (component in connectedComponents(adjacency)) {
                val colorOf = colorComponent(adjacency, component)

                // Rule 2: two same-colored chain cells sharing a unit -> that color is impossible.
                for (unit in units) {
                    val coloredInUnit = unit.filter { it in colorOf }
                    for (color in 0..1) {
                        if (coloredInUnit.count { colorOf[it] == color } >= 2) {
                            val toClear = colorOf.keys.filter { colorOf[it] == color }
                            val changed = toClear.count { (r, c) -> grid.eliminate(r, c, digit) }
                            if (changed > 0) {
                                return TechniqueResult(tier, "Simple coloring rule 2: digit $digit, color $color impossible", changed)
                            }
                        }
                    }
                }

                // Rule 4: a cell outside this chain seeing both its colors can't be the digit either way.
                for (r in 0 until n) for (c in 0 until n) {
                    val cell = r to c
                    if (cell in colorOf || grid.values[r][c] != 0 || grid.candidates[r][c] and bit == 0) continue

                    val seesColor0 = colorOf.keys.any { colorOf[it] == 0 && seesEachOther(grid, cell, it) }
                    val seesColor1 = colorOf.keys.any { colorOf[it] == 1 && seesEachOther(grid, cell, it) }
                    if (seesColor0 && seesColor1 && grid.eliminate(r, c, digit)) {
                        return TechniqueResult(tier, "Simple coloring rule 4: digit $digit eliminated at r$r c$c", 1)
                    }
                }
            }
        }
        return null
    }
}