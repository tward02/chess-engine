package com.tward.engine.tournament

import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.sqrt

/**
 * Sequential probability ratio test over **game pairs** (the same opening played once with each
 * colour), the way engine testing frameworks gate patches. A pair's score for the candidate is one
 * of 0, ½, 1, 1½, 2 — the pentanomial — which is far less noisy than counting single games, because
 * the opening's colour bias cancels within each pair.
 *
 * H0: the candidate is [elo0] Elo stronger; H1: it is [elo1] Elo stronger (logistic Elo). After
 * every pair, [llr] (a normal-approximation generalised log-likelihood ratio) is compared with the
 * bounds: at or above [upperBound] accept H1 ("passed"), at or below [lowerBound] accept H0
 * ("failed"). [alpha]/[beta] are the false-positive/false-negative rates. Not thread-safe.
 */
class PentanomialSprt(
    val elo0: Double = 0.0,
    val elo1: Double = 10.0,
    val alpha: Double = 0.05,
    val beta: Double = 0.05
) {
    init {
        require(elo1 > elo0) { "elo1 must exceed elo0" }
    }

    enum class Status { CONTINUE, PASSED, FAILED }

    data class EloEstimate(val elo: Double, val low: Double, val high: Double)

    /** counts[k] = pairs where the candidate scored k half-points (0..4). */
    private val counts = LongArray(5)

    val lowerBound: Double = ln(beta / (1 - alpha))
    val upperBound: Double = ln((1 - beta) / alpha)

    val pairs: Long get() = counts.sum()
    val pentanomial: List<Long> get() = counts.toList()

    /** Records one pair; [candidatePoints] is the candidate's total over both games (0.0..2.0). */
    fun record(candidatePoints: Double) {
        val halves = Math.round(candidatePoints * 2).toInt()
        require(halves in 0..4) { "a pair scores 0..2 points, got $candidatePoints" }
        counts[halves]++
    }

    fun llr(): Double {
        val n = pairs.toDouble()
        if (n == 0.0) return 0.0
        val (mean, variance) = meanAndVariance()
        if (variance <= 0.0) return 0.0   // e.g. all pairs identical: no information about spread yet
        val s0 = expectedScore(elo0)
        val s1 = expectedScore(elo1)
        return n * (s1 - s0) * (2 * mean - s0 - s1) / (2 * variance)
    }

    fun status(): Status {
        val llr = llr()
        return when {
            llr >= upperBound -> Status.PASSED
            llr <= lowerBound -> Status.FAILED
            else -> Status.CONTINUE
        }
    }

    /** Elo of the candidate with a 95% confidence interval, from the per-game score. */
    fun eloEstimate(): EloEstimate {
        val n = pairs.toDouble()
        if (n == 0.0) return EloEstimate(0.0, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY)
        val (mean, variance) = meanAndVariance()
        val margin = 1.96 * sqrt(variance / n)
        return EloEstimate(elo(mean), elo(mean - margin), elo(mean + margin))
    }

    // Per-game score of each pair (half its points), averaged over pairs.
    private fun meanAndVariance(): Pair<Double, Double> {
        val n = pairs.toDouble()
        var mean = 0.0
        for (k in 0..4) mean += counts[k] * (k / 4.0)
        mean /= n
        var variance = 0.0
        for (k in 0..4) {
            val d = k / 4.0 - mean
            variance += counts[k] * d * d
        }
        return mean to variance / n
    }

    companion object {
        fun expectedScore(elo: Double): Double = 1.0 / (1.0 + Math.pow(10.0, -elo / 400.0))

        fun elo(score: Double): Double = when {
            score <= 0.0 -> Double.NEGATIVE_INFINITY
            score >= 1.0 -> Double.POSITIVE_INFINITY
            else -> -400.0 * log10(1.0 / score - 1.0)
        }
    }
}
