package com.tward.engine.nnue

import com.tward.engine.player.evaluator.nnue.NnueFeatures
import com.tward.engine.player.evaluator.nnue.NnueNetwork
import java.nio.file.Path
import java.nio.file.Paths
import java.util.Random
import java.util.concurrent.Callable
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.sqrt

/** How the learning rate moves over a training run. */
enum class LearningRateSchedule {
    /** The same rate throughout. */
    CONSTANT,

    /** Cosine decay from the initial rate to the minimum over all planned steps. */
    COSINE
}

/**
 * Trains an [NnueNetwork] on [TrainingDataGenerator] output (text, or binary packed by
 * `:engine:packNnueData`) with mini-batch Adam, all in Kotlin — no external ML stack. The target for
 * each position blends the search score with the game's final result, both squashed through a
 * sigmoid so a 300-vs-500 disagreement matters more than 3000-vs-5000 (the same trick Texel tuning
 * uses). The net therefore learns both "what the search thought" and "what actually won games".
 *
 * Each mini-batch is split across [threads] workers that accumulate gradients into their own
 * buffers; the sums are then applied in parallel, and only to the first-layer columns some position
 * in the batch actually used (~32 features a position — "lazy" Adam). A run is deterministic for a
 * given seed and thread count. `batchSize = 1, threads = 1, schedule = CONSTANT` is per-sample Adam,
 * the trainer's original behaviour.
 *
 * A [validationFraction] of the data is held out, in blocks of consecutive positions (positions from
 * one game are near-duplicates, so a per-position split would leak). Its loss is reported every
 * epoch, the output file always holds the epoch with the **best validation loss**, and training stops
 * after [patience] epochs without improvement.
 *
 * [weightDecay] adds decoupled (AdamW-style) weight decay: each step shrinks the weights it updates
 * by `rate * weightDecay` before the Adam step, pulling unused capacity back towards zero so a wide
 * net generalises instead of memorising. It applies to weights only, never biases, and (like the
 * Adam step) only to the first-layer columns the batch touched. 0 (the default) is plain Adam.
 *
 * Run via `:engine:trainNnue`, with `-PnnueArgs="data=... out=... hidden=... epochs=... lr=...
 * minLr=... schedule=... batch=... threads=... patience=... blend=... val=... dedupe=... decay=..."`.
 */
