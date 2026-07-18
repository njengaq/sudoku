package com.example.simplesudoku.model

/**
 * Immutable 9x9 Sudoku board state.
 *
 * `Grid` owns *what the board looks like*, nothing else. It has no concept
 * of guesses, bullets, hints, or the solved grid — those are Session
 * Controller concerns (design doc §4, §7, §8). Grid Model only answers:
 * what is in each cell right now, and what does the board look like after
 * one legal transform?
 *
 * Every mutating function returns a *new* Grid. This is deliberate:
 *  - Undo (§4/§5) becomes "don't apply the last transform" instead of a
 *    separate bookkeeping system.
 *  - It plays cleanly with state-driven UI (e.g. Compose recomposition on
 *    a new immutable value rather than in-place mutation).
 *
 * Mutating functions throw [IllegalStateException] if called on a cell
 * that isn't legally editable (GIVEN or SEALED). This is intentional
 * fail-fast behavior: the UI/Session Controller is expected to gate input
 * before calling these (e.g. don't even show a number pad for a sealed
 * cell), so hitting this exception means a bug upstream, not a normal
 * "ignore and continue" case.
 */
class Grid private constructor(
    private val cells: List<List<Cell>>
) {
    companion object {
        /**
         * Builds a fresh Grid from a puzzle's starting digits.
         *
         * @param givens 9x9 array, 0 = empty, 1-9 = a given digit.
         */
        fun fromGivens(givens: Array<IntArray>): Grid {
            require(givens.size == 9 && givens.all { it.size == 9 }) {
                "givens must be a 9x9 array"
            }
            val built = (0..8).map { row ->
                (0..8).map { col ->
                    val v = givens[row][col]
                    require(v in 0..9) { "given at ($row,$col) must be 0..9, was $v" }
                    if (v == 0) {
                        Cell(row = row, col = col, value = 0, state = CellState.EDITABLE)
                    } else {
                        Cell(row = row, col = col, value = v, state = CellState.GIVEN)
                    }
                }
            }
            return Grid(built)
        }
    }

    /** Returns the cell at (row, col). */
    fun cellAt(row: Int, col: Int): Cell = cells[row][col]

    /** All 81 cells, row-major. */
    fun allCells(): List<Cell> = cells.flatten()

    /**
     * The 20 peer cells for (row, col) — same row, same column, and same
     * 3x3 box, excluding the cell itself. Used by peer-cell highlighting
     * (§1) and will be reused by the future Move Validator's conflict
     * check (§3/§4), so it lives here rather than being duplicated.
     */
    fun peersOf(row: Int, col: Int): List<Cell> {
        val box = cellAt(row, col).boxIndex
        return allCells().filter { c ->
            (c.row == row || c.col == col || c.boxIndex == box) &&
                    !(c.row == row && c.col == col)
        }
    }

    /**
     * Toggles a pencil-mark candidate on/off for an editable cell.
     * No-op on the value; candidates and value are independent (§5).
     */
    fun withCandidateToggled(row: Int, col: Int, digit: Int): Grid {
        require(digit in 1..9) { "digit must be 1..9, was $digit" }
        val cell = requireEditable(row, col)
        val newCandidates = if (digit in cell.candidates) {
            cell.candidates - digit
        } else {
            cell.candidates + digit
        }
        return replace(cell.copy(candidates = newCandidates))
    }

    /**
     * Clears all candidates in a cell. Typically called when a value is
     * committed into that cell, since notes and a committed answer don't
     * usually coexist meaningfully — left as an explicit separate call
     * (rather than automatic inside [withAnswerEntered]) so the Session
     * Controller decides that policy rather than Grid Model assuming it.
     */
    fun withCandidatesCleared(row: Int, col: Int): Grid {
        val cell = requireEditable(row, col)
        return replace(cell.copy(candidates = emptySet()))
    }

    /**
     * Commits a digit as a real answer entry into an editable cell.
     * The cell stays EDITABLE — this function does NOT check correctness
     * or seal the cell. Per §4, correctness checking and the guess-cost
     * decision belong to the Session Controller; it calls this first,
     * decides right/wrong using the solved grid, then follows up with
     * either [withCellSealed] (correct) or [withCellCleared] (wrong,
     * on Undo — see note on withCellCleared).
     */
    fun withAnswerEntered(row: Int, col: Int, digit: Int): Grid {
        require(digit in 1..9) { "digit must be 1..9, was $digit" }
        val cell = requireEditable(row, col)
        return replace(cell.copy(value = digit))
    }

    /**
     * Seals a cell: locks it as SEALED, meaning "correct and permanent."
     * Only legal on an EDITABLE cell that already has a value — sealing
     * an empty cell would make no sense. Called by the Session Controller
     * immediately after it confirms the just-entered digit is correct
     * (§5: entry -> check -> wobble/seal happen atomically at that layer;
     * Grid Model just executes the resulting state change).
     */
    fun withCellSealed(row: Int, col: Int): Grid {
        val cell = requireEditable(row, col)
        check(!cell.isEmpty) { "cannot seal an empty cell at ($row,$col)" }
        return replace(cell.copy(state = CellState.SEALED))
    }

    /**
     * Clears a cell's value (used by Undo). Only legal on EDITABLE cells —
     * SEALED cells cannot be undone (§5: no grace window, no re-editing
     * once sealed) and GIVEN cells were never player-entered in the first
     * place. Candidates are left untouched; Undo only clears the answer
     * entry, not any pencil marks the player had jotted down.
     *
     * Note: per §4, this never touches the guess counter. Guess deduction
     * happens at entry time, atomically with the correctness check, in
     * the Session Controller — undoing afterward is purely visual state.
     */
    fun withCellCleared(row: Int, col: Int): Grid {
        val cell = requireEditable(row, col)
        return replace(cell.copy(value = 0))
    }

    /** Count of GIVEN cells — excluded from the §9 progress bar denominator. */
    val givenCount: Int
        get() = allCells().count { it.state == CellState.GIVEN }

    /** Total empty cells at puzzle start = the §9 progress bar denominator. */
    val editableCellCount: Int
        get() = 81 - givenCount

    /** Cells the player has correctly filled = the §9 progress bar numerator. */
    val sealedCount: Int
        get() = allCells().count { it.state == CellState.SEALED }

    /** (cells correctly filled) / (total empty cells at start), per §9's formula. */
    val progress: Float
        get() = if (editableCellCount == 0) 1f else sealedCount.toFloat() / editableCellCount

    /** True once every non-given cell is sealed. */
    val isComplete: Boolean
        get() = sealedCount == editableCellCount

    private fun requireEditable(row: Int, col: Int): Cell {
        val cell = cellAt(row, col)
        check(cell.state == CellState.EDITABLE) {
            "Cell ($row,$col) is ${cell.state}, not EDITABLE — this call indicates a bug " +
                    "upstream (input should be gated before reaching Grid Model)."
        }
        return cell
    }

    private fun replace(updated: Cell): Grid {
        val newCells = cells.map { rowList ->
            if (rowList[0].row != updated.row) rowList
            else rowList.map { if (it.col == updated.col) updated else it }
        }
        return Grid(newCells)
    }
}