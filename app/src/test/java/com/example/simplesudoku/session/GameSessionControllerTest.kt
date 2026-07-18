package com.example.simplesudoku.session

import com.example.simplesudoku.model.CellState
import com.example.simplesudoku.model.Grid
import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Assert.fail

class GameSessionControllerTest {

    /**
     * A known-valid, fully-solved 9x9 grid (verified by hand: every row,
     * column, and 3x3 box contains 1-9 exactly once). Used as the "solution"
     * for tests - the controller only ever compares against it, never solves.
     */
    private fun sampleSolution(): Array<IntArray> = arrayOf(
        intArrayOf(1, 2, 3, 4, 5, 6, 7, 8, 9),
        intArrayOf(4, 5, 6, 7, 8, 9, 1, 2, 3),
        intArrayOf(7, 8, 9, 1, 2, 3, 4, 5, 6),
        intArrayOf(2, 3, 1, 5, 6, 4, 8, 9, 7),
        intArrayOf(5, 6, 4, 8, 9, 7, 2, 3, 1),
        intArrayOf(8, 9, 7, 2, 3, 1, 5, 6, 4),
        intArrayOf(3, 1, 2, 6, 4, 5, 9, 7, 8),
        intArrayOf(6, 4, 5, 9, 7, 8, 3, 1, 2),
        intArrayOf(9, 7, 8, 3, 1, 2, 6, 4, 5)
    )

    /** All cells empty (0), so every cell is EDITABLE and free to write to in tests. */
    private fun emptyGivens(): Array<IntArray> = Array(9) { IntArray(9) }

    private fun newController(): GameSessionController {
        val grid = Grid.fromGivens(emptyGivens())
        return GameSessionController(grid, sampleSolution())
    }

    @Test
    fun `correct entry seals the cell and does not spend a guess`() {
        val controller = newController()
        val result = controller.enterAnswer(0, 0, 1) // solution[0][0] == 1

        assertEquals(AnswerOutcome.CORRECT, result.outcome)
        assertEquals(GameSessionController.FREE_GUESS_BUDGET, result.guessesRemaining)
        assertEquals(CellState.SEALED, controller.grid.cellAt(0, 0).state)
    }

    @Test
    fun `wrong entry within budget commits the digit and spends one guess`() {
        val controller = newController()
        val result = controller.enterAnswer(0, 0, 9) // solution[0][0] == 1, so 9 is wrong

        assertEquals(AnswerOutcome.WRONG_GUESS_CHARGED, result.outcome)
        assertEquals(GameSessionController.FREE_GUESS_BUDGET - 1, result.guessesRemaining)
        assertEquals(GameSessionController.FREE_GUESS_BUDGET - 1, controller.guessesRemaining)
        // Wrong digit IS visible in the grid, per §4 - not silently discarded.
        assertEquals(9, controller.grid.cellAt(0, 0).value)
        assertEquals(CellState.EDITABLE, controller.grid.cellAt(0, 0).state)
    }

    @Test
    fun `three wrong guesses exhaust the free budget, fourth requires payment`() {
        val controller = newController()
        // Three wrong entries in three different (independent) cells,
        // each a genuine miss against the solution.
        controller.enterAnswer(0, 0, 9) // wrong, guesses -> 2
        controller.enterAnswer(0, 1, 9) // wrong, guesses -> 1
        controller.enterAnswer(0, 2, 9) // wrong, guesses -> 0
        assertEquals(0, controller.guessesRemaining)

        val fourth = controller.enterAnswer(0, 3, 9) // wrong again, budget already 0
        assertEquals(AnswerOutcome.WRONG_PAYMENT_REQUIRED, fourth.outcome)
        assertEquals(1, fourth.paymentCost) // first paid guess costs 1

        // Nothing committed for the blocked attempt.
        assertEquals(0, controller.grid.cellAt(0, 3).value)
        assertEquals(CellState.EDITABLE, controller.grid.cellAt(0, 3).state)
    }

    @Test
    fun `payment cost escalates exponentially across multiple paid guesses`() {
        val controller = newController()
        repeat(3) { i -> controller.enterAnswer(0, i, 9) } // burn the free budget

        val first = controller.enterAnswer(0, 3, 9)
        assertEquals(1, first.paymentCost)
        controller.confirmPaidEntry(0, 3, 9)

        val second = controller.enterAnswer(0, 4, 9)
        assertEquals(2, second.paymentCost)
        controller.confirmPaidEntry(0, 4, 9)

        val third = controller.enterAnswer(0, 5, 9)
        assertEquals(4, third.paymentCost)
    }

    @Test
    fun `confirmPaidEntry commits the digit and does not touch free guess count`() {
        val controller = newController()
        repeat(3) { i -> controller.enterAnswer(0, i, 9) }
        assertEquals(0, controller.guessesRemaining)

        controller.enterAnswer(0, 3, 9) // triggers WRONG_PAYMENT_REQUIRED
        val result = controller.confirmPaidEntry(0, 3, 9)

        assertEquals(AnswerOutcome.WRONG_GUESS_CHARGED, result.outcome)
        assertEquals(9, controller.grid.cellAt(0, 3).value)
        assertEquals(0, controller.guessesRemaining) // free budget untouched
        assertEquals(1, controller.extraGuessesSpent)
    }

