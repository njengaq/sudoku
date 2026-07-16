package com.example.simplesudoku.solver.techniques

import com.example.simplesudoku.solver.CandidateGrid

/** True if two cells share a row, column, or box - i.e. they're peers. */
internal fun seesEachOther(grid: CandidateGrid, a: Pair<Int, Int>, b: Pair<Int, Int>): Boolean =
    a.first == b.first || a.second == b.second || grid.boxOf(a.first, a.second) == grid.boxOf(b.first, b.second)

/**
 * Splits an adjacency map into its connected components. Kept separate from
 * coloring on purpose: chains for the same digit (or digit-pair) can exist
 * as several unrelated groups across the grid, and mixing their colors
 * together would produce false eliminations.
 */
internal fun connectedComponents(adjacency: Map<Pair<Int, Int>, Set<Pair<Int, Int>>>): List<Set<Pair<Int, Int>>> {
    val visited = HashSet<Pair<Int, Int>>()
    val components = mutableListOf<Set<Pair<Int, Int>>>()
    for (node in adjacency.keys) {
        if (node in visited) continue
        val comp = mutableSetOf<Pair<Int, Int>>()
        val queue = ArrayDeque<Pair<Int, Int>>()
        queue.add(node); visited.add(node)
        while (queue.isNotEmpty()) {
            val cur = queue.removeFirst()
            comp.add(cur)
            for (nb in adjacency[cur].orEmpty()) {
                if (nb !in visited) { visited.add(nb); queue.add(nb) }
            }
        }
        components.add(comp)
    }
    return components
}

/** 2-colors ONE connected component via BFS from an arbitrary start node. */
internal fun colorComponent(
    adjacency: Map<Pair<Int, Int>, Set<Pair<Int, Int>>>,
    component: Set<Pair<Int, Int>>
): Map<Pair<Int, Int>, Int> {
    val colorOf = HashMap<Pair<Int, Int>, Int>()
    val start = component.first()
    colorOf[start] = 0
    val visited = mutableSetOf(start)
    val queue = ArrayDeque<Pair<Int, Int>>()
    queue.add(start)
    while (queue.isNotEmpty()) {
        val cur = queue.removeFirst()
        for (nb in adjacency[cur].orEmpty()) {
            if (nb !in visited) {
                visited.add(nb)
                colorOf[nb] = 1 - colorOf.getValue(cur)
                queue.add(nb)
            }
        }
    }
    return colorOf
}