package com.example.simplesudoku.solver

/**
 * Dancing Links (Algorithm X) Sudoku solver.
 *
 * The 9x9 Sudoku is modeled as an Exact Cover problem with 4 constraint families,
 * each with 81 columns (9x9 = 729 total columns):
 *   1. Cell     - each of the 81 cells must contain exactly one digit
 *   2. Row      - each row must contain each digit exactly once
 *   3. Column   - each column must contain each digit exactly once
 *   4. Box      - each 3x3 box must contain each digit exactly once
 *
 * Each of the 729 possible (row, col, digit) placements becomes a matrix row
 * with exactly 4 ones (one per constraint family). DLX finds a set of rows
 * that covers every column exactly once -> a valid, complete Sudoku solution.
 */
class DlxSudokuSolver {

    // ---- Dancing Links node -------------------------------------------------

    private open class Node {
        var left: Node = this
        var right: Node = this
        var up: Node = this
        var down: Node = this
        var column: ColumnNode? = null
        var rowId: Int = -1 // encodes (cellIndex, digit) for solution rows
    }

    private class ColumnNode(val name: Int) : Node() {
        var size: Int = 0
        init { column = this }
    }

    private val header = ColumnNode(-1)
    private val columns = ArrayList<ColumnNode>(COLS)

    companion object {
        private const val N = 9
        private const val BOX = 3
        private const val CELL_CONSTRAINTS = N * N        // 81
        private const val ROW_CONSTRAINTS = N * N          // 81
        private const val COL_CONSTRAINTS = N * N          // 81
        private const val BOX_CONSTRAINTS = N * N          // 81
        private const val COLS = CELL_CONSTRAINTS + ROW_CONSTRAINTS + COL_CONSTRAINTS + BOX_CONSTRAINTS

        private fun cellConstraint(r: Int, c: Int) = r * N + c
        private fun rowConstraint(r: Int, d: Int) = CELL_CONSTRAINTS + r * N + d
        private fun colConstraint(c: Int, d: Int) = CELL_CONSTRAINTS + ROW_CONSTRAINTS + c * N + d
        private fun boxConstraint(r: Int, c: Int, d: Int): Int {
            val box = (r / BOX) * BOX + (c / BOX)
            return CELL_CONSTRAINTS + ROW_CONSTRAINTS + COL_CONSTRAINTS + box * N + d
        }
    }

    init {
        // Build the 324 column headers, linked left-right in a circular list.
        var prev: Node = header
        for (i in 0 until COLS) {
            val col = ColumnNode(i)
            columns.add(col)
            col.left = prev
            prev.right = col
            prev = col
        }
        prev.right = header
        header.left = prev

        // Build the 729 candidate rows (r, c, digit), each hooking into 4 columns.
        for (r in 0 until N) {
            for (c in 0 until N) {
                for (d in 0 until N) {
                    val rowId = (r * N + c) * N + d // encode placement
                    val constraintCols = intArrayOf(
                        cellConstraint(r, c),
                        rowConstraint(r, d),
                        colConstraint(c, d),
                        boxConstraint(r, c, d)
                    )
                    linkRow(rowId, constraintCols)
                }
            }
        }
    }

    private fun linkRow(rowId: Int, colIndices: IntArray) {
        var firstInRow: Node? = null
        var lastInRow: Node? = null
        for (idx in colIndices) {
            val col = columns[idx]
            val node = Node()
            node.rowId = rowId
            node.column = col

            // vertical link into the column
            node.up = col.up
            node.down = col
            col.up.down = node
            col.up = node
            col.size++

            // horizontal link within this row
            if (firstInRow == null) {
                firstInRow = node
                node.left = node
                node.right = node
            } else {
                node.left = lastInRow!!
                node.right = firstInRow
                lastInRow!!.right = node
                firstInRow.left = node
            }
            lastInRow = node
        }
    }

    // ---- Algorithm X core -----------------------------------------------------

    private fun cover(col: ColumnNode) {
        col.right.left = col.left
        col.left.right = col.right
        var row = col.down
        while (row !== col) {
            var node = row.right
            while (node !== row) {
                node.down.up = node.up
                node.up.down = node.down
                node.column!!.size--
                node = node.right
            }
            row = row.down
        }
    }

