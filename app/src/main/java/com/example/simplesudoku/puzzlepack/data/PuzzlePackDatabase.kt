package com.example.simplesudoku.puzzlepack.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters

class PuzzlePackConverters {
    @TypeConverter
    fun fromSourceType(value: PuzzleSourceType): String = value.name

    @TypeConverter
    fun toSourceType(value: String): PuzzleSourceType = PuzzleSourceType.valueOf(value)

    @TypeConverter
    fun fromStatus(value: PuzzleStatus): String = value.name

    @TypeConverter
    fun toStatus(value: String): PuzzleStatus = PuzzleStatus.valueOf(value)
}

@Database(
    entities = [BankCursor::class, GeneratedPuzzleBuffer::class, PuzzleProgress::class],
    version = 1,
    exportSchema = false
)
@TypeConverters(PuzzlePackConverters::class)
abstract class PuzzlePackDatabase : RoomDatabase() {
    abstract fun bankCursorDao(): BankCursorDao
    abstract fun generatedPuzzleBufferDao(): GeneratedPuzzleBufferDao
    abstract fun puzzleProgressDao(): PuzzleProgressDao

    companion object {
        @Volatile private var INSTANCE: PuzzlePackDatabase? = null

        fun getInstance(context: Context): PuzzlePackDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    PuzzlePackDatabase::class.java,
                    "puzzlepack.db"
                ).build().also { INSTANCE = it }
            }
    }
}