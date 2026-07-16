package com.example.simplesudoku.solver.techniques

import com.example.simplesudoku.solver.CandidateGrid
import com.example.simplesudoku.solver.Technique
import com.example.simplesudoku.solver.TechniqueResult
import com.example.simplesudoku.solver.TechniqueTier

/**
 * Remote Pairs - the one chain technique in scope (design doc §11: "maybe
 * one chain type for the hardest tier"). A chain of 3+ cells that all
 * share the exact same bivalue pair {x, y}, linked by seeing each other,
 * 2-colored like Simple Coloring. Whatever the true assignment turns out
 * to be, colors alternate strictly between x and y along the chain - so
 * any outside cell seeing one cell of each color can't be x OR y, no
 * matter which end of the chain is actually x.
 *
 * (A 2-cell version of this is just a naked pair, already caught by
 * NakedSubsetTechnique at the SUBSETS tier - this only fires for genuine
 * chains of length 3+.)
 */
class ChainTechnique : Technique {
    override val tier = TechniqueTier.CHAIN

    override fun tryApply(grid: CandidateGrid): TechniqueResult? {
        val n = CandidateGrid.N

        for (x in 1..9) for (y in x + 1..9) {
            val pairMask = CandidateGrid.bitFor(x) or CandidateGrid.bitFor(y)

            val bivalueCells = (0 until n).flatMap { r -> (0 until n).map { c -> r to c } }
                .filter { (r, c) -> grid.values[r][c] == 0 && grid.candidates[r][c] == pairMask }
            if (bivalueCells.size < 3) continue

            val adjacency = HashMap<Pair<Int, Int>, MutableSet<Pair<Int, Int>>>()
            for (a in bivalueCells) for (b in bivalueCells) {
                if (a != b && seesEachOther(grid, a, b)) {
                    adjacency.getOrPut(a) { mutableSetOf() }.add(b)
                }
            }
            if (adjacency.isEmpty()) continue

            for (component in connectedComponents(adjacency)) {
                if (component.size < 3) continue // a 2-cell chain is just a naked pair, already handled elsewhere
                val colorOf = colorComponent(adjacency, component)

                for (r in 0 until n) for (c in 0 until n) {
                    val cell = r to c
                    if (cell in colorOf || grid.values[r][c] != 0) continue
                    if (grid.candidates[r][c] and pairMask == 0) continue

                    val seesColor0 = colorOf.keys.any { colorOf[it] == 0 && seesEachOther(grid, cell, it) }
                    val seesColor1 = colorOf.keys.any { colorOf[it] == 1 && seesEachOther(grid, cell, it) }
                    if (seesColor0 && seesColor1) {
                        var changed = 0
                        if (grid.eliminate(r, c, x)) changed++
                        if (grid.eliminate(r, c, y)) changed++
                        if (changed > 0) {
                            return TechniqueResult(tier, "Remote pairs: digits $x/$y eliminated at r$r c$c", changed)
                        }
                    }
                }
            }
        }
        return null
    }
}