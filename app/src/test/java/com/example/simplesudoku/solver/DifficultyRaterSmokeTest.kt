package com.example.simplesudoku.solver

import org.junit.Test
import org.junit.Assert.assertTrue

/**
 * Not a strict correctness test (the individual technique classes deserve
 * their own focused unit tests eventually) - this is a smoke test: generate
 * a handful of puzzles at every clue-target, rate them, and print a
 * breakdown. The main things to eyeball in the output:
 *   - Do EASY-target puzzles mostly land as EASY/MEDIUM, and PRO-target
 *     ones mostly as PRO/LEGEND? (They won't match 1:1 - clue count and
 *     technique difficulty are related but not identical, by design.)
 *   - Is Bifurcation ("Legend") rare, or is it firing constantly? Constant
 *     firing likely means an earlier technique has a bug and isn't
 *     catching deductions it should.
 *   - Does every puzzle report solved=true? If not, something is wrong -
 *     Bifurcation is designed to always finish the job.
 */
class DifficultyRaterSmokeTest {

    @Test
    fun `rates a sample of puzzles across all four generation targets`() {
        val generator = SudokuGenerator()
        val rater = DifficultyRater()

        SudokuGenerator.Difficulty.values().forEach { target ->
            println("\n=== Target: ${target.name} (clue range ${target.minClues}-${target.maxClues}) ===")
            val labelTally = mutableMapOf<DifficultyLabel, Int>()

            repeat(SAMPLES_PER_TARGET) { i ->
                val generated = generator.generate(target)

                // generate() already rates internally (see SudokuGenerator.buildResult), but
                // we rate again here explicitly so this test reflects what DifficultyRater
                // itself computes, independent of that internal wiring.
                val rated = rater.rate(generated.puzzle)

                labelTally[rated.label] = (labelTally[rated.label] ?: 0) + 1

                println(
                    "  #$i clues=${generated.clueCount} -> label=${rated.label} " +
                            "floor=${rated.floorTier} score=${rated.rawScore} " +
                            "bifurcation=${rated.usedBifurcation} tiers=${formatTierCounts(rated.tierUsageCounts)}"
                )

                assertTrue(
                    "Puzzle generated for $target (attempt $i) did not fully solve - " +
                            "check whether Bifurcation is actually firing when needed",
                    rated.solved
                )
            }

            println("  --> label distribution for $target target: $labelTally")
        }
    }

    private fun formatTierCounts(counts: Map<TechniqueTier, Int>): String =
        counts.entries.joinToString(", ") { (tier, count) -> "${tier.name}=$count" }

    companion object {
        private const val SAMPLES_PER_TARGET = 5
    }
}