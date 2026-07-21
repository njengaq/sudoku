package com.example.simplesudoku.puzzlepack

import android.content.Context
import com.example.simplesudoku.puzzlepack.data.PuzzlePackDatabase
import com.example.simplesudoku.puzzlepack.data.PuzzleProgress
import com.example.simplesudoku.puzzlepack.data.PuzzleStatus
import com.example.simplesudoku.puzzlepack.source.CascadingPuzzleSource
import com.example.simplesudoku.puzzlepack.source.LiveGeneratedSource
import com.example.simplesudoku.puzzlepack.source.PuzzleRecord
import com.example.simplesudoku.puzzlepack.source.PuzzleSource
import com.example.simplesudoku.puzzlepack.source.ShippedBankSource

class PuzzlePackManager(context: Context) {

    private val db = PuzzlePackDatabase.getInstance(context)
    private val bankCursorDao = db.bankCursorDao()
    private val bufferDao = db.generatedPuzzleBufferDao()
    private val progressDao = db.puzzleProgressDao()

    private val bankSource: PuzzleSource = ShippedBankSource(context, bankCursorDao)
    private val generatedSource: PuzzleSource = LiveGeneratedSource(bufferDao)

    // EASY / HARD: pure live generation, no fallback needed (generator handles these directly)
    // MEDIUM: shipped bank, falls back to HARD generation if the 5000-bank runs dry
    // PRO: shipped bank, falls back to PRO generation again for now
    //      (TODO: should fall back to LEGEND once a Legend-specific digging mode exists -
    //      see LiveGeneratedSource's toDifficulty() note)
    private val mediumSource = CascadingPuzzleSource(
        primary = bankSource, primaryCategory = "MEDIUM",
        fallback = generatedSource, fallbackCategory = "HARD"
    )
    private val proSource = CascadingPuzzleSource(
        primary = bankSource, primaryCategory = "PRO",
        fallback = generatedSource, fallbackCategory = "PRO"
    )

    private fun sourceFor(category: String): PuzzleSource = when (category) {
        "MEDIUM" -> mediumSource
        "PRO" -> proSource
        "EASY", "HARD" -> generatedSource
        else -> throw IllegalArgumentException("Unknown category: $category")
    }

    /**
     * Fetches the next puzzle for [category], records it in PuzzleProgress
     * as UNPLAYED, and returns it ready for the board renderer.
     *
     * Note: for EASY/HARD/PRO-fallback, this may trigger live generation
     * synchronously (see LiveGeneratedSource's runBlocking note) - call
     * this from a background dispatcher, not the main thread.
     */
    suspend fun nextPuzzle(category: String): PuzzleRecord {
        val source = sourceFor(category)
        val record = source.next(category)
            ?: throw IllegalStateException("No puzzle available for $category")

        source.markConsumed(record.id)

        progressDao.insert(
            PuzzleProgress(
                puzzleId = record.id,
                servedAsCategory = record.servedAsCategory,
                actualCategory = record.actualCategory,
                source = record.source,
                status = PuzzleStatus.UNPLAYED,
                startedAt = null,
                completedAt = null
            )
        )

        return record
    }
}