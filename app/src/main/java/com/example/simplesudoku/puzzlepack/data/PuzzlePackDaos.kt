package com.example.simplesudoku.puzzlepack.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update

@Dao
interface BankCursorDao {
    @Query("SELECT * FROM bank_cursor WHERE category = :category")
    suspend fun get(category: String): BankCursor?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(cursor: BankCursor)

    @Update
    suspend fun update(cursor: BankCursor)
}

@Dao
interface GeneratedPuzzleBufferDao {
    @Query("SELECT * FROM generated_puzzle_buffer WHERE category = :category AND consumed = 0 ORDER BY createdAt ASC LIMIT 1")
    suspend fun nextUnconsumed(category: String): GeneratedPuzzleBuffer?

    @Query("SELECT COUNT(*) FROM generated_puzzle_buffer WHERE category = :category AND consumed = 0")
    suspend fun unconsumedCount(category: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(puzzle: GeneratedPuzzleBuffer): Long

    @Query("UPDATE generated_puzzle_buffer SET consumed = 1 WHERE id = :id")
    suspend fun markConsumed(id: Long)
}

@Dao
interface PuzzleProgressDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(progress: PuzzleProgress)

    @Update
    suspend fun update(progress: PuzzleProgress)

    @Query("SELECT * FROM puzzle_progress WHERE puzzleId = :id")
    suspend fun get(id: String): PuzzleProgress?

    @Query("SELECT COUNT(*) FROM puzzle_progress WHERE servedAsCategory = :category AND status = 'COMPLETE'")
    suspend fun completedCount(category: String): Int
}