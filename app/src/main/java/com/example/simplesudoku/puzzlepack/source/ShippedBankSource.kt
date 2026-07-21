package com.example.simplesudoku.puzzlepack.source

import android.content.Context
import com.example.simplesudoku.puzzlepack.data.BankCursor
import com.example.simplesudoku.puzzlepack.data.BankCursorDao
import com.example.simplesudoku.puzzlepack.data.PuzzleSourceType
import org.json.JSONArray
import kotlin.random.Random

private data class BankEntry(
    val id: String,
    val grid: String,
    val solution: String,
    val difficultyScore: Int
)

class ShippedBankSource(
    private val context: Context,
    private val bankCursorDao: BankCursorDao
) : PuzzleSource {

    // category -> loaded entries, cached in memory after first read
    private val cache = mutableMapOf<String, List<BankEntry>>()

    private fun assetFileName(category: String): String = when (category) {
        "MEDIUM" -> "banks/medium_bank.json"
        "PRO" -> "banks/pro_bank.json"
        else -> throw IllegalArgumentException("No shipped bank for category $category")
    }

    private fun loadBank(category: String): List<BankEntry> {
        cache[category]?.let { return it }

        val fileName = assetFileName(category)
        val text = context.assets.open(fileName).bufferedReader().use { it.readText() }
        val array = JSONArray(text)
        val entries = (0 until array.length()).map { i ->
            val obj = array.getJSONObject(i)
            BankEntry(
                id = obj.getString("id"),
                grid = obj.getString("grid"),
                solution = obj.optString("solution", ""),
                difficultyScore = obj.optInt("difficultyScore", 0)
            )
        }
        cache[category] = entries
        return entries
    }

    private suspend fun cursorFor(category: String, total: Int): BankCursor {
        return bankCursorDao.get(category) ?: BankCursor(
            category = category,
            currentIndex = 0,
            seedOffset = Random.nextInt(total.coerceAtLeast(1)),
            totalCount = total
        ).also { bankCursorDao.upsert(it) }
    }

    override suspend fun next(category: String): PuzzleRecord? {
        val entries = loadBank(category)
        if (entries.isEmpty()) return null

        val cursor = cursorFor(category, entries.size)

        // Exhaustion is now a real, permanent state for this install, not a
        // wraparound. Once every entry has been served once, this category's
        // bank is done until an app update ships a fresh one - the caller
        // (CascadingPuzzleSource) falls through to generation from here on.
        if (cursor.currentIndex >= cursor.totalCount) {
            return null
        }

        val actualIndex = (cursor.currentIndex + cursor.seedOffset) % entries.size
        val entry = entries[actualIndex]

        bankCursorDao.update(cursor.copy(currentIndex = cursor.currentIndex + 1))

        return PuzzleRecord(
            id = entry.id,
            servedAsCategory = category,
            actualCategory = category,
            grid = entry.grid,
            solution = entry.solution,
            difficultyScore = entry.difficultyScore,
            source = PuzzleSourceType.BANK
        )
    }

    override suspend fun markConsumed(puzzleId: String) {
        // Bank entries don't need per-item consumed tracking;
        // the cursor advancing in next() is sufficient.
    }
}