class NnueTrainer(
    private val hiddenSize: Int = 128,
    private val epochs: Int = 12,
    private val learningRate: Float = DEFAULT_LEARNING_RATE,
    /** 0 = train purely on search scores, 1 = purely on game results. */
    private val resultBlend: Float = 0.3f,
    private val seed: Long = 42L,
    /** Share of the data held out for validation; 0 trains on everything and saves every epoch. */
    private val validationFraction: Float = 0.02f,
    /**
     * Drop repeated positions (same pieces and side to move), keeping the first occurrence.
     * Self-play revisits common positions constantly, and duplicates silently overweight them and
     * leak between the training and validation splits.
     */
    private val dedupe: Boolean = true,
    private val batchSize: Int = DEFAULT_BATCH,
    private val threads: Int = Runtime.getRuntime().availableProcessors(),
    private val schedule: LearningRateSchedule = LearningRateSchedule.COSINE,
    /** Where [LearningRateSchedule.COSINE] ends. */
    private val minLearningRate: Float = learningRate * 0.05f,
    /** Stop after this many epochs without a better validation loss; 0 never stops early. */
    private val patience: Int = 3,
    /** Decoupled weight decay per unit of learning rate; 0 disables it. Typical values 1e-4..1e-2. */
    private val weightDecay: Float = 0f
) {
    init {
        require(validationFraction in 0f..0.5f) { "validationFraction must be in 0..0.5" }
        require(batchSize >= 1) { "batchSize must be at least 1" }
        require(threads >= 1) { "threads must be at least 1" }
        require(patience >= 0) { "patience must be non-negative" }
        require(weightDecay >= 0f) { "weightDecay must be non-negative" }
    }

    fun train(dataFile: Path, outFile: Path): NnueNetwork = train(listOf(dataFile), outFile)

    fun train(dataFiles: List<Path>, outFile: Path): NnueNetwork {
        val loadStart = System.currentTimeMillis()
        val set = TrainingSet()
        val seen = if (dedupe) LongHashSet() else null
        var duplicates = 0
        for (file in dataFiles) {
            println("Loading training data from ${file.toAbsolutePath()}")
            val stats = TrainingData.load(file, set, seen)
            duplicates += stats.duplicates
            if (stats.skipped > 0) println("  skipped ${stats.skipped} unparseable line(s) in $file")
        }
        if (dedupe) println("Dropped $duplicates duplicate positions")
        require(set.size > 0) {
            "no training samples in $dataFiles — is the file NNUE data (FEN;score;result lines)?"
        }
        println("Loaded ${set.size} positions in ${formatDuration(System.currentTimeMillis() - loadStart)}")

        val (training, validation) = splitValidation(set.size)
        val workerCount = threads.coerceAtMost(max(1, batchSize))
        println(
            "Training: hidden=$hiddenSize epochs=$epochs lr=$learningRate schedule=${schedule.name.lowercase()}" +
                    "${if (schedule == LearningRateSchedule.COSINE) " minLr=$minLearningRate" else ""} " +
                    "batch=$batchSize threads=$workerCount blend=$resultBlend seed=$seed " +
                    "${if (weightDecay > 0f) "decay=$weightDecay " else ""}" +
                    "train=${training.size} validation=${validation.size}"
        )

        val random = Random(seed)
        val net = NnueNetwork.random(hiddenSize, random)
        val run = Run(net, set, workerCount)
        try {
            return run.train(training, validation, random, outFile)
        } finally {
            run.close()
        }
    }

    /** Index lists for training and validation; whole blocks go to validation so games don't leak. */
    private fun splitValidation(size: Int): Pair<IntArray, IntArray> {
        if (validationFraction == 0f) return IntArray(size) { it } to IntArray(0)
        val every = Math.round(1f / validationFraction).coerceAtLeast(2)
        fun isValidation(i: Int) = (i / VALIDATION_BLOCK) % every == every - 1
        val validationCount = (0 until size).count(::isValidation)
        val training = IntArray(size - validationCount)
        val validation = IntArray(validationCount)
        var t = 0
        var v = 0
        for (i in 0 until size) if (isValidation(i)) validation[v++] = i else training[t++] = i
        return training to validation
    }

    /** The rate for [step] (0-based) of [totalSteps]. */
    internal fun learningRateAt(step: Long, totalSteps: Long): Float = when (schedule) {
        LearningRateSchedule.CONSTANT -> learningRate
        LearningRateSchedule.COSINE -> {
            val progress = if (totalSteps <= 1) 0.0 else step.toDouble() / (totalSteps - 1)
            (minLearningRate + 0.5 * (learningRate - minLearningRate) * (1 + cos(PI * progress.coerceIn(0.0, 1.0))))
                .toFloat()
        }
    }

    /** One training run's mutable state: the net, its Adam moments, the workers and their pool. */
    private inner class Run(val net: NnueNetwork, val set: TrainingSet, workerCount: Int) {
        val hidden = net.hiddenSize
        val workers = Array(workerCount) { Worker(hidden) }
        val pool: ExecutorService? = if (workerCount > 1) {
            Executors.newFixedThreadPool(workerCount) { task -> Thread(task, "nnue-trainer").apply { isDaemon = true } }
        } else null

        val mFt = FloatArray(net.ftWeights.size); val vFt = FloatArray(net.ftWeights.size)
        val mFtB = FloatArray(hidden); val vFtB = FloatArray(hidden)
        val mOut = FloatArray(2 * hidden); val vOut = FloatArray(2 * hidden)
        var mOutB = 0f; var vOutB = 0f

        // Running powers of the betas give Adam's bias correction without a pow() per parameter.
        var beta1Power = 1f; var beta2Power = 1f
        var correction1 = 1f; var correction2 = 1f
        var rate = learningRate

        val touchedColumns = IntArray(NnueFeatures.COUNT)
        var touchedCount = 0

        fun train(training: IntArray, validation: IntArray, random: Random, outFile: Path): NnueNetwork {
            val stepsPerEpoch = (training.size + batchSize - 1) / batchSize
            val totalSteps = stepsPerEpoch.toLong() * epochs
            var step = 0L
            val trainStart = System.currentTimeMillis()
            var previousLoss: Double? = null
            var bestValidation = Double.MAX_VALUE
            var bestEpoch = 0

            for (epoch in 1..epochs) {
                val epochStart = System.currentTimeMillis()
                shuffle(training, random)
                val progress = ProgressReporter(training.size, "positions")
                workers.forEach { it.loss = 0.0 }
                var from = 0
                while (from < training.size) {
                    val to = minOf(from + batchSize, training.size)
                    rate = learningRateAt(step++, totalSteps)
                    batch(training, from, to)
                    from = to
                    progress.update(from, " (epoch $epoch/$epochs)")
                }

                val averageLoss = workers.sumOf { it.loss } / training.size
                val delta = previousLoss?.let { " (%+.6f)".format(averageLoss - it) } ?: ""
                previousLoss = averageLoss

                // Checkpoint each improving epoch, so an interrupted run still leaves the best net so far.
                val validationText = if (validation.isEmpty()) {
                    net.save(outFile)
                    ""
                } else {
                    val validationLoss = validationLoss(validation)
                    val improved = validationLoss < bestValidation
                    if (improved) {
                        bestValidation = validationLoss
                        bestEpoch = epoch
                        net.save(outFile)
                    }
                    "  val=${"%.6f".format(validationLoss)}${if (improved) " (best, saved)" else ""}"
                }

                val now = System.currentTimeMillis()
                val elapsed = now - trainStart
                val remaining = elapsed / epoch * (epochs - epoch)
                val perSecond = training.size * 1000L / max(1L, now - epochStart)
                println(
                    "epoch $epoch/$epochs  loss=${"%.6f".format(averageLoss)}$delta$validationText  " +
                            "lr=${"%.2e".format(rate)}  $perSecond pos/s  " +
                            "${formatDuration(elapsed)} elapsed, ~${formatDuration(remaining)} left"
                )
                if (validation.isNotEmpty() && patience > 0 && epoch - bestEpoch >= patience && epoch < epochs) {
                    println("Stopping early: no validation improvement for $patience epochs")
                    break
                }
            }
            val which = if (validation.isEmpty()) "final epoch" else "epoch $bestEpoch, best validation loss"
            println("Saved network ($which) to ${outFile.toAbsolutePath()}")
            return NnueNetwork.load(outFile)
        }

        /** One optimiser step over `order[from until to]`. */
        fun batch(order: IntArray, from: Int, to: Int) {
            val count = to - from
            parallel { w -> workers[w].accumulate(order, from + count * w / workers.size, from + count * (w + 1) / workers.size) }

            // Union of the first-layer columns any worker touched; only those are updated.
            touchedCount = 0
            for (f in 0 until NnueFeatures.COUNT) {
                var touched = false
                for (worker in workers) if (worker.touched[f]) {
                    touched = true
                    worker.touched[f] = false
                }
                if (touched) touchedColumns[touchedCount++] = f
            }

            beta1Power *= BETA1; beta2Power *= BETA2
            correction1 = 1f - beta1Power; correction2 = 1f - beta2Power
            val scale = 1f / count
            parallel { w -> applyColumns(scale, touchedCount * w / workers.size, touchedCount * (w + 1) / workers.size) }

            for (h in 0 until hidden) {
                var biasGrad = 0f
                for (worker in workers) { biasGrad += worker.ftBiasGrad[h]; worker.ftBiasGrad[h] = 0f }
                adam(net.ftBias, mFtB, vFtB, h, biasGrad * scale, decay = false)
            }
            for (j in 0 until 2 * hidden) {
                var outGrad = 0f
                for (worker in workers) { outGrad += worker.outGrad[j]; worker.outGrad[j] = 0f }
                adam(net.outWeights, mOut, vOut, j, outGrad * scale, decay = true)
            }
            var outBiasGrad = 0f
            for (worker in workers) { outBiasGrad += worker.outBiasGrad; worker.outBiasGrad = 0f }
            outBiasGrad *= scale
            mOutB = BETA1 * mOutB + (1f - BETA1) * outBiasGrad
            vOutB = BETA2 * vOutB + (1f - BETA2) * outBiasGrad * outBiasGrad
            net.outBias -= rate * (mOutB / correction1) / (sqrt(vOutB / correction2) + EPSILON)
        }

        /** Sums the workers' gradients for `touchedColumns[from until to]`, clears them and steps Adam. */
        fun applyColumns(scale: Float, from: Int, to: Int) {
            val weights = net.ftWeights
            for (c in from until to) {
                val base = touchedColumns[c] * hidden
                for (h in base until base + hidden) {
                    var grad = 0f
                    for (worker in workers) { grad += worker.ftGrad[h]; worker.ftGrad[h] = 0f }
                    adam(weights, mFt, vFt, h, grad * scale, decay = true)
                }
            }
        }

        fun adam(params: FloatArray, m: FloatArray, v: FloatArray, i: Int, grad: Float, decay: Boolean) {
            if (decay && weightDecay > 0f) params[i] -= rate * weightDecay * params[i]
            m[i] = BETA1 * m[i] + (1f - BETA1) * grad
            v[i] = BETA2 * v[i] + (1f - BETA2) * grad * grad
            params[i] -= rate * (m[i] / correction1) / (sqrt(v[i] / correction2) + EPSILON)
        }

        fun validationLoss(validation: IntArray): Double {
            val losses = DoubleArray(workers.size)
            parallel { w ->
                val worker = workers[w]
                var loss = 0.0
                for (k in validation.size * w / workers.size until validation.size * (w + 1) / workers.size) {
                    val i = validation[k]
                    val error = sigmoid(worker.forward(i)) - target(i)
                    loss += error * error
                }
                losses[w] = loss
            }
            return losses.sum() / validation.size
        }

        /** Runs [task] once per worker index, on the pool when there is one; returns when all finish. */
        fun parallel(task: (Int) -> Unit) {
            if (pool == null) {
                task(0)
                return
            }
            pool.invokeAll(workers.indices.map { w -> Callable { task(w) } }).forEach { it.get() }
        }

        fun close() {
            pool?.shutdownNow()
        }

        fun target(i: Int): Float =
            (1f - resultBlend) * sigmoid(set.score(i) / NnueNetwork.OUTPUT_SCALE) + resultBlend * set.result(i)

        /** A thread's scratch space and gradient accumulators. */
        inner class Worker(hidden: Int) {
            val features = IntArray(32)
            var featureCount = 0
            val accOwn = FloatArray(hidden)
            val accOpp = FloatArray(hidden)
            val gradOwn = FloatArray(hidden)
            val gradOpp = FloatArray(hidden)
            val ftGrad = FloatArray(NnueFeatures.COUNT * hidden)
            val touched = BooleanArray(NnueFeatures.COUNT)
            val ftBiasGrad = FloatArray(hidden)
            val outGrad = FloatArray(2 * hidden)
            var outBiasGrad = 0f
            var loss = 0.0

            /** Raw output (sigmoid units) for position [i]; leaves both accumulators for backprop. */
            fun forward(i: Int): Float {
                val weights = net.ftWeights
                val whiteToMove = set.whiteToMove(i)
                featureCount = set.features(i, features)
                net.ftBias.copyInto(accOwn)
                net.ftBias.copyInto(accOpp)
                for (k in 0 until featureCount) {
                    val feature = features[k]
                    val mirrored = NnueFeatures.mirror(feature)
                    val ownBase = (if (whiteToMove) feature else mirrored) * hidden
                    val oppBase = (if (whiteToMove) mirrored else feature) * hidden
                    for (h in 0 until hidden) {
                        accOwn[h] += weights[ownBase + h]
                        accOpp[h] += weights[oppBase + h]
                    }
                }
                val out = net.outWeights
                var prediction = net.outBias
                for (h in 0 until hidden) prediction += out[h] * clip(accOwn[h]) + out[hidden + h] * clip(accOpp[h])
                return prediction
            }

            /** Adds the gradients of `order[from until to]` to this worker's buffers. */
            fun accumulate(order: IntArray, from: Int, to: Int) {
                val out = net.outWeights
                for (k in from until to) {
                    val i = order[k]
                    val predicted = sigmoid(forward(i))
                    val error = predicted - target(i)
                    loss += error * error

                    // Gradient through the sigmoid; the clipped ReLU passes gradient only when open.
                    val g = error * predicted * (1f - predicted)
                    outBiasGrad += g
                    for (h in 0 until hidden) {
                        val own = accOwn[h]
                        val opp = accOpp[h]
                        outGrad[h] += g * clip(own)
                        outGrad[hidden + h] += g * clip(opp)
                        gradOwn[h] = if (own > 0f && own < 1f) g * out[h] else 0f
                        gradOpp[h] = if (opp > 0f && opp < 1f) g * out[hidden + h] else 0f
                        ftBiasGrad[h] += gradOwn[h] + gradOpp[h]
                    }

                    val whiteToMove = set.whiteToMove(i)
                    for (f in 0 until featureCount) {
                        val feature = features[f]
                        val mirrored = NnueFeatures.mirror(feature)
                        val own = if (whiteToMove) feature else mirrored
                        val opp = if (whiteToMove) mirrored else feature
                        touched[own] = true
                        touched[opp] = true
                        val ownBase = own * hidden
                        val oppBase = opp * hidden
                        for (h in 0 until hidden) {
                            ftGrad[ownBase + h] += gradOwn[h]
                            ftGrad[oppBase + h] += gradOpp[h]
                        }
                    }
                }
            }
        }
    }

    private fun shuffle(order: IntArray, random: Random) {
        for (i in order.size - 1 downTo 1) {
            val j = random.nextInt(i + 1)
            val tmp = order[i]; order[i] = order[j]; order[j] = tmp
        }
    }

    private fun clip(x: Float): Float = if (x < 0f) 0f else if (x > 1f) 1f else x

    private fun sigmoid(x: Float): Float = 1f / (1f + exp(-x))

    companion object {
        const val DEFAULT_LEARNING_RATE = 0.002f
        const val DEFAULT_BATCH = 1024
        private const val BETA1 = 0.9f
        private const val BETA2 = 0.999f
        private const val EPSILON = 1e-8f
        private const val VALIDATION_BLOCK = 512
    }
}

