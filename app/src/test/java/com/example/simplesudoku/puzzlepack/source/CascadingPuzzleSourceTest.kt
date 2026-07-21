package com.example.simplesudoku.puzzlepack.source

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlinx.coroutines.test.runTest

/**
 * Proves CascadingPuzzleSource actually falls through once a primary source
 * is exhausted (returns null) - this is the real bug the MEDIUM->HARD and
 * PRO->LEGEND fallback chains depend on. Deliberately does NOT use the real
 * ShippedBankSource/LiveGeneratedSource here - those need Android Context /
 * Room and belong in their own tests (see ShippedBankSourceTest, pending on
 * whether Mockito or Robolectric is available in this project). This file
 * only tests CascadingPuzzleSource's own logic, using simple in-memory fakes
 * that implement the PuzzleSource contract directly.
 */
class CascadingPuzzleSourceTest {

    /**
     * A fake PuzzleSource backed by a plain in-memory list, standing in for
     * "a shipped bank" or "a generator" without touching Android/Room at all.
     * Mimics the real exhaustion behavior we just added to ShippedBankSource:
     * once every entry has been served once, next() returns null for good -
     * no wraparound.
     */
    private class FakeExhaustibleSource(
        private val entries: List<PuzzleRecord>
    ) : PuzzleSource {
        var served = 0
            private set
        val consumedIds = mutableListOf<String>()

        override suspend fun next(category: String): PuzzleRecord? {
            if (served >= entries.size) return null
            val record = entries[served]
            served += 1
            return record
        }

        override suspend fun markConsumed(puzzleId: String) {
            consumedIds.add(puzzleId)
        }
    }

    /** A fake source that never runs out - stands in for live generation. */
    private class FakeInfiniteSource(private val categoryLabel: String) : PuzzleSource {
        var callCount = 0
            private set

        override suspend fun next(category: String): PuzzleRecord? {
            callCount += 1
            return PuzzleRecord(
                id = "gen_$callCount",
                servedAsCategory = category,
                actualCategory = category,
                grid = "0".repeat(81),
                solution = "1".repeat(81),
                difficultyScore = 0,
                source = PuzzleSourceType.GENERATED
            )
        }

        override suspend fun markConsumed(puzzleId: String) {
            // no-op - generated puzzles don't need consumed-tracking in this fake
        }
    }

    private fun bankRecord(id: String, category: String) = PuzzleRecord(
        id = id,
        servedAsCategory = category,
        actualCategory = category,
        grid = "5".repeat(81),
        solution = "6".repeat(81),
        difficultyScore = 50,
        source = PuzzleSourceType.BANK
    )

    @Test
    fun `serves from primary until exhausted, then falls through to fallback`() = runTest {
        val primary = FakeExhaustibleSource(
            listOf(bankRecord("bank_1", "MEDIUM"), bankRecord("bank_2", "MEDIUM"))
        )
        val fallback = FakeInfiniteSource("HARD")
        val cascade = CascadingPuzzleSource(
            primary = primary, primaryCategory = "MEDIUM",
            fallback = fallback, fallbackCategory = "HARD"
        )

        val first = cascade.next("MEDIUM")
        val second = cascade.next("MEDIUM")
        assertEquals("bank_1", first?.id)
        assertEquals("bank_2", second?.id)
        assertEquals(0, fallback.callCount) // fallback untouched while primary still has entries

        // Bank is now exhausted (2 entries, both served) - third call must fall through.
        val third = cascade.next("MEDIUM")
        assertTrue("Expected a fallback-generated puzzle once the bank is exhausted", third != null)
        assertEquals(1, fallback.callCount)

        // Fallback puzzles are re-labeled as the primary's category for the player,
        // even though they were actually generated at the fallback's difficulty -
        // this is CascadingPuzzleSource's servedAsCategory override.
        assertEquals("MEDIUM", third?.servedAsCategory)
        assertEquals(PuzzleSourceType.GENERATED, third?.source)
    }

    @Test
    fun `fallback continues to serve indefinitely once primary is exhausted`() = runTest {
        val primary = FakeExhaustibleSource(listOf(bankRecord("only_one", "PRO")))
        val fallback = FakeInfiniteSource("LEGEND")
        val cascade = CascadingPuzzleSource(
            primary = primary, primaryCategory = "PRO",
            fallback = fallback, fallbackCategory = "LEGEND"
        )

        cascade.next("PRO") // consumes the bank's one entry
        repeat(5) { cascade.next("PRO") }

        assertEquals(5, fallback.callCount)
    }

    @Test
    fun `an empty primary bank falls straight through on the very first call`() = runTest {
        val primary = FakeExhaustibleSource(emptyList())
        val fallback = FakeInfiniteSource("HARD")
        val cascade = CascadingPuzzleSource(
            primary = primary, primaryCategory = "MEDIUM",
            fallback = fallback, fallbackCategory = "HARD"
        )

        val result = cascade.next("MEDIUM")
        assertTrue(result != null)
        assertEquals(1, fallback.callCount)
    }

    @Test
    fun `markConsumed routes to fallback for gen-prefixed ids, primary otherwise`() = runTest {
        val primary = FakeExhaustibleSource(listOf(bankRecord("bank_1", "MEDIUM")))
        val fallback = FakeInfiniteSource("HARD")
        val cascade = CascadingPuzzleSource(
            primary = primary, primaryCategory = "MEDIUM",
            fallback = fallback, fallbackCategory = "HARD"
        )

        cascade.markConsumed("bank_1")
        cascade.markConsumed("gen_42")

        assertEquals(listOf("bank_1"), primary.consumedIds)
        // FakeInfiniteSource.markConsumed is a no-op, so we can't assert on a list here -
        // this just confirms no exception is thrown routing a gen_ id to the fallback.
    }

    /**
     * Confirms the underlying assumption CascadingPuzzleSource relies on: a
     * fully-served FakeExhaustibleSource returns null, not a wrapped-around
     * repeat. This isn't testing CascadingPuzzleSource itself - it's a
     * sanity check on the fake's own exhaustion behavior, which mirrors
     * ShippedBankSource's real fix. If ShippedBankSourceTest is written
     * later against the real class, this same expectation should hold there.
     */
    @Test
    fun `exhausted fake source returns null rather than wrapping around`() = runTest {
        val source = FakeExhaustibleSource(listOf(bankRecord("a", "MEDIUM")))
        source.next("MEDIUM")
        assertNull(source.next("MEDIUM"))
        assertNull(source.next("MEDIUM")) // still null on repeated calls, not a one-time fluke
    }
}