    @Test
    fun `confirmPaidEntry seals for free if the digit turns out correct`() {
        val controller = newController()
        repeat(3) { i -> controller.enterAnswer(0, i, 9) }
        controller.enterAnswer(0, 3, 9) // block

        // Defensive path: caller passes the correct digit at confirm time.
        val result = controller.confirmPaidEntry(0, 3, 4) // solution[0][3] == 4
        assertEquals(AnswerOutcome.CORRECT, result.outcome)
        assertEquals(CellState.SEALED, controller.grid.cellAt(0, 3).state)
        assertEquals(0, controller.extraGuessesSpent) // not counted as a paid wrong guess
    }

    @Test
    fun `undo clears the value but never refunds a spent guess`() {
        val controller = newController()
        controller.enterAnswer(0, 0, 9) // wrong, spends a guess
        assertEquals(GameSessionController.FREE_GUESS_BUDGET - 1, controller.guessesRemaining)

        controller.undo(0, 0)
        assertEquals(0, controller.grid.cellAt(0, 0).value)
        assertEquals(GameSessionController.FREE_GUESS_BUDGET - 1, controller.guessesRemaining)
    }

    @Test
    fun `enterAnswer throws on a non-editable cell`() {
        val controller = newController()
        controller.enterAnswer(0, 0, 1) // correct -> seals the cell

        try {
            controller.enterAnswer(0, 0, 1)
            fail("expected IllegalStateException on a SEALED cell")
        } catch (e: IllegalStateException) {
            // expected
        }
    }

    @Test
    fun `a wrong entry blocks further entry into the same cell until undo`() {
        val controller = newController()
        controller.enterAnswer(0, 0, 9) // wrong (solution[0][0] == 1), spends a guess
        assertTrue(controller.grid.cellAt(0, 0).hasPendingWrongEntry)

        try {
            controller.enterAnswer(0, 0, 8) // trying a second digit without undo first
            fail("expected IllegalStateException - must undo before re-entering")
        } catch (e: IllegalStateException) {
            // expected
        }

        // Guess count should reflect only the one charge, not two.
        assertEquals(GameSessionController.FREE_GUESS_BUDGET - 1, controller.guessesRemaining)
    }

    @Test
    fun `undo clears the pending-wrong-entry lock and allows re-entry`() {
        val controller = newController()
        controller.enterAnswer(0, 0, 9) // wrong, spends a guess, cell now locked-pending-undo
        controller.undo(0, 0)
        assertFalse(controller.grid.cellAt(0, 0).hasPendingWrongEntry)

        // Re-entry is allowed now - and if wrong again, spends a second guess,
        // which is expected: undo is free but doesn't refund the first guess.
        val result = controller.enterAnswer(0, 0, 8) // still wrong
        assertEquals(AnswerOutcome.WRONG_GUESS_CHARGED, result.outcome)
        assertEquals(GameSessionController.FREE_GUESS_BUDGET - 2, controller.guessesRemaining)
    }

    @Test
    fun `fresh controller always starts with a full guess budget`() {
        val controller = newController()
        assertEquals(GameSessionController.FREE_GUESS_BUDGET, controller.guessesRemaining)
    }

    @Test
    fun `timer accrues time across pause and resume`() {
        var fakeNow = 1_000L
        val grid = Grid.fromGivens(emptyGivens())
        val controller = GameSessionController(grid, sampleSolution(), clock = { fakeNow })

        controller.resumeTimer()
        fakeNow += 5_000L
        controller.pauseTimer()
        assertEquals(5_000L, controller.elapsedMillis())

        // Paused - advancing fake time should NOT accrue more.
        fakeNow += 10_000L
        assertEquals(5_000L, controller.elapsedMillis())

        controller.resumeTimer()
        fakeNow += 2_000L
        assertEquals(7_000L, controller.elapsedMillis())
    }

    @Test
    fun `pauseTimer is a no-op when not running`() {
        var fakeNow = 1_000L
        val grid = Grid.fromGivens(emptyGivens())
        val controller = GameSessionController(grid, sampleSolution(), clock = { fakeNow })

        controller.pauseTimer() // never started
        assertEquals(0L, controller.elapsedMillis())
    }

    @Test
    fun `isComplete and progress delegate to the underlying grid`() {
        val controller = newController()
        assertFalse(controller.isComplete)
        assertEquals(0f, controller.progress, 0.0001f)
    }

    @Test
    fun `constructor rejects a malformed solution`() {
        val grid = Grid.fromGivens(emptyGivens())
        try {
            GameSessionController(grid, Array(8) { IntArray(9) })
            fail("expected IllegalArgumentException for wrong-sized solution")
        } catch (e: IllegalArgumentException) {
            // expected
        }

        try {
            val badSolution = sampleSolution().also { it[0][0] = 0 } // 0 isn't a solved value
            GameSessionController(grid, badSolution)
            fail("expected IllegalArgumentException for an unsolved cell in solution")
        } catch (e: IllegalArgumentException) {
            // expected
        }
    }
}