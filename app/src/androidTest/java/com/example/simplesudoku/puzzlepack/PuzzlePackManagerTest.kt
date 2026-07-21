package com.example.simplesudoku.puzzlepack

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PuzzlePackManagerTest {

    @Test
    fun nextPuzzle_easy_returnsValidPuzzle() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val manager = PuzzlePackManager(context)

        val puzzle = manager.nextPuzzle("EASY")

        assertNotNull(puzzle)
        assertEquals(81, puzzle.grid.length)
        assertEquals(81, puzzle.solution.length)
        assertEquals("EASY", puzzle.servedAsCategory)
        assertTrue("Difficulty score should be non-negative", puzzle.difficultyScore >= 0)
    }

    @Test
    fun nextPuzzle_medium_fallsBackGracefullyOrReadsBank() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val manager = PuzzlePackManager(context)

        val puzzle = manager.nextPuzzle("MEDIUM")

        assertNotNull(puzzle)
        assertEquals(81, puzzle.grid.length)
        // servedAsCategory should read MEDIUM to the caller even if
        // actualCategory silently fell back to HARD under the hood
        assertEquals("MEDIUM", puzzle.servedAsCategory)
    }

    @Test
    fun nextPuzzle_pro_readsBank() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val manager = PuzzlePackManager(context)

        val puzzle = manager.nextPuzzle("PRO")

        assertNotNull(puzzle)
        assertEquals(81, puzzle.grid.length)
        assertEquals("PRO", puzzle.servedAsCategory)
    }
}