package com.example.simplesudoku.model

/**
 * The lifecycle state of a single cell.
 *
 * Three states, not two, because "locked" happens for two different reasons
 * that the rest of the app needs to tell apart (rendering, flavor text, and
 * the §9 progress bar, which excludes GIVEN cells from its denominator):
 *
 *  - GIVEN:    part of the original puzzle. Immutable. Never counted as
 *              player progress.
 *  - EDITABLE: empty or holds a player value that hasn't been confirmed
 *              correct yet. Candidates (notes) may exist here regardless
 *              of whether value is set.
 *  - SEALED:   player entered the correct digit and it has been locked.
 *              No re-editing, no re-undoing (see design doc §5 — since
 *              puzzles are unique-solution, there's never a legitimate
 *              reason to revisit a correct digit).
 */
enum class CellState {
    GIVEN,
    EDITABLE,
    SEALED
}

/**
 * A single Sudoku cell.
 *
 * Immutable by design: all changes go through [Grid], which returns a new
 * [Grid] instance rather than mutating cells in place. This keeps undo
 * trivial (just don't apply the last transform) and keeps this layer free
 * of any game-rule logic (guess counting, correctness checking, etc. — that
 * belongs to the Session Controller, not here).
 *
 * @property row 0-based row index (0..8)
 * @property col 0-based column index (0..8)
 * @property value the digit currently shown, or 0 if empty. For GIVEN
 *   cells this is the puzzle's original digit and never changes.
 * @property state see [CellState]
 * @property candidates pencil-mark notes (1..9). Independent of [value] —
 *   a cell can hold notes while still being empty of an answer. Notes are
 *   free/unlimited per the design doc (§5) and are never checked against
 *   the solution.
 */
data class Cell(
    val row: Int,
    val col: Int,
    val value: Int = 0,
    val state: CellState = CellState.EDITABLE,
    val candidates: Set<Int> = emptySet()
) {
    init {
        require(row in 0..8) { "row must be 0..8, was $row" }
        require(col in 0..8) { "col must be 0..8, was $col" }
        require(value in 0..9) { "value must be 0..9, was $value" }
        require(candidates.all { it in 1..9 }) { "candidates must be 1..9, was $candidates" }
    }

    /** 0-based index (0..8) of the 3x3 box this cell belongs to, row-major. */
    val boxIndex: Int
        get() = (row / 3) * 3 + (col / 3)

    val isEmpty: Boolean
        get() = value == 0

    /**
     * True when this cell is EDITABLE but already holds a digit. Given the
     * atomic guess/seal design (correct entries seal immediately), the only
     * way this can be true is a wrong entry that hasn't been undone yet.
     * The UI should treat this as "frozen" - only Undo is allowed, no
     * second digit may be entered until the cell is cleared.
     */
    val hasPendingWrongEntry: Boolean
        get() = state == CellState.EDITABLE && !isEmpty

    val isLocked: Boolean
        get() = state == CellState.GIVEN || state == CellState.SEALED
}