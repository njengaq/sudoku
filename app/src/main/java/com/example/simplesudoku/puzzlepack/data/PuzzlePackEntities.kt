package com.example.simplesudoku.puzzlepack.data

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class PuzzleSourceType { BANK, GENERATED }
enum class PuzzleStatus { UNPLAYED, ACTIVE, COMPLETE }

@Entity(tableName = "bank_cursor")
data class BankCursor(
    @PrimaryKey val category: String,
    val currentIndex: Int,
    val seedOffset: Int,
    val totalCount: Int
)

@Entity(tableName = "generated_puzzle_buffer")
data class GeneratedPuzzleBuffer(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val category: String,
    val grid: String,
    val solution: String,
    val difficultyScore: Int,
    val createdAt: Long,
    val consumed: Boolean = false
)

@Entity(tableName = "puzzle_progress")
data class PuzzleProgress(
    @PrimaryKey val puzzleId: String,
    val servedAsCategory: String,
    val actualCategory: String,
    val source: PuzzleSourceType,
    val status: PuzzleStatus,
    val startedAt: Long?,
    val completedAt: Long?,
    val hintsUsed: Int = 0,
    val mistakeCount: Int = 0
)