    private fun uncover(col: ColumnNode) {
        var row = col.up
        while (row !== col) {
            var node = row.left
            while (node !== row) {
                node.column!!.size++
                node.down.up = node
                node.up.down = node
                node = node.left
            }
            row = row.up
        }
        col.right.left = col
        col.left.right = col
    }

    private fun chooseColumn(): ColumnNode? {
        var best: ColumnNode? = null
        var node = header.right
        while (node !== header) {
            val col = node as ColumnNode
            if (best == null || col.size < best.size) best = col
            if (best.size == 0) break // dead end, bail early
            node = node.right
        }
        return best
    }

    /**
     * Runs the search. Returns true (and fills [solutionRows]) as soon as one
     * full solution is found. For a properly-clued Sudoku this returns the
     * unique solution.
     */
    private fun search(solutionRows: MutableList<Int>): Boolean {
        if (header.right === header) return true // all constraints covered

        val col = chooseColumn() ?: return false
        if (col.size == 0) return false
        cover(col)

        var row = col.down
        while (row !== col) {
            solutionRows.add(row.rowId)
            var node = row.right
            while (node !== row) {
                cover(node.column!!)
                node = node.right
            }

            val found = search(solutionRows)

            // Always undo this row's column covers, whether we found a
            // solution or not - the matrix must be fully restored either way
            // so the solver instance can be reused for another solve() call.
            node = row.left
            while (node !== row) {
                uncover(node.column!!)
                node = node.left
            }

            if (found) {
                // Restore this column too before propagating success upward,
                // but keep this row in solutionRows - it's part of the answer.
                uncover(col)
                return true
            }

            solutionRows.removeAt(solutionRows.size - 1)
            row = row.down
        }

        uncover(col)
        return false
    }

    // ---- Public API -------------------------------------------------------

    /**
     * @param givens 9x9 grid, 0 = empty cell, 1-9 = given digit.
     * @return solved 9x9 grid, or null if no solution exists.
     */
    fun solve(givens: Array<IntArray>): Array<IntArray>? {
        require(givens.size == N && givens.all { it.size == N }) { "Grid must be 9x9" }

        // Pre-cover columns implied by the givens, so DLX only searches empty cells.
        val preCovered = ArrayList<ColumnNode>()
        for (r in 0 until N) {
            for (c in 0 until N) {
                val d = givens[r][c]
                if (d in 1..9) {
                    val digit = d - 1
                    // Cover the 4 columns this given already satisfies.
                    listOf(
                        cellConstraint(r, c),
                        rowConstraint(r, digit),
                        colConstraint(c, digit),
                        boxConstraint(r, c, digit)
                    ).forEach { idx ->
                        val col = columns[idx]

                        // A column already unlinked from the header row means some earlier
                        // given already covered it - i.e. two givens are fighting over the
                        // same constraint (duplicate digit in a row/col/box, or a
                        // literal duplicate given). That's an invalid puzzle, not a bug -
                        // bail out cleanly instead of double-covering (which would corrupt
                        // the matrix's internal linked-list state).
                        if (col.left.right !== col) {
                            // Undo whatever we've already pre-covered before returning,
                            // so this solver instance is still safe to reuse afterward.
                            for (i in preCovered.indices.reversed()) uncover(preCovered[i])
                            return null
                        }

                        cover(col)
                        preCovered.add(col)
                    }
                }
            }
        }

        val solutionRows = ArrayList<Int>()
        // Seed solution with the givens themselves.
        for (r in 0 until N) for (c in 0 until N) {
            val d = givens[r][c]
            if (d in 1..9) solutionRows.add((r * N + c) * N + (d - 1))
        }

        val found = search(solutionRows)

        // Restore matrix state (undo pre-covers) so the solver instance is reusable.
        for (i in preCovered.indices.reversed()) uncover(preCovered[i])

        if (!found) return null

        val result = Array(N) { IntArray(N) }
        for (encoded in solutionRows) {
            val digit = encoded % N
            val cell = encoded / N
            val r = cell / N
            val c = cell % N
            result[r][c] = digit + 1
        }
        return result
    }
}