package com.example.simplesudoku.solver

/**
 * Generates valid, uniquely-solvable Sudoku puzzles.
 *
 * REVISED AGAIN (see chat history): digHoles no longer just rates at
 * checkpoints and accepts whatever it finds. Instead, every single
 * candidate removal is gated by TWO checks (three for EASY), each working
 * exactly like the existing uniqueness check already did - "does this
 * specific removal break something? If so, put the clue back and try a
 * different cell":
 *
 *   1. Uniqueness (unchanged) - does the puzzle still have exactly one
 *      solution?
 *   2. No forced guess - does the puzzle still fully solve WITHOUT
 *      Bifurcation? If a removal would force a guess, it's rejected, same
 *      as a uniqueness failure. This means LEGEND is now essentially
 *      unreachable from EASY/MEDIUM/HARD/PRO generation - which matches
 *      targetLabel below (LEGEND was never a target for those four), but
 *      is worth remembering: LEGEND has its own separate digging mode
 *      (digHolesLegend, below) that deliberately allows what this one
 *      avoids, gated behind a clue-count cut line - see that method's doc.
 *   3. EASY-only: does this removal keep the puzzle AT EASY, not push it
 *      past it? EASY is the lowest possible label, so "reached target"
 *      can't mean "at least this hard" the way it does for the other
 *      three - it means "stay this easy, but dig as deep as clueFloor
 *      allows." Same rejection pattern as gate 2, just with EASY's own
 *      label as the ceiling instead of Bifurcation.
 *
 * This also fixes a real bug from the checkpoint version: comparing
 * "current label >= EASY" is trivially true on the very first checkpoint,
 * since EASY is the minimum possible ordinal - which is why every EASY
 * puzzle previously stopped after one checkpoint interval (4 clues) no
 * matter what. Excluding EASY from that check (gate 3 replaces it) fixes
 * this.
 *
 * Performance note: gate 2's rating call (DifficultyRater.rateWithoutBifurcation)
 * never touches DlxSudokuSolver - Bifurcation is the only tier that does -
 * so checking on every single candidate removal is cheap. The previous
 * checkpoint version actually did MORE expensive work per rating call
 * (full rate(), including repeated DLX solves whenever Bifurcation fired),
 * just less often.
 */
