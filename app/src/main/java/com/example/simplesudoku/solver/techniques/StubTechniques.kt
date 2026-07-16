package com.example.simplesudoku.solver.techniques

import com.example.simplesudoku.solver.CandidateGrid
import com.example.simplesudoku.solver.Technique
import com.example.simplesudoku.solver.TechniqueResult
import com.example.simplesudoku.solver.TechniqueTier

/**
 * Placeholders for tiers not yet implemented. Each always returns null
 * (never applies). Because DifficultyRater tries tiers in order, this
 * just means the solve loop falls through to whatever comes next -
 * eventually Bifurcation, if nothing else fires. Wired in now so the
 * full tier list and weighting already work end-to-end; real logic to
 * be filled in one technique at a time.
 *
 * TODO: implement BasicFish (X-Wing), SimpleColoring, BasicWings (XY-Wing),
 *       UniqueRectangleType1, and Chain for real.
 */
class BasicFishTechnique : Technique {
    override val tier = TechniqueTier.BASIC_FISH
    override fun tryApply(grid: CandidateGrid): TechniqueResult? = null
}

class SimpleColoringTechnique : Technique {
    override val tier = TechniqueTier.SIMPLE_COLORING
    override fun tryApply(grid: CandidateGrid): TechniqueResult? = null
}

class BasicWingsTechnique : Technique {
    override val tier = TechniqueTier.BASIC_WINGS
    override fun tryApply(grid: CandidateGrid): TechniqueResult? = null
}

class UniqueRectangleType1Technique : Technique {
    override val tier = TechniqueTier.UNIQUE_RECTANGLE
    override fun tryApply(grid: CandidateGrid): TechniqueResult? = null
}

class ChainTechnique : Technique {
    override val tier = TechniqueTier.CHAIN
    override fun tryApply(grid: CandidateGrid): TechniqueResult? = null
}
