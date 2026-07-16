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
}

/*
 * OPEN QUESTION for next session (flagging, not deciding):
 * SudokuGenerator.GeneratedPuzzle.difficultyLabel is currently typed as
 * SudokuGenerator.Difficulty (4 values, clue-count based). This module's
 * DifficultyLabel has 5 values and is technique-based. Need to decide how
 * Generator gets re-stamped once this Rater runs on its output - e.g.
 * does GeneratedPuzzle's field change type to DifficultyLabel, or does
 * something map between the two?
 */
