package com.example.simplesudoku.model

/**
 * Result of a conflict check for one cell's current value.
 *
 * @property conflictingCells the peer cells that share the same digit
 *   (empty if no conflict). These are exactly the cells the UI should
 *   wobble/highlight alongside the cell that was just entered.
 */
data class ConflictResult(
    val conflictingCells: Set<Cell>
) {
    val hasConflict: Boolean
        get() = conflictingCells.isNotEmpty()
}

/**
 * Structural (row/col/box) conflict checking — the wobble.
 *
 * Deliberately blind to the actual solution (§3): this only tells you
 * whether a digit duplicates another digit already visible on the board.
 * A digit can be fully conflict-free here and still be the wrong answer —
 * that's the whole point of keeping this separate from the rationed
 * correctness check against the solved grid, which lives in Session
 * Controller, not here.
 *
 * Free and unlimited to call, same as the wobble itself (§3/§4) — there's
 * no cost or state associated with this check, it's a pure function of
 * the current Grid.
 */
object MoveValidator {

    /**
     * Checks whether the digit currently in cell (row, col) conflicts with
     * any of its peers (same row, column, or box).
     *
     * Ignores empty peers (value == 0) since an empty cell can't conflict
     * with anything. If the cell itself is empty, returns no conflicts —
     * there's nothing to check yet.
     */
    fun checkConflicts(grid: Grid, row: Int, col: Int): ConflictResult {
        val cell = grid.cellAt(row, col)
        if (cell.isEmpty) return ConflictResult(emptySet())

        val conflicting = grid.peersOf(row, col)
            .filter { peer -> !peer.isEmpty && peer.value == cell.value }
            .toSet()

        return ConflictResult(conflicting)
    }

    /**
     * Convenience check for "would placing `digit` at (row, col) conflict
     * right now" — useful for live feedback while the player is still
     * choosing a digit, before it's actually committed via
     * [Grid.withAnswerEntered]. Does not require the cell to currently
     * hold that digit.
     */
    fun wouldConflict(grid: Grid, row: Int, col: Int, digit: Int): Boolean {
        require(digit in 1..9) { "digit must be 1..9, was $digit" }
        return grid.peersOf(row, col).any { peer -> !peer.isEmpty && peer.value == digit }
    }
}