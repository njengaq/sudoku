package com.example.simplesudoku.puzzlepack.source

import com.example.simplesudoku.puzzlepack.data.GeneratedPuzzleBuffer
import com.example.simplesudoku.puzzlepack.data.GeneratedPuzzleBufferDao
import com.example.simplesudoku.puzzlepack.data.PuzzleSourceType
import com.example.simplesudoku.solver.SudokuGenerator
import java.lang.System

class LiveGeneratedSource(
    private val bufferDao: GeneratedPuzzleBufferDao,
    private val generator: SudokuGenerator = SudokuGenerator()
) : PuzzleSource {

    private fun toDifficulty(category: String): SudokuGenerator.Difficulty? = when (category) {
        "EASY" -> SudokuGenerator.Difficulty.EASY
        "MEDIUM" -> SudokuGenerator.Difficulty.MEDIUM
        "HARD" -> SudokuGenerator.Difficulty.HARD
        "PRO" -> SudokuGenerator.Difficulty.PRO
        "LEGEND" -> SudokuGenerator.Difficulty.LEGEND
        else -> null
    }

    private fun flatten(grid: Array<IntArray>): String =
        grid.joinToString("") { row -> row.joinToString("") { it.toString() } }

    private fun generateAndStore(category: String) {
        val difficulty = toDifficulty(category) ?: return
        val result = generator.generate(difficulty)

        val bufferEntry = GeneratedPuzzleBuffer(
            category = category,
            grid = flatten(result.puzzle),
            solution = flatten(result.solution),
            difficultyScore = result.ratingScore,
            createdAt = System.currentTimeMillis(),
            consumed = false
        )
        // insert is suspend on the DAO, but generate() itself is blocking/CPU-bound;
        // caller (PuzzlePackManager) is expected to invoke this off the main thread.
        kotlinx.coroutines.runBlocking { bufferDao.insert(bufferEntry) }
    }

    override suspend fun next(category: String): PuzzleRecord? {
        var buffered = bufferDao.nextUnconsumed(category)

        if (buffered == null) {
            generateAndStore(category)
            buffered = bufferDao.nextUnconsumed(category)
        }

        return buffered?.let {
            PuzzleRecord(
                id = "gen_${it.id}",
                servedAsCategory = category,
                actualCategory = category,
                grid = it.grid,
                solution = it.solution,
                difficultyScore = it.difficultyScore,
                source = PuzzleSourceType.GENERATED
            )
        }
    }

    override suspend fun markConsumed(puzzleId: String) {
        val rawId = puzzleId.removePrefix("gen_").toLongOrNull() ?: return
        bufferDao.markConsumed(rawId)
    }
}