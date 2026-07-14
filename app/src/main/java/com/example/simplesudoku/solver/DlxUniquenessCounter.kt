package com.example.simplesudoku.solver

/**
 * Counts solutions to a Sudoku puzzle, stopping early once it knows the
 * answer is "more than one". Used by the puzzle generator to confirm a
 * candidate puzzle has exactly one valid solution before shipping it.
 *
 * Reuses the same Dancing Links matrix structure as DlxSudokuSolver, but
 * the search here never stops at the first solution - it keeps going
 * (fully restoring state after each one, same as DlxSudokuSolver's search)
 * until it either exhausts all possibilities or hits the given cap.
 */
class DlxUniquenessCounter {

    private open class Node {
        var left: Node = this
        var right: Node = this
        var up: Node = this
        var down: Node = this
        var column: ColumnNode? = null
        var rowId: Int = -1
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
        private const val CELL_CONSTRAINTS = N * N
        private const val ROW_CONSTRAINTS = N * N
        private const val COL_CONSTRAINTS = N * N
        private const val BOX_CONSTRAINTS = N * N
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

        for (r in 0 until N) {
            for (c in 0 until N) {
                for (d in 0 until N) {
                    val rowId = (r * N + c) * N + d
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

            node.up = col.up
            node.down = col
            col.up.down = node
            col.up = node
            col.size++

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
            if (best.size == 0) break
            node = node.right
        }
        return best
    }

    /**
     * Counts solutions, stopping as soon as [cap] is reached (default 2 -
     * all the generator needs to know is "unique" vs "not unique").
     * Fully restores matrix state on every path so [count] can be called
     * repeatedly on the same instance.
     */
    private fun count(cap: Int): Int {
        if (header.right === header) return 1 // one full solution found

        val col = chooseColumn() ?: return 0
        if (col.size == 0) return 0
        cover(col)

        var found = 0
        var row = col.down
        while (row !== col) {
            var node = row.right
            while (node !== row) {
                cover(node.column!!)
                node = node.right
            }

            found += count(cap - found)

            node = row.left
            while (node !== row) {
                uncover(node.column!!)
                node = node.left
            }

            if (found >= cap) break // enough - stop exploring this level too
            row = row.down
        }

        uncover(col)
        return found
    }

    /**
     * @param givens 9x9 grid, 0 = empty cell, 1-9 = given digit.
     * @return true if the puzzle has exactly one solution.
     */
    fun hasUniqueSolution(givens: Array<IntArray>): Boolean = countSolutions(givens, cap = 2) == 1

    /**
     * @param givens 9x9 grid, 0 = empty cell, 1-9 = given digit.
     * @param cap stop counting once this many solutions are found (default 2).
     * @return number of solutions found, up to [cap]. A return value equal
     *         to [cap] means "at least [cap]", not necessarily exactly that many.
     */
    fun countSolutions(givens: Array<IntArray>, cap: Int = 2): Int {
        require(givens.size == N && givens.all { it.size == N }) { "Grid must be 9x9" }
        require(cap >= 1) { "cap must be at least 1" }

        val preCovered = ArrayList<ColumnNode>()
        for (r in 0 until N) {
            for (c in 0 until N) {
                val d = givens[r][c]
                if (d in 1..9) {
                    val digit = d - 1
                    listOf(
                        cellConstraint(r, c),
                        rowConstraint(r, digit),
                        colConstraint(c, digit),
                        boxConstraint(r, c, digit)
                    ).forEach { idx ->
                        val col = columns[idx]
                        cover(col)
                        preCovered.add(col)
                    }
                }
            }
        }

        val result = count(cap)

        for (i in preCovered.indices.reversed()) uncover(preCovered[i])

        return result
    }
}