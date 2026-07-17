package com.example.simplesudoku.solver

import com.example.simplesudoku.solver.techniques.BasicFishTechnique
import com.example.simplesudoku.solver.techniques.BasicWingsTechnique
import com.example.simplesudoku.solver.techniques.BifurcationTechnique
import com.example.simplesudoku.solver.techniques.ChainTechnique
import com.example.simplesudoku.solver.techniques.HiddenSingleTechnique
import com.example.simplesudoku.solver.techniques.HiddenSubsetTechnique
import com.example.simplesudoku.solver.techniques.LockedCandidatesTechnique
import com.example.simplesudoku.solver.techniques.NakedSingleTechnique
import com.example.simplesudoku.solver.techniques.NakedSubsetTechnique
import com.example.simplesudoku.solver.techniques.SimpleColoringTechnique
import com.example.simplesudoku.solver.techniques.UniqueRectangleType1Technique

/** Player-facing rating. Distinct from SudokuGenerator.Difficulty, which is
 *  only a clue-count *targeting* knob for digHoles - see open question at
 *  the bottom of this file about reconciling the two. */
enum class DifficultyLabel { EASY, MEDIUM, HARD, PRO, LEGEND }

data class RaterResult(
    val label: DifficultyLabel,
    val rawScore: Int,                                // kept for progressive-journey ordering & future tuning
    val floorTier: TechniqueTier,                      // hardest tier actually required
    val tierUsageCounts: Map<TechniqueTier, Int>,
    val usedBifurcation: Boolean,
    val solved: Boolean                                // false only if something has gone wrong - see note below
)

/**
 * Simulates solving a puzzle the way a human would - technique by
 * technique, always reaching for the easiest applicable one first - to
 * produce a weighted difficulty rating. Deliberately a different engine
 * from DlxSudokuSolver: that one proves a solution exists, this one grades
 * how hard it is to *find* logically.
 */
class DifficultyRater(
    private val techniques: List<Technique> = defaultTechniques()
) {
    companion object {
        fun defaultTechniques(): List<Technique> = listOf(
            NakedSingleTechnique(),
            HiddenSingleTechnique(),
            LockedCandidatesTechnique(),
            NakedSubsetTechnique(2), NakedSubsetTechnique(3), NakedSubsetTechnique(4),
            HiddenSubsetTechnique(2), HiddenSubsetTechnique(3), HiddenSubsetTechnique(4),
            BasicFishTechnique(),
            SimpleColoringTechnique(),
            BasicWingsTechnique(),
            UniqueRectangleType1Technique(),
            ChainTechnique(),
            BifurcationTechnique()
        ).sortedBy { it.tier.ordinal } // guarantees easiest-first regardless of list order above
    }

    fun rate(puzzle: Array<IntArray>): RaterResult {
        val grid = CandidateGrid.fromGivens(puzzle)
        val usageCounts = linkedMapOf<TechniqueTier, Int>()
        var floor = TechniqueTier.SINGLES

        // Bifurcation is designed to always find SOMETHING (a placement or
        // an elimination) whenever it's reached with 2+ unsolved cells, so
        // this loop should only exit via isSolved(). The `?: break` is a
        // safety net, not an expected path, for a genuinely malformed grid.
        while (!grid.isSolved()) {
            val result = techniques.firstNotNullOfOrNull { it.tryApply(grid) } ?: break

            usageCounts[result.tier] = (usageCounts[result.tier] ?: 0) + 1
            if (result.tier.ordinal > floor.ordinal) floor = result.tier
        }

        val rawScore = usageCounts.entries.sumOf { (tier, count) -> tier.baseWeight * count }

        return RaterResult(
            label = labelFor(floor),
            rawScore = rawScore,
            floorTier = floor,
            tierUsageCounts = usageCounts,
            usedBifurcation = usageCounts.containsKey(TechniqueTier.BIFURCATION),
            solved = grid.isSolved()
        )
    }

    private fun labelFor(floor: TechniqueTier): DifficultyLabel = when (floor) {
        TechniqueTier.SINGLES -> DifficultyLabel.EASY
        TechniqueTier.LOCKED_CANDIDATES, TechniqueTier.SUBSETS -> DifficultyLabel.MEDIUM
        TechniqueTier.BASIC_FISH, TechniqueTier.SIMPLE_COLORING, TechniqueTier.BASIC_WINGS -> DifficultyLabel.HARD
        TechniqueTier.UNIQUE_RECTANGLE, TechniqueTier.CHAIN -> DifficultyLabel.PRO
        TechniqueTier.BIFURCATION -> DifficultyLabel.LEGEND
    }

    /**
     * Runs every tier EXCEPT Bifurcation to completion. Returns the
     * resulting RaterResult if the grid fully solves this way, or null if
     * it gets stuck needing a guess (i.e. Bifurcation genuinely is
     * required for this puzzle in its current state).
     *
     * This is the cheap half of the engine - no DLX solves happen here,
     * since Bifurcation is the only tier that calls into DlxSudokuSolver.
     * SudokuGenerator uses this to ask "would removing this clue force a
     * guess?" on every candidate removal, without paying Bifurcation's
     * cost at all during digging.
     */
    fun rateWithoutBifurcation(puzzle: Array<IntArray>): RaterResult? {
        val grid = CandidateGrid.fromGivens(puzzle)
        val nonBifurcationTechniques = techniques.filter { it.tier != TechniqueTier.BIFURCATION }
        val usageCounts = linkedMapOf<TechniqueTier, Int>()
        var floor = TechniqueTier.SINGLES

        while (!grid.isSolved()) {
            val result = nonBifurcationTechniques.firstNotNullOfOrNull { it.tryApply(grid) } ?: break
            usageCounts[result.tier] = (usageCounts[result.tier] ?: 0) + 1
            if (result.tier.ordinal > floor.ordinal) floor = result.tier
        }

        if (!grid.isSolved()) return null // genuinely needs a guess in this state

        val rawScore = usageCounts.entries.sumOf { (tier, count) -> tier.baseWeight * count }
        return RaterResult(
            label = labelFor(floor),
            rawScore = rawScore,
            floorTier = floor,
            tierUsageCounts = usageCounts,
            usedBifurcation = false,
            solved = true
        )
    }
}