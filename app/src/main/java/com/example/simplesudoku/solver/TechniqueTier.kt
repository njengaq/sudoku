package com.example.simplesudoku.solver

/**
 * Ordered from easiest-to-spot to hardest. Order matters as much as the
 * weight: DifficultyRater always tries tiers in this order, lowest first,
 * so a puzzle is never credited with needing a hard technique if an
 * easier one would have worked just as well at that point in the solve.
 *
 * Weights are a tuning knob, not gospel - expect to adjust these once we
 * see real puzzles land in each label.
 */
enum class TechniqueTier(val displayName: String, val baseWeight: Int) {
    SINGLES("Singles", 1),
    LOCKED_CANDIDATES("Locked Candidates", 2),
    SUBSETS("Naked/Hidden Subsets", 3),
    BASIC_FISH("Basic Fish (X-Wing)", 5),
    SIMPLE_COLORING("Simple Coloring", 8),
    BASIC_WINGS("Basic Wings (XY-Wing)", 12),
    UNIQUE_RECTANGLE("Unique Rectangle Type 1", 18),
    CHAIN("Chain", 25),
    BIFURCATION("Bifurcation", 100) // deliberately dominant - "Legend" tier
}
