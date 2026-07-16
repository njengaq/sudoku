package com.example.simplesudoku.solver

/**
 * One deduction step a human solver might make. Each Technique looks for
 * exactly one applicable instance of itself and, if found, applies it
 * in-place to the grid and reports what it did.
 *
 * Techniques are tried lowest-tier-first by DifficultyRater, so a technique
 * can assume "if I'm being asked, everything easier than me already failed
 * to find anything" - it never needs to re-check easier deductions itself.
 */
interface Technique {
    val tier: TechniqueTier

    /**
     * Attempts to find and apply ONE instance of this technique.
     * @return a TechniqueResult describing what happened, or null if this
     *         technique found nothing applicable in the current grid state.
     */
    fun tryApply(grid: CandidateGrid): TechniqueResult?
}

data class TechniqueResult(
    val tier: TechniqueTier,
    val description: String,   // human-readable; useful for debugging and potential "why" hint text later
    val cellsChanged: Int
)