class SudokuGenerator(
    private val uniquenessCounter: DlxUniquenessCounter = DlxUniquenessCounter(),
    private val difficultyRater: DifficultyRater = DifficultyRater()
) {
    /**
     * clueFloor: hard stop on digging regardless of whether targetLabel
     * has been reached - a per-label floor purely for player-facing
     * "don't look absurdly sparse" reasons (EASY/MEDIUM keep generous
     * floors; HARD/PRO get real room to dig; LEGEND gets the most room of
     * all, since it needs to dig deep enough to plausibly force a guess).
     *
     * targetLabel: the DifficultyRater tier digging aims for.
     */
    enum class Difficulty(val clueFloor: Int, val targetLabel: DifficultyLabel) {
        EASY(36, DifficultyLabel.EASY),
        MEDIUM(32, DifficultyLabel.MEDIUM),
        HARD(28, DifficultyLabel.HARD),
        PRO(22, DifficultyLabel.PRO),
        LEGEND(18, DifficultyLabel.LEGEND) // NEW - see digHolesLegend for how this is reached
    }

    data class GeneratedPuzzle(
        val puzzle: Array<IntArray>,
        val solution: Array<IntArray>,
        val clueCount: Int,
        val clueTarget: Difficulty,
        val difficultyLabel: DifficultyLabel,
        val floorTier: TechniqueTier,
        val tierUsageCounts: Map<TechniqueTier, Int>,
        val ratingScore: Int,
        val usedBifurcation: Boolean // true only for LEGEND puzzles that actually required a guess
    )

    /** Bundles a dig attempt's result with the rating that produced it - avoids re-rating. */
    private data class DigOutcome(
        val puzzle: Array<IntArray>,
        val clueCount: Int,
        val rating: RaterResult
    )

    companion object {
        private const val N = 9
        private const val ABSOLUTE_MIN_CLUES = 17

        // NEW - placeholder default, NOT yet tuned against real generated output.
        // Below this remaining-clue count, digHolesLegend is allowed to accept a
        // removal that forces a guess (Bifurcation). At or above it, LEGEND digs
        // exactly like PRO - no guesses allowed. This is what stops a lucky early
        // guess-requiring removal from shipping as LEGEND at, say, 71 clues: the
        // puzzle must already be substantially hollowed out before a guess
        // requirement counts toward the label. Revisit once real LEGEND puzzles
        // start coming out of digHolesLegend.
        private const val LEGEND_GUESS_CUTLINE = 24

        private val seedGrid = arrayOf(
            intArrayOf(5,3,4, 6,7,8, 9,1,2),
            intArrayOf(6,7,2, 1,9,5, 3,4,8),
            intArrayOf(1,9,8, 3,4,2, 5,6,7),
            intArrayOf(8,5,9, 7,6,1, 4,2,3),
            intArrayOf(4,2,6, 8,5,3, 7,9,1),
            intArrayOf(7,1,3, 9,2,4, 8,5,6),
            intArrayOf(9,6,1, 5,3,7, 2,8,4),
            intArrayOf(2,8,7, 4,1,9, 6,3,5),
            intArrayOf(3,4,5, 2,8,6, 1,7,9)
        )

        init {
            check(Difficulty.values().all { it.clueFloor >= ABSOLUTE_MIN_CLUES }) {
                "Every Difficulty.clueFloor must be >= ABSOLUTE_MIN_CLUES ($ABSOLUTE_MIN_CLUES)."
            }
            check(LEGEND_GUESS_CUTLINE >= Difficulty.LEGEND.clueFloor) {
                "LEGEND_GUESS_CUTLINE must be >= LEGEND's clueFloor, or the guess-allowed " +
                        "phase would never have room to run."
            }
        }
    }

    /**
     * Generates a puzzle whose DifficultyRater label matches (or exceeds,
     * for non-EASY targets) [target]'s targetLabel tier. For EASY/MEDIUM/
     * HARD/PRO this never needs a guess to solve; for LEGEND, a guess is
     * specifically what's being aimed for (see digHolesLegend). Retries
     * with a fresh shuffle if an attempt gets stuck below target before
     * its clueFloor - not every arrangement digs down to the same
     * technique requirement.
     *
     * If every attempt falls short, ships the closest attempt found
     * (smallest gap to the target label) - never throws.
     */
    fun generate(target: Difficulty, maxAttempts: Int = 8): GeneratedPuzzle {
        var best: Pair<Array<IntArray>, DigOutcome>? = null
        var bestGap = Int.MAX_VALUE

        repeat(maxAttempts) {
            val solved = randomizeGrid(seedGrid)
            val outcome = if (target == Difficulty.LEGEND) {
                digHolesLegend(solved)
            } else {
                digHoles(solved, target)
            }

            if (outcome.rating.label.ordinal >= target.targetLabel.ordinal) {
                return buildResult(solved, outcome, target)
            }

            val gap = target.targetLabel.ordinal - outcome.rating.label.ordinal
            if (gap < bestGap) {
                bestGap = gap
                best = solved to outcome
            }
        }

        val (solved, outcome) = best!!
        println(
            "Note: ${target.name} generation didn't reach ${target.targetLabel} after " +
                    "$maxAttempts attempts - shipping closest result (${outcome.rating.label}, " +
                    "${outcome.clueCount} clues). Expected occasionally at the hardest tiers, given " +
                    "our technique catalog is intentionally smaller than a full solver's - not a bug."
        )
        return buildResult(solved, outcome, target)
    }

    private fun buildResult(
        solved: Array<IntArray>,
        outcome: DigOutcome,
        target: Difficulty
    ): GeneratedPuzzle = GeneratedPuzzle(
        puzzle = outcome.puzzle,
        solution = solved,
        clueCount = outcome.clueCount,
        clueTarget = target,
        difficultyLabel = outcome.rating.label,
        floorTier = outcome.rating.floorTier,
        tierUsageCounts = outcome.rating.tierUsageCounts,
        ratingScore = outcome.rating.rawScore,
        usedBifurcation = outcome.rating.usedBifurcation
    )

    private fun snapshot(grid: Array<IntArray>): Array<IntArray> =
        grid.map { it.copyOf() }.toTypedArray()

    // ---- Phase 1: random valid solved grid via seed transformation (unchanged) ----

    private fun randomizeGrid(seed: Array<IntArray>): Array<IntArray> {
        var grid = seed.map { it.copyOf() }.toTypedArray()
        grid = relabelDigits(grid)
        grid = shuffleRowsWithinBands(grid)
        grid = shuffleColsWithinStacks(grid)
        grid = shuffleBands(grid)
        grid = shuffleStacks(grid)
        if (listOf(true, false).random()) grid = transpose(grid)
        return grid
    }

    private fun relabelDigits(grid: Array<IntArray>): Array<IntArray> {
        val mapping = (1..9).shuffled()
        return Array(N) { r -> IntArray(N) { c -> mapping[grid[r][c] - 1] } }
    }

    private fun shuffleRowsWithinBands(grid: Array<IntArray>): Array<IntArray> {
        val newRowOrder = (0 until N).toMutableList()
        for (band in 0..2) {
            val bandRows = (band * 3 until band * 3 + 3).shuffled()
            for (i in 0..2) newRowOrder[band * 3 + i] = bandRows[i]
        }
        return Array(N) { r -> grid[newRowOrder[r]].copyOf() }
    }

    private fun shuffleColsWithinStacks(grid: Array<IntArray>): Array<IntArray> {
        val newColOrder = (0 until N).toMutableList()
        for (stack in 0..2) {
            val stackCols = (stack * 3 until stack * 3 + 3).shuffled()
            for (i in 0..2) newColOrder[stack * 3 + i] = stackCols[i]
        }
        return Array(N) { r -> IntArray(N) { c -> grid[r][newColOrder[c]] } }
    }

    private fun shuffleBands(grid: Array<IntArray>): Array<IntArray> {
        val bandOrder = listOf(0, 1, 2).shuffled()
        val newRowOrder = bandOrder.flatMap { band -> (band * 3 until band * 3 + 3) }
        return Array(N) { r -> grid[newRowOrder[r]].copyOf() }
    }

    private fun shuffleStacks(grid: Array<IntArray>): Array<IntArray> {
        val stackOrder = listOf(0, 1, 2).shuffled()
        val newColOrder = stackOrder.flatMap { stack -> (stack * 3 until stack * 3 + 3) }
        return Array(N) { r -> IntArray(N) { c -> grid[r][newColOrder[c]] } }
    }

    private fun transpose(grid: Array<IntArray>): Array<IntArray> =
        Array(N) { r -> IntArray(N) { c -> grid[c][r] } }

    // ---- Phase 2: symmetric digging, gated by uniqueness AND difficulty ----

    /**
     * Digs symmetric clue pairs from [solved]. Every candidate removal
     * must pass:
     *   1. Uniqueness (as before).
     *   2. rateWithoutBifurcation succeeds (doesn't force a guess).
     *   3. For EASY only: the resulting label doesn't exceed EASY.
     * A removal failing any gate is reverted, exactly like a uniqueness
     * failure - "try a different cell" instead of "give up."
     *
     * Stops at whichever comes first: target label reached (non-EASY),
     * clueFloor reached, or no more removable cells. Always returns a
     * real, rated DigOutcome - generate() handles a below-target result
     * via its best-effort fallback.
     *
     * Used for EASY, MEDIUM, HARD, and PRO. LEGEND uses digHolesLegend
     * instead - see that method's doc for why.
     */
    private fun digHoles(solved: Array<IntArray>, difficulty: Difficulty): DigOutcome {
        val puzzle = solved.map { it.copyOf() }.toTypedArray()
        var cluesRemaining = N * N

        // Base case (no cells removed yet): trivially EASY, floor SINGLES, score 0.
        var lastGoodPuzzle = snapshot(puzzle)
        var lastGoodClues = cluesRemaining
        var lastGoodRating = difficultyRater.rateWithoutBifurcation(puzzle)!!

        val cellOrder = (0 until N * N).shuffled()
        val tried = BooleanArray(N * N)

        for (idx in cellOrder) {
            if (tried[idx]) continue
            val r = idx / N
            val c = idx % N
            val mr = (N - 1) - r
            val mc = (N - 1) - c
            val mirrorIdx = mr * N + mc
            val isCenter = (r == mr && c == mc)

            tried[idx] = true
            if (!isCenter) tried[mirrorIdx] = true
            if (puzzle[r][c] == 0) continue

            val removedCount = if (isCenter) 1 else 2
            if (cluesRemaining - removedCount < difficulty.clueFloor) continue

            val backupPrimary = puzzle[r][c]
            val backupMirror = puzzle[mr][mc]
            puzzle[r][c] = 0
            if (!isCenter) puzzle[mr][mc] = 0

            // Gate 1: uniqueness.
            if (uniquenessCounter.countSolutions(puzzle, cap = 2) != 1) {
                puzzle[r][c] = backupPrimary
                puzzle[mr][mc] = backupMirror
                continue
            }

            // Gate 2: would this removal force a guess? Reject it, same as gate 1.
            val rating = difficultyRater.rateWithoutBifurcation(puzzle)
            if (rating == null) {
                puzzle[r][c] = backupPrimary
                puzzle[mr][mc] = backupMirror
                continue
            }

            // Gate 3 (EASY only): would this removal push the label past EASY?
            if (difficulty == Difficulty.EASY && rating.label.ordinal > difficulty.targetLabel.ordinal) {
                puzzle[r][c] = backupPrimary
                puzzle[mr][mc] = backupMirror
                continue
            }

            cluesRemaining -= removedCount
            lastGoodPuzzle = snapshot(puzzle)
            lastGoodClues = cluesRemaining
            lastGoodRating = rating

            // Non-EASY targets: stop as soon as we've reached (or passed) the target
            // tier. EASY is excluded here on purpose - see class doc for why ">=" is
            // meaningless against the lowest possible label; gate 3 above is what
            // keeps EASY honest instead, and this loop just keeps digging toward
            // clueFloor for it.
            val reachedTarget = difficulty != Difficulty.EASY &&
                    rating.label.ordinal >= difficulty.targetLabel.ordinal
            val reachedFloor = cluesRemaining <= difficulty.clueFloor

            if (reachedTarget || reachedFloor) {
                return DigOutcome(lastGoodPuzzle, lastGoodClues, lastGoodRating)
            }
        }

        // Ran out of removable cells before reaching target or floor (every
        // remaining candidate either broke uniqueness or would have forced a
        // guess) - ship the last known-good, guess-free state found.
        return DigOutcome(lastGoodPuzzle, lastGoodClues, lastGoodRating)
    }

    /**
     * NEW - UNTESTED against real generated output yet. Cut-line value
     * (LEGEND_GUESS_CUTLINE) is an unvalidated placeholder.
     *
     * LEGEND-specific digging, kept entirely separate from digHoles rather
     * than folded into it, since the gating rule genuinely changes
     * mid-dig:
     *
     *   Phase 1 (cluesRemaining after removal > LEGEND_GUESS_CUTLINE):
     *     behaves exactly like digHoles for PRO - gate 1 (uniqueness) +
     *     gate 2 (no forced guess, via rateWithoutBifurcation - cheap,
     *     never touches DlxSudokuSolver).
     *
     *   Phase 2 (cluesRemaining after removal <= LEGEND_GUESS_CUTLINE):
     *     gate 2 relaxes - a removal that forces a guess is now
     *     acceptable. Gate 1 (uniqueness) is NEVER relaxed: a guess is
     *     fine, an ambiguous puzzle never is. This phase calls the FULL
     *     rate() (Bifurcation-capable, does real DLX solves), so it's
     *     meaningfully more expensive per removal than phase 1 - expected,
     *     since it only runs on a small tail of removals near the floor.
     *
     * The cut line exists specifically so a removal isn't credited as
     * "LEGEND-worthy" just because it happened to require a guess very
     * early (e.g. at 71 clues, before the puzzle is meaningfully dug) -
     * the guess requirement only counts once the puzzle is already
     * substantially hollowed out.
     */
    private fun digHolesLegend(solved: Array<IntArray>): DigOutcome {
        val puzzle = solved.map { it.copyOf() }.toTypedArray()
        var cluesRemaining = N * N

        var lastGoodPuzzle = snapshot(puzzle)
        var lastGoodClues = cluesRemaining
        var lastGoodRating = difficultyRater.rateWithoutBifurcation(puzzle)!!

        val cellOrder = (0 until N * N).shuffled()
        val tried = BooleanArray(N * N)

        for (idx in cellOrder) {
            if (tried[idx]) continue
            val r = idx / N
            val c = idx % N
            val mr = (N - 1) - r
            val mc = (N - 1) - c
            val mirrorIdx = mr * N + mc
            val isCenter = (r == mr && c == mc)

            tried[idx] = true
            if (!isCenter) tried[mirrorIdx] = true
            if (puzzle[r][c] == 0) continue

            val removedCount = if (isCenter) 1 else 2
            val postRemovalClues = cluesRemaining - removedCount
            if (postRemovalClues < Difficulty.LEGEND.clueFloor) continue

            val backupPrimary = puzzle[r][c]
            val backupMirror = puzzle[mr][mc]
            puzzle[r][c] = 0
            if (!isCenter) puzzle[mr][mc] = 0

            // Gate 1: uniqueness - never relaxed, guess or no guess.
            if (uniquenessCounter.countSolutions(puzzle, cap = 2) != 1) {
                puzzle[r][c] = backupPrimary
                puzzle[mr][mc] = backupMirror
                continue
            }

            val allowGuess = postRemovalClues <= LEGEND_GUESS_CUTLINE

            val rating: RaterResult = if (!allowGuess) {
                // Phase 1: same rule as PRO - reject anything that would force a guess.
                difficultyRater.rateWithoutBifurcation(puzzle) ?: run {
                    puzzle[r][c] = backupPrimary
                    puzzle[mr][mc] = backupMirror
                    continue
                }
            } else {
                // Phase 2: guess-requiring removals are now acceptable. Full rate()
                // is Bifurcation-capable and touches DlxSudokuSolver - real cost per
                // removal from here down, unlike phase 1's cheap gate-2 checks.
                val full = difficultyRater.rate(puzzle)
                if (!full.solved) {
                    // Safety net only - rate() should always solve a uniquely-solvable
                    // grid (Bifurcation is designed to always find something with 2+
                    // unsolved cells). Treat an unsolved result as a rejection.
                    puzzle[r][c] = backupPrimary
                    puzzle[mr][mc] = backupMirror
                    continue
                }
                full
            }

            cluesRemaining -= removedCount
            lastGoodPuzzle = snapshot(puzzle)
            lastGoodClues = cluesRemaining
            lastGoodRating = rating

            val reachedTarget = rating.label.ordinal >= DifficultyLabel.LEGEND.ordinal
            val reachedFloor = cluesRemaining <= Difficulty.LEGEND.clueFloor

            if (reachedTarget || reachedFloor) {
                return DigOutcome(lastGoodPuzzle, lastGoodClues, lastGoodRating)
            }
        }

        // Ran out of removable cells before reaching LEGEND or the floor (every
        // remaining candidate either broke uniqueness, or - above the cut line -
        // would have forced a guess too early to count). Ship the last known-good
        // state found; generate()'s best-effort fallback handles a below-target
        // result the same way it does for the other four tiers.
        return DigOutcome(lastGoodPuzzle, lastGoodClues, lastGoodRating)
    }
}