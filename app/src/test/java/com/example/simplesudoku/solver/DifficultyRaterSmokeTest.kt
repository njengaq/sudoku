package com.example.simplesudoku.solver

import org.junit.Test
import org.junit.Assert.assertTrue

/**
 * Not a strict correctness test (the individual technique classes deserve
 * their own focused unit tests eventually) - this is a smoke test: generate
 * a handful of puzzles at every clue-target, rate them, and print a
 * breakdown. The main things to eyeball in the output:
 *   - For EASY/MEDIUM/HARD/PRO targets: does LEGEND basically never show
 *     up? It should still be unreachable from these four - digHoles
 *     rejects any removal that would force a guess, unchanged by today's
 *     LEGEND work. If LEGEND appears under one of these four targets,
 *     that's a real bug in the bifurcation-avoidance gate.
 *   - For the LEGEND target specifically: this is the one case where
 *     seeing LEGEND in the tally is the whole point, not a bug signal -
 *     see digHolesLegend. PRO showing up instead (best-effort fallback)
 *     is also expected sometimes and isn't itself a failure.
 *   - Does EASY dig down toward its clueFloor (36), not stop after a
 *     handful of clues?
 *   - Do MEDIUM/HARD/PRO show a real spread of labels, not just
 *     everything landing at EASY?
 *   - Does every puzzle report solved=true?
 */
class DifficultyRaterSmokeTest {

    @Test
    fun `rates a sample of puzzles across all generation targets`() {
        val generator = SudokuGenerator()
        val rater = DifficultyRater()

        SudokuGenerator.Difficulty.values().forEach { target ->
            println("\n=== Target: ${target.name} (targetLabel=${target.targetLabel}, clueFloor=${target.clueFloor}) ===")
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

                // For the four non-LEGEND targets, LEGEND appearing would mean gate 2
                // (rateWithoutBifurcation rejection) failed to do its job - a real
                // regression, not noise. This assertion is new; the LEGEND target is
                // deliberately excluded since LEGEND appearing there is the goal.
                if (target != SudokuGenerator.Difficulty.LEGEND) {
                    assertTrue(
                        "$target (attempt $i) was rated LEGEND (used a guess) - this should be " +
                                "impossible outside the LEGEND target; digHoles's no-guess gate may " +
                                "have a bug",
                        rated.label != DifficultyLabel.LEGEND
                    )
                }
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