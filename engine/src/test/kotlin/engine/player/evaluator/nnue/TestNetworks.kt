package engine.player.evaluator.nnue

import com.tward.engine.player.evaluator.nnue.NnueFeatures
import com.tward.engine.player.evaluator.nnue.NnueNetwork

/** Hand-built networks with known behaviour, for deterministic NNUE tests. */
object TestNetworks {

    /**
     * A two-unit network that computes plain material balance: unit 0 accumulates friendly material,
     * unit 1 enemy material (scaled by 1/10000 so the clipped ReLU never clips), and the output layer
     * rescales their difference back to centipawns. Lets search tests run against a deterministic
     * "evaluator" whose correct answers are obvious.
     */
    fun material(): NnueNetwork {
        val hidden = 2
        val ftWeights = FloatArray(NnueFeatures.COUNT * hidden)
        for (kind in 0 until 12) {
            val centipawns = when (kind % 6) {
                0 -> 100; 1 -> 320; 2 -> 330; 3 -> 500; 4 -> 900; else -> 0
            }
            val unit = if (kind < 6) 0 else 1   // friendly kinds feed unit 0, enemy kinds unit 1
            for (square in 0 until 64) {
                ftWeights[(kind * 64 + square) * hidden + unit] = centipawns / SCALE
            }
        }
        // The output layer undoes the 1/SCALE and emits raw units of OUTPUT_SCALE centipawns,
        // so the evaluator's final scaling lands back on exact centipawn material.
        val outUnit = SCALE / NnueNetwork.OUTPUT_SCALE
        return NnueNetwork(
            hiddenSize = hidden,
            ftWeights = ftWeights,
            ftBias = FloatArray(hidden),
            outWeights = floatArrayOf(outUnit, -outUnit, 0f, 0f),   // own material − enemy material
            outBias = 0f
        )
    }

    private const val SCALE = 10_000f
}