fun main(args: Array<String>) {
    val opts = args.associate { it.substringBefore('=') to it.substringAfter('=') }
    // Comma-separated so multiple generation runs train together with no combining step.
    val data = (opts["data"] ?: "../nnue-data/training-data.txt").split(',').map(Paths::get)
    val out = Paths.get(opts["out"] ?: "src/main/resources/nnue/new.nnue")
    val learningRate = opts["lr"]?.toFloat() ?: NnueTrainer.DEFAULT_LEARNING_RATE

    val trainer = NnueTrainer(
        hiddenSize = opts["hidden"]?.toInt() ?: 128,
        epochs = opts["epochs"]?.toInt() ?: 12,
        learningRate = learningRate,
        resultBlend = opts["blend"]?.toFloat() ?: 0.3f,
        seed = opts["seed"]?.toLong() ?: 42L,
        validationFraction = opts["val"]?.toFloat() ?: 0.02f,
        dedupe = opts["dedupe"] != "false",
        batchSize = opts["batch"]?.toInt() ?: NnueTrainer.DEFAULT_BATCH,
        threads = opts["threads"]?.toInt() ?: Runtime.getRuntime().availableProcessors(),
        schedule = opts["schedule"]?.let { LearningRateSchedule.valueOf(it.uppercase()) } ?: LearningRateSchedule.COSINE,
        minLearningRate = opts["minLr"]?.toFloat() ?: (learningRate * 0.05f),
        patience = opts["patience"]?.toInt() ?: 3,
        weightDecay = opts["decay"]?.toFloat() ?: 0f
    )
    println("Training NNUE: data=$data out=$out")
    trainer.train(data, out)
}
