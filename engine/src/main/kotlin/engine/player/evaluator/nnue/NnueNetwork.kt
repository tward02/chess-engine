package com.tward.engine.player.evaluator.nnue

import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path
import java.util.Random
import java.util.concurrent.ConcurrentHashMap

/**
 * The weights of an NNUE-style evaluation network. 768 one-hot piece-square inputs (indexed by
 * [NnueFeatures]) feed one hidden "accumulator" layer, computed once from each side's perspective;
 * the two hidden vectors are clipped to [0, 1], concatenated side-to-move first, and a linear output
 * layer maps them to a centipawn score for the side to move.
 *
 * This class is pure data plus file IO — [com.tward.engine.player.evaluator.NnueEvaluator] runs
 * inference and `com.tward.engine.nnue.NnueTrainer` produces the weights. Loaded networks are cached
 * per name (like the opening book) so constructing many bots is cheap.
 */
class NnueNetwork(
    val hiddenSize: Int,
    /** `[feature * hiddenSize + h]` — the hidden column added when a feature is active. */
    val ftWeights: FloatArray,
    val ftBias: FloatArray,
    /** `[2 * hiddenSize]` — side-to-move half first, opponent half second. */
    val outWeights: FloatArray,
    var outBias: Float   // var (like the array contents) so the trainer can update it in place
) {

    init {
        require(hiddenSize > 0) { "hiddenSize must be positive" }
        require(ftWeights.size == NnueFeatures.COUNT * hiddenSize) { "ftWeights size mismatch" }
        require(ftBias.size == hiddenSize) { "ftBias size mismatch" }
        require(outWeights.size == 2 * hiddenSize) { "outWeights size mismatch" }
    }

    fun save(path: Path) {
        path.toAbsolutePath().parent?.let(Files::createDirectories)
        DataOutputStream(BufferedOutputStream(Files.newOutputStream(path))).use { out ->
            out.writeInt(MAGIC)
            out.writeInt(VERSION)
            out.writeInt(hiddenSize)
            ftWeights.forEach(out::writeFloat)
            ftBias.forEach(out::writeFloat)
            outWeights.forEach(out::writeFloat)
            out.writeFloat(outBias)
        }
    }

    companion object {
        private const val MAGIC = 0x4E4E5545 // "NNUE"
        private const val VERSION = 1

        /**
         * Centipawns per raw output unit. The network computes in small dimensionless units (which
         * keeps every parameter at a scale SGD handles well — a forced-win position is ~±3, not ±900)
         * and inference multiplies by this; ~+1 unit is a 73% expected score.
         */
        const val OUTPUT_SCALE = 400f

        /** The bundled network the catalog bots play with; retrain and overwrite it to upgrade them. */
        const val DEFAULT_RESOURCE = "/nnue/default.nnue"

        private val cache = ConcurrentHashMap<String, NnueNetwork>()

        fun default(): NnueNetwork = fromResource(DEFAULT_RESOURCE)

        fun fromResource(name: String): NnueNetwork = cache.getOrPut(name) {
            val stream = NnueNetwork::class.java.getResourceAsStream(name)
                ?: throw IOException(
                    "NNUE network resource $name not found — generate one with " +
                            ":engine:generateNnueData then :engine:trainNnue"
                )
            stream.use(::read)
        }

        fun load(path: Path): NnueNetwork = Files.newInputStream(path).use(::read)

        fun read(input: InputStream): NnueNetwork {
            val data = DataInputStream(BufferedInputStream(input))
            require(data.readInt() == MAGIC) { "not an NNUE network file" }
            require(data.readInt() == VERSION) { "unsupported NNUE network version" }
            val hidden = data.readInt()
            fun floats(count: Int) = FloatArray(count) { data.readFloat() }
            return NnueNetwork(
                hiddenSize = hidden,
                ftWeights = floats(NnueFeatures.COUNT * hidden),
                ftBias = floats(hidden),
                outWeights = floats(2 * hidden),
                outBias = data.readFloat()
            )
        }

        /**
         * Small random weights — the trainer's starting point. Biases start mid-clip (0.5) so every
         * hidden unit begins in the linear region of the clipped ReLU and can learn in both directions.
         */
        fun random(hiddenSize: Int, random: Random): NnueNetwork {
            fun noise(count: Int, scale: Float) = FloatArray(count) { (random.nextFloat() * 2f - 1f) * scale }
            return NnueNetwork(
                hiddenSize = hiddenSize,
                ftWeights = noise(NnueFeatures.COUNT * hiddenSize, 0.05f),
                ftBias = FloatArray(hiddenSize) { 0.5f },
                outWeights = noise(2 * hiddenSize, 0.05f),
                outBias = 0f
            )
        }
    }
}