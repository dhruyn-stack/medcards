package com.medcards

import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/**
 * FSRS-5 scheduler.
 * Reference: https://github.com/open-spaced-repetition/fsrs4anki/wiki
 */
class Fsrs(
    weights: List<Double> = DEFAULT_WEIGHTS,
    desiredRetention: Double = 0.9
) {

    companion object {
        val DEFAULT_WEIGHTS = listOf(
            0.40255, 1.18385, 3.173, 15.69105,
            7.1949, 0.5345, 1.4604, 0.0046,
            1.54575, 0.1192, 1.01925, 1.9395,
            0.11, 0.29605, 2.2698, 0.2315,
            2.9898, 0.51655, 0.6621
        )

        const val DECAY = -0.5
        val FACTOR = 0.9.pow(1.0 / DECAY) - 1.0   // == 19/81

        const val DAY_MS = 86_400_000L

        fun humanInterval(millis: Long): String {
            val seconds = millis / 1000.0
            if (seconds < 3600) return "${max((seconds / 60).toInt(), 1)}m"
            if (seconds < 86400) return "${(seconds / 3600).toInt()}h"
            val days = seconds / 86400.0
            if (days < 30) return "${days.roundToInt()}d"
            if (days < 365) return String.format("%.1fmo", days / 30.0)
            return String.format("%.1fy", days / 365.0)
        }
    }

    private val w: List<Double> = if (weights.size == 19) weights else DEFAULT_WEIGHTS
    private val retention: Double = desiredRetention.coerceIn(0.70, 0.98)

    // ------------------------------------------------------------ formulas

    /** Probability of recall after [elapsedDays] with memory [stability]. */
    fun retrievability(elapsedDays: Double, stability: Double): Double {
        if (stability <= 0) return 0.0
        return (1.0 + FACTOR * elapsedDays / stability).pow(DECAY)
    }

    /** Interval in days that yields the desired retention. */
    fun intervalDays(stability: Double): Double {
        val raw = (stability / FACTOR) * (retention.pow(1.0 / DECAY) - 1.0)
        return raw.roundToLong().toDouble().coerceIn(1.0, 36500.0)
    }

    private fun initialStability(g: Rating): Double = max(w[g.value - 1], 0.1)

    private fun initialDifficulty(g: Rating): Double =
        clampD(w[4] - exp(w[5] * (g.value - 1)) + 1.0)

    private fun clampD(d: Double): Double = min(max(d, 1.0), 10.0)

    private fun nextDifficulty(d: Double, g: Rating): Double {
        val delta = -w[6] * (g.value - 3)
        val damped = d + delta * ((10.0 - d) / 9.0)              // linear damping (FSRS-5)
        val reverted = w[7] * initialDifficulty(Rating.EASY) + (1.0 - w[7]) * damped
        return clampD(reverted)
    }

    private fun stabilityAfterRecall(d: Double, s: Double, r: Double, g: Rating): Double {
        val hardPenalty = if (g == Rating.HARD) w[15] else 1.0
        val easyBonus = if (g == Rating.EASY) w[16] else 1.0
        val inc = exp(w[8]) *
                (11.0 - d) *
                s.pow(-w[9]) *
                (exp(w[10] * (1.0 - r)) - 1.0) *
                hardPenalty *
                easyBonus
        return max(s * (1.0 + inc), 0.01)
    }

    private fun stabilityAfterForget(d: Double, s: Double, r: Double): Double {
        val sf = w[11] *
                d.pow(-w[12]) *
                ((s + 1.0).pow(w[13]) - 1.0) *
                exp(w[14] * (1.0 - r))
        return max(min(sf, s), 0.01)
    }

    private fun shortTermStability(s: Double, g: Rating): Double =
        max(s * exp(w[17] * (g.value - 3.0 + w[18])), 0.01)

    // ------------------------------------------------------------ api

    data class Outcome(val card: Card, val scheduledDays: Double, val elapsedDays: Double)

    /** Applies a rating and returns the updated card. */
    fun review(card: Card, rating: Rating, now: Long = System.currentTimeMillis()): Outcome {
        val elapsed = card.lastReview?.let { max((now - it).toDouble() / DAY_MS, 0.0) } ?: 0.0

        var difficulty: Double
        var stability: Double

        if (card.state == CardState.NEW) {
            difficulty = initialDifficulty(rating)
            stability = initialStability(rating)
        } else {
            val s0 = max(card.stability, 0.01)
            val r = retrievability(elapsed, s0)
            difficulty = nextDifficulty(card.difficulty, rating)
            stability = when {
                elapsed < 1.0 -> shortTermStability(s0, rating)
                rating == Rating.AGAIN -> stabilityAfterForget(difficulty, s0, r)
                else -> stabilityAfterRecall(difficulty, s0, r, rating)
            }
        }

        var newState = card.state
        var lapses = card.lapses
        val due: Long
        var scheduledDays = 0.0

        when (rating) {
            Rating.AGAIN -> {
                if (card.state == CardState.REVIEW) lapses += 1
                newState = CardState.LEARNING
                due = now + 5 * 60_000L                     // 5 minutes
            }
            Rating.HARD -> {
                if (card.state == CardState.NEW || card.state == CardState.LEARNING) {
                    newState = CardState.LEARNING
                    due = now + 10 * 60_000L                // 10 minutes
                } else {
                    val d = intervalDays(stability)
                    scheduledDays = d
                    due = now + (d * DAY_MS).toLong()
                }
            }
            Rating.GOOD, Rating.EASY -> {
                newState = CardState.REVIEW
                val d = intervalDays(stability)
                scheduledDays = d
                due = now + (d * DAY_MS).toLong()
            }
        }

        val updated = card.copy(
            state = newState,
            stability = stability,
            difficulty = difficulty,
            due = due,
            lastReview = now,
            reps = card.reps + 1,
            lapses = lapses
        )
        return Outcome(updated, scheduledDays, elapsed)
    }

    /** Next-interval preview for each button, shown on the answer screen. */
    fun previews(card: Card, now: Long = System.currentTimeMillis()): Map<Rating, String> =
        Rating.values().associateWith { r ->
            humanInterval(review(card, r, now).card.due - now)
        }
}
