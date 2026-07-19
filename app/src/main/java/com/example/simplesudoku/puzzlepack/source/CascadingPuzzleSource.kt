package com.example.simplesudoku.puzzlepack.source

class CascadingPuzzleSource(
    private val primary: PuzzleSource,
    private val primaryCategory: String,
    private val fallback: PuzzleSource,
    private val fallbackCategory: String
) : PuzzleSource {

    override suspend fun next(category: String): PuzzleRecord? {
        primary.next(primaryCategory)?.let { return it }

        return fallback.next(fallbackCategory)?.copy(
            servedAsCategory = primaryCategory
        )
    }

    override suspend fun markConsumed(puzzleId: String) {
        if (puzzleId.startsWith("gen_")) {
            fallback.markConsumed(puzzleId)
        } else {
            primary.markConsumed(puzzleId)
        }
    }
}