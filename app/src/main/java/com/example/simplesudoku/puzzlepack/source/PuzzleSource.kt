package com.example.simplesudoku.puzzlepack.source

import com.example.simplesudoku.puzzlepack.data.PuzzleSourceType

data class PuzzleRecord(
    val id: String,
    val servedAsCategory: String,
    val actualCategory: String,
    val grid: String,
    val solution: String,
    val difficultyScore: Int,
    val source: PuzzleSourceType
)

interface PuzzleSource {
    suspend fun next(category: String): PuzzleRecord?
    suspend fun markConsumed(puzzleId: String)
}