package com.example.simplesudoku.session

import com.example.simplesudoku.model.Cell
import com.example.simplesudoku.model.CellState
import com.example.simplesudoku.model.Grid
import com.example.simplesudoku.model.MoveValidator

/**
 * The outcome of a single answer attempt.
 *
 *  - CORRECT: digit matched the solution. Cell is sealed immediately, free.
 *  - WRONG_GUESS_CHARGED: digit was wrong, a free guess was available and
 *    has now been spent. The wrong digit IS committed into the grid (§4:
 *    "any wrong final-answer entry... costs 1 guess, permanently" - the
 *    player can see and choose to Undo it, but the guess is already gone).
 *  - WRONG_PAYMENT_REQUIRED: digit was wrong AND the free guess budget is
 *    already at 0. Nothing was committed to the grid. The caller must
 *    show the payment/ad/restart prompt (§4) and, if the player pays or
 *    watches an ad, call [GameSessionController.confirmPaidEntry] to
 *    actually commit the digit.
 */
enum class AnswerOutcome {
    CORRECT,
    WRONG_GUESS_CHARGED,
    WRONG_PAYMENT_REQUIRED
}

/**
 * Result of [GameSessionController.enterAnswer] or
 * [GameSessionController.confirmPaidEntry].
 *
 * @property conflictingCells peer cells sharing the same digit, for the
 *   wobble/highlight (empty for CORRECT, and empty for
 *   WRONG_PAYMENT_REQUIRED since nothing was committed yet).
 * @property guessesRemaining free guesses left after this attempt.
 * @property paymentCost only set when outcome is WRONG_PAYMENT_REQUIRED —
 *   the bullet cost of unlocking one more guess right now (§4/§8's
 *   escalating 1, 2, 4, 8... curve). The caller decides whether the
 *   player pays this, watches a rewarded ad instead, or restarts.
 */
data class AnswerResult(
    val outcome: AnswerOutcome,
    val conflictingCells: Set<Cell>,
    val guessesRemaining: Int,
    val paymentCost: Int? = null
)

/**
 * Owns one puzzle-solving session: the guess budget, correctness checking
 * against the solved grid, sealing, and the solve timer.
 *
 * Deliberately does NOT own: bullet balances, ad SDK calls, hints, streaks,
 * or puzzle selection — those are separate modules/economy layers (§7, §8,
 * Puzzle Pack Manager, Streak Tracker). This class only answers "is this
 * entry allowed right now, and what happens if it is."
 *
 * One instance per puzzle attempt. Guess budget does not carry over
 * between puzzles (§4) - simply construct a new controller for the next
 * puzzle rather than trying to reset this one.
 *
 * @param initialGrid a Grid already built via [Grid.fromGivens] for this
 *   puzzle.
 * @param solution the fully solved 9x9 grid for this puzzle (from
 *   DlxSudokuSolver), used only for correctness checks. Never exposed.
 * @param clock time source in milliseconds, injectable for testing.
 */
