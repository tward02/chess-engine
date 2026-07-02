package com.tward.engine.nnue

import com.tward.engine.board.Board
import com.tward.engine.board.Colour
import com.tward.engine.player.evaluator.nnue.NnueFeatures
import com.tward.engine.player.evaluator.nnue.NnueNetwork
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.Random
import kotlin.math.exp
import kotlin.math.sqrt

/**
 * Trains an [NnueNetwork] on [TrainingDataGenerator] output using per-sample Adam SGD, all in
 * Kotlin — no external ML stack. The target for each position blends the search score with the
 * game's final result, both squashed through a sigmoid so a 300-vs-500 disagreement matters more
 * than 3000-vs-5000 (the same trick Texel tuning uses). The net therefore learns both "what the
 * search thought" and "what actually won games".
 *
 * Run via `:engine:trainNnue`, with `-PnnueArgs="data=... out=... hidden=... epochs=... lr=... blend=..."`.
 * The default output path overwrites the bundled network, which is exactly how an improved net ships.
 */
class NnueTrainer(
    private val hiddenSize: Int = 128,
    private val epochs: Int = 12,
    private val learningRate: Float = 0.001f,
    /** 0 = train purely on search scores, 1 = purely on game results. */
    private val resultBlend: Float = 0.3f,
    private val seed: Long = 42L
) {

    fun train(dataFile: Path, outFile: Path): NnueNetwork {
        println("Loading training data from ${dataFile.toAbsolutePath()}")
        val loadStart = System.currentTimeMillis()
        val samples = load(dataFile)
        require(samples.isNotEmpty()) { "no training samples in $dataFile" }
        println("Loaded ${samples.size} positions in ${formatDuration(System.currentTimeMillis() - loadStart)}")
        println("Training: hidden=$hiddenSize epochs=$epochs lr=$learningRate blend=$resultBlend seed=$seed")

        val random = Random(seed)
        val net = NnueNetwork.random(hiddenSize, random)
        val state = AdamState(net)
        val order = IntArray(samples.size) { it }

        val trainStart = System.currentTimeMillis()
        var previousLoss: Double? = null
        for (epoch in 1..epochs) {
            shuffle(order, random)
            val progress = ProgressReporter(samples.size, "positions")
            var loss = 0.0
            var done = 0
            for (i in order) {
                loss += step(net, state, samples[i])
                done++
                progress.update(done, " (epoch $epoch/$epochs)")
            }
            net.save(outFile)   // checkpoint every epoch — an interrupted run still leaves a usable net

            val averageLoss = loss / samples.size
            val delta = previousLoss?.let { " (%+.6f)".format(averageLoss - it) } ?: ""
            previousLoss = averageLoss
            val elapsed = System.currentTimeMillis() - trainStart
            val remaining = elapsed / epoch * (epochs - epoch)
            println(
                "epoch $epoch/$epochs  loss=${"%.6f".format(averageLoss)}$delta  " +
                        "${formatDuration(elapsed)} elapsed, ~${formatDuration(remaining)} left"
            )
        }
        println("Saved network to ${outFile.toAbsolutePath()}")
        return net
    }

    private class Sample(
        val features: IntArray,     // White-perspective feature indices; Black's are derived by mirror
        val whiteToMove: Boolean,
        val target: Float,          // search score in centipawns, side-to-move perspective
        val result: Float           // 1 / 0.5 / 0, side-to-move perspective
    )

    private fun load(dataFile: Path): List<Sample> = Files.newBufferedReader(dataFile).useLines { lines ->
        lines.mapNotNull { line ->
            val parts = line.split(';')
            if (parts.size != 3) return@mapNotNull null
            val board = Board.fromFEN(parts[0])
            val whiteScore = parts[1].toFloat()
            val whiteResult = parts[2].toFloat()
            val features = board.getPiecesWithSquares()
                .map { (square, piece) -> NnueFeatures.index(Colour.WHITE, piece, square) }
                .toIntArray()
            val whiteToMove = board.activeColour == Colour.WHITE
            Sample(
                features = features,
                whiteToMove = whiteToMove,
                target = if (whiteToMove) whiteScore else -whiteScore,
                result = if (whiteToMove) whiteResult else 1f - whiteResult
            )
        }.toList()
    }

    /** Adam first/second moments for every parameter group, plus the reusable per-step buffers. */
    private class AdamState(net: NnueNetwork) {
        val mFt = FloatArray(net.ftWeights.size); val vFt = FloatArray(net.ftWeights.size)
        val mFtB = FloatArray(net.ftBias.size); val vFtB = FloatArray(net.ftBias.size)
        val mOut = FloatArray(net.outWeights.size); val vOut = FloatArray(net.outWeights.size)
        var mOutB = 0f; var vOutB = 0f

        // Running powers of the betas give the bias correction without a pow() per parameter.
        var beta1Power = 1f; var beta2Power = 1f
        var correction1 = 1f; var correction2 = 1f

        fun nextStep() {
            beta1Power *= BETA1; beta2Power *= BETA2
            correction1 = 1f - beta1Power; correction2 = 1f - beta2Power
        }

        val accOwn = FloatArray(net.hiddenSize)
        val accOpp = FloatArray(net.hiddenSize)
        val gradOwn = FloatArray(net.hiddenSize)
        val gradOpp = FloatArray(net.hiddenSize)
    }

    /** One SGD step; returns the sample's loss (squared error in sigmoid space). */
    private fun step(net: NnueNetwork, state: AdamState, sample: Sample): Double {
        val hidden = net.hiddenSize
        val weights = net.ftWeights
        val accOwn = state.accOwn
        val accOpp = state.accOpp

        // Forward: rebuild both perspective accumulators (own = side to move).
        net.ftBias.copyInto(accOwn)
        net.ftBias.copyInto(accOpp)
        for (feature in sample.features) {
            val ownBase = (if (sample.whiteToMove) feature else NnueFeatures.mirror(feature)) * hidden
            val oppBase = (if (sample.whiteToMove) NnueFeatures.mirror(feature) else feature) * hidden
            for (h in 0 until hidden) {
                accOwn[h] += weights[ownBase + h]
                accOpp[h] += weights[oppBase + h]
            }
        }

        var prediction = net.outBias
        for (h in 0 until hidden) prediction += net.outWeights[h] * clip(accOwn[h])
        for (h in 0 until hidden) prediction += net.outWeights[hidden + h] * clip(accOpp[h])

        // The raw output is in sigmoid units (see NnueNetwork.OUTPUT_SCALE); centipawn targets are
        // brought into the same space, so every gradient stays at a scale Adam's step size suits.
        val predicted = sigmoid(prediction)
        val target = (1f - resultBlend) * sigmoid(sample.target / NnueNetwork.OUTPUT_SCALE) +
                resultBlend * sample.result
        val error = predicted - target

        // Backward. Gradient through the sigmoid; the clipped ReLU passes gradient only when open.
        val g = error * predicted * (1f - predicted)
        state.nextStep()
        val gradOwn = state.gradOwn
        val gradOpp = state.gradOpp
        for (h in 0 until hidden) {
            gradOwn[h] = if (accOwn[h] > 0f && accOwn[h] < 1f) g * net.outWeights[h] else 0f
            gradOpp[h] = if (accOpp[h] > 0f && accOpp[h] < 1f) g * net.outWeights[hidden + h] else 0f
            adam(net.outWeights, state.mOut, state.vOut, h, g * clip(accOwn[h]), state)
            adam(net.outWeights, state.mOut, state.vOut, hidden + h, g * clip(accOpp[h]), state)
            adam(net.ftBias, state.mFtB, state.vFtB, h, gradOwn[h] + gradOpp[h], state)
        }

        state.mOutB = BETA1 * state.mOutB + (1f - BETA1) * g
        state.vOutB = BETA2 * state.vOutB + (1f - BETA2) * g * g
        net.outBias -= learningRate * (state.mOutB / state.correction1) /
                (sqrt(state.vOutB / state.correction2) + EPSILON)

        for (feature in sample.features) {
            val ownBase = (if (sample.whiteToMove) feature else NnueFeatures.mirror(feature)) * hidden
            val oppBase = (if (sample.whiteToMove) NnueFeatures.mirror(feature) else feature) * hidden
            for (h in 0 until hidden) {
                if (gradOwn[h] != 0f) adam(weights, state.mFt, state.vFt, ownBase + h, gradOwn[h], state)
                if (gradOpp[h] != 0f) adam(weights, state.mFt, state.vFt, oppBase + h, gradOpp[h], state)
            }
        }

        return (error * error).toDouble()
    }

    private fun adam(params: FloatArray, m: FloatArray, v: FloatArray, i: Int, grad: Float, state: AdamState) {
        m[i] = BETA1 * m[i] + (1f - BETA1) * grad
        v[i] = BETA2 * v[i] + (1f - BETA2) * grad * grad
        params[i] -= learningRate * (m[i] / state.correction1) / (sqrt(v[i] / state.correction2) + EPSILON)
    }

    private fun shuffle(order: IntArray, random: Random) {
        for (i in order.size - 1 downTo 1) {
            val j = random.nextInt(i + 1)
            val tmp = order[i]; order[i] = order[j]; order[j] = tmp
        }
    }

    private fun clip(x: Float): Float = if (x < 0f) 0f else if (x > 1f) 1f else x

    private fun sigmoid(x: Float): Float = 1f / (1f + exp(-x))

    private companion object {
        const val BETA1 = 0.9f
        const val BETA2 = 0.999f
        const val EPSILON = 1e-8f
    }
}

fun main(args: Array<String>) {
    val opts = args.associate { it.substringBefore('=') to it.substringAfter('=') }
    val data = Paths.get(opts["data"] ?: "build/nnue/training-data.txt")
    val out = Paths.get(opts["out"] ?: "src/main/resources/nnue/default.nnue")

    val trainer = NnueTrainer(
        hiddenSize = opts["hidden"]?.toInt() ?: 128,
        epochs = opts["epochs"]?.toInt() ?: 12,
        learningRate = opts["lr"]?.toFloat() ?: 0.001f,
        resultBlend = opts["blend"]?.toFloat() ?: 0.3f,
        seed = opts["seed"]?.toLong() ?: 42L
    )
    println("Training NNUE: data=$data out=$out")
    trainer.train(data, out)
}