class GameSessionController(
    initialGrid: Grid,
    private val solution: Array<IntArray>,
    private val clock: () -> Long = System::currentTimeMillis
) {
    companion object {
        const val FREE_GUESS_BUDGET = 3
    }

    init {
        require(solution.size == 9 && solution.all { it.size == 9 }) {
            "solution must be a 9x9 array"
        }
        require(solution.all { row -> row.all { it in 1..9 } }) {
            "solution must be fully solved (every cell 1..9)"
        }
    }

    var grid: Grid = initialGrid
        private set

    var guessesRemaining: Int = FREE_GUESS_BUDGET
        private set

    /** How many extra (post-free-budget) guesses have been paid for so far. */
    var extraGuessesSpent: Int = 0
        private set

    private var elapsedMillisAccrued: Long = 0
    private var runStartedAt: Long? = null

    /** True once every non-given cell is sealed correctly. */
    val isComplete: Boolean
        get() = grid.isComplete

    /** (cells correctly filled) / (total empty cells at start), per §9. */
    val progress: Float
        get() = grid.progress

    /**
     * Attempts to commit `digit` into (row, col) as a real answer.
     *
     * Throws [IllegalStateException] if the cell isn't EDITABLE (GIVEN or
     * SEALED cells should never reach here - the UI is expected to gate
     * input before calling this, same fail-fast philosophy as [Grid]).
     */
    fun enterAnswer(row: Int, col: Int, digit: Int): AnswerResult {
        require(digit in 1..9) { "digit must be 1..9, was $digit" }
        val cell = grid.cellAt(row, col)
        check(cell.state == CellState.EDITABLE) {
            "Cell ($row,$col) is ${cell.state}, not EDITABLE — input should be gated " +
                    "before reaching GameSessionController."
        }
        check(cell.isEmpty) {
            "Cell ($row,$col) already holds a wrong entry (${cell.value}) — call undo() " +
                    "before entering a new digit. The UI should only offer Undo here, not " +
                    "the number pad."
        }

        val isCorrect = digit == solution[row][col]

        if (isCorrect) {
            grid = grid.withAnswerEntered(row, col, digit)
                .withCandidatesCleared(row, col)
                .withCellSealed(row, col)
            return AnswerResult(
                outcome = AnswerOutcome.CORRECT,
                conflictingCells = emptySet(),
                guessesRemaining = guessesRemaining
            )
        }

        // Wrong digit.
        if (guessesRemaining > 0) {
            grid = grid.withAnswerEntered(row, col, digit)
            guessesRemaining -= 1
            val conflicts = MoveValidator.checkConflicts(grid, row, col).conflictingCells
            return AnswerResult(
                outcome = AnswerOutcome.WRONG_GUESS_CHARGED,
                conflictingCells = conflicts,
                guessesRemaining = guessesRemaining
            )
        }

        // Free budget exhausted - do NOT commit. Caller must resolve payment.
        return AnswerResult(
            outcome = AnswerOutcome.WRONG_PAYMENT_REQUIRED,
            conflictingCells = emptySet(),
            guessesRemaining = 0,
            paymentCost = nextExtraGuessCost()
        )
    }

    /**
     * Finalizes a wrong entry that was previously blocked with
     * WRONG_PAYMENT_REQUIRED, after the caller has confirmed payment
     * happened (bullets deducted, or a rewarded ad was watched — this
     * class doesn't care which; both unlock the same next guess slot,
     * per §8's escalating cost curve).
     *
     * `digit` is expected to be the same wrong digit the player was
     * trying to enter — this does not re-check correctness against a
     * *different* digit than what triggered the payment prompt. If the
     * digit happens to equal the solution (shouldn't normally happen,
     * since the caller only reaches this after enterAnswer already
     * confirmed it was wrong), it is sealed for free rather than
     * silently miscounted as a paid wrong guess.
     */
    fun confirmPaidEntry(row: Int, col: Int, digit: Int): AnswerResult {
        require(digit in 1..9) { "digit must be 1..9, was $digit" }
        val cell = grid.cellAt(row, col)
        check(cell.state == CellState.EDITABLE) {
            "Cell ($row,$col) is ${cell.state}, not EDITABLE."
        }

        if (digit == solution[row][col]) {
            grid = grid.withAnswerEntered(row, col, digit)
                .withCandidatesCleared(row, col)
                .withCellSealed(row, col)
            return AnswerResult(AnswerOutcome.CORRECT, emptySet(), guessesRemaining)
        }

        grid = grid.withAnswerEntered(row, col, digit)
        extraGuessesSpent += 1
        val conflicts = MoveValidator.checkConflicts(grid, row, col).conflictingCells
        return AnswerResult(
            outcome = AnswerOutcome.WRONG_GUESS_CHARGED,
            conflictingCells = conflicts,
            guessesRemaining = 0
        )
    }

    /**
     * Bullet cost to unlock the next guess beyond the free budget.
     * Escalating per §4/§8: 1, 2, 4, 8... (2^extraGuessesSpent) -
     * specifically so brute-forcing many cells becomes prohibitively
     * expensive rather than just mildly annoying.
     */
    fun nextExtraGuessCost(): Int = 1 shl extraGuessesSpent

    /** Toggles a pencil-mark note. Free and unlimited (§5). */
    fun toggleCandidate(row: Int, col: Int, digit: Int) {
        grid = grid.withCandidateToggled(row, col, digit)
    }

    /**
     * Clears a wrong entry's value (Undo). Never touches the guess
     * counter - the guess was already spent atomically at entry time (§4).
     * Throws if the cell is SEALED (can't undo a locked-correct cell) or
     * GIVEN, same as the underlying Grid behavior.
     */
    fun undo(row: Int, col: Int) {
        grid = grid.withCellCleared(row, col)
    }

    /** Starts or resumes the solve timer. No-op if already running. */
    fun resumeTimer() {
        if (runStartedAt == null) {
            runStartedAt = clock()
        }
    }

    /**
     * Pauses the solve timer, folding elapsed time into the accrued total.
     * Intended to be called from Android lifecycle callbacks (onPause) per
     * §10. No-op if already paused.
     */
    fun pauseTimer() {
        val startedAt = runStartedAt ?: return
        elapsedMillisAccrued += clock() - startedAt
        runStartedAt = null
    }

    /** Total active solving time in milliseconds, including any in-progress run. */
    fun elapsedMillis(): Long {
        val inProgress = runStartedAt?.let { clock() - it } ?: 0
        return elapsedMillisAccrued + inProgress
    }
}