package com.tward.engine.nnue

import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.BufferedReader
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.EOFException
import java.io.InputStream
import java.io.InputStreamReader
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.math.roundToInt

/**
 * One training position in packed form, White's perspective (as the text format): an occupancy
 * bitboard (bit `row * 8 + col`, row 0 = rank 8, matching [com.tward.engine.player.evaluator.nnue.NnueFeatures]),
 * one 4-bit piece kind per occupied square in ascending square order (0..5 = White P N B R Q K,
 * 6..11 = Black), the side to move, White's score in centipawns and White's result doubled (0/1/2).
 * A reusable holder, so loading allocates nothing per position.
 */
internal class PackedPosition {
    var occupancy = 0L
    var kindsLow = 0L     // the kinds of the first 16 pieces
    var kindsHigh = 0L    // the kinds of pieces 17..32
    var whiteToMove = true
    var whiteScore = 0
    var whiteResult2 = 1

    /**
     * A 64-bit key over exactly what the network sees (pieces + side to move), for deduplication.
     * Castling and en passant aren't inputs, so positions differing only there are duplicates too.
     */
    fun key(): Long {
        var h = mix(occupancy)
        h = mix(h xor kindsLow)
        h = mix(h xor kindsHigh)
        return mix(h xor if (whiteToMove) 0x5bd1e9955bd1e995L else 0L)
    }

    private fun mix(value: Long): Long {   // splitmix64's finaliser
        var z = value + -0x61c8864680b583ebL
        z = (z xor (z ushr 30)) * -0x40a7b892e31b1a47L
        z = (z xor (z ushr 27)) * -0x6b2fb644ecceee15L
        return z xor (z ushr 31)
    }
}

/**
 * Training positions held column-wise in primitive arrays: 27 bytes a position, against ~170 for an
 * object holding an `IntArray` of features, so tens of millions of positions fit in a few GB of
 * heap. Features are decoded on the fly by [features] — cheap next to the forward pass.
 */
internal class TrainingSet(capacity: Int = 1 shl 16) {
    var size = 0
        private set
    private var occupancy = LongArray(capacity)
    private var kindsLow = LongArray(capacity)
    private var kindsHigh = LongArray(capacity)
    private var scores = ShortArray(capacity)
    private var flags = ByteArray(capacity)   // bit 0: White to move; bits 1-2: White's result x2

    fun add(p: PackedPosition) {
        if (size == occupancy.size) grow()
        occupancy[size] = p.occupancy
        kindsLow[size] = p.kindsLow
        kindsHigh[size] = p.kindsHigh
        scores[size] = p.whiteScore.coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        flags[size] = ((if (p.whiteToMove) 1 else 0) or (p.whiteResult2 shl 1)).toByte()
        size++
    }

    fun whiteToMove(i: Int): Boolean = (flags[i].toInt() and 1) != 0

    /** Side-to-move search score in centipawns. */
    fun score(i: Int): Int = if (whiteToMove(i)) scores[i].toInt() else -scores[i].toInt()

    /** Side-to-move game result: 1, 0.5 or 0. */
    fun result(i: Int): Float {
        val white = ((flags[i].toInt() ushr 1) and 3) * 0.5f
        return if (whiteToMove(i)) white else 1f - white
    }

    /** Writes position [i]'s White-perspective feature indices into [out]; returns how many. */
    fun features(i: Int, out: IntArray): Int {
        var occ = occupancy[i]
        val low = kindsLow[i]
        val high = kindsHigh[i]
        var k = 0
        while (occ != 0L) {
            val square = java.lang.Long.numberOfTrailingZeros(occ)
            occ = occ and (occ - 1)
            val kind = (if (k < 16) low ushr (4 * k) else high ushr (4 * (k - 16))).toInt() and 15
            out[k++] = kind * 64 + square
        }
        return k
    }

    fun get(i: Int, into: PackedPosition) {
        into.occupancy = occupancy[i]
        into.kindsLow = kindsLow[i]
        into.kindsHigh = kindsHigh[i]
        into.whiteScore = scores[i].toInt()
        into.whiteToMove = whiteToMove(i)
        into.whiteResult2 = (flags[i].toInt() ushr 1) and 3
    }

    private fun grow() {
        val capacity = occupancy.size * 2
        occupancy = occupancy.copyOf(capacity)
        kindsLow = kindsLow.copyOf(capacity)
        kindsHigh = kindsHigh.copyOf(capacity)
        scores = scores.copyOf(capacity)
        flags = flags.copyOf(capacity)
    }
}

/**
 * Reads and writes NNUE training data in two formats, chosen by content rather than file name:
 * - **text**: `FEN;score;result` lines, White's perspective, as [TrainingDataGenerator] writes them
 *   (any encoding with a BOM; PowerShell writes UTF-16).
 * - **binary** (`.bin`, written by [pack]): a header then variable-length records of a flags byte,
 *   a score short, the occupancy long and the piece-kind nibbles. About 26 bytes a position against
 *   ~70 for text, and no FEN parsing to load.
 */
internal object TrainingData {
    private const val MAGIC = 0x4E4E5544 // "NNUD"
    private const val VERSION = 1

    class LoadStats(var loaded: Int = 0, var duplicates: Int = 0, var skipped: Int = 0)

    /**
     * Appends [file]'s positions to [set], skipping any whose key is already in [seen] (pass null
     * to keep duplicates). Malformed text lines are skipped and counted, never thrown.
     */
    fun load(file: Path, set: TrainingSet, seen: LongHashSet?): LoadStats {
        val stats = LoadStats()
        val position = PackedPosition()
        fun accept() {
            if (seen != null && !seen.add(position.key())) stats.duplicates++
            else {
                set.add(position)
                stats.loaded++
            }
        }
        if (isBinary(file)) {
            DataInputStream(BufferedInputStream(Files.newInputStream(file), 1 shl 20)).use { input ->
                input.readInt(); input.readInt()   // header, checked by isBinary
                while (readRecord(input, position)) accept()
            }
        } else {
            openText(file).useLines { lines ->
                for (line in lines) {
                    if (parseText(line, position)) accept() else if (line.isNotBlank()) stats.skipped++
                }
            }
        }
        return stats
    }

    fun isBinary(file: Path): Boolean = DataInputStream(Files.newInputStream(file)).use { input ->
        val magic = try {
            input.readInt()
        } catch (_: EOFException) {
            return false
        }
        if (magic != MAGIC) return false
        require(input.readInt() == VERSION) { "unsupported NNUE data version in $file" }
        true
    }

    /** Parses one `FEN;score;result` line (White's perspective) into [p]; false if malformed. */
    fun parseText(line: String, p: PackedPosition): Boolean {
        val first = line.indexOf(';')
        val second = if (first < 0) -1 else line.indexOf(';', first + 1)
        if (second < 0 || line.indexOf(';', second + 1) >= 0) return false

        var row = 0
        var col = 0
        var count = 0
        var occupancy = 0L
        var low = 0L
        var high = 0L
        var i = 0
        while (i < first && line[i] != ' ') {
            val c = line[i]
            when (c) {
                '/' -> {
                    if (col != 8 || row == 7) return false
                    row++
                    col = 0
                }
                in '1'..'8' -> col += c - '0'
                else -> {
                    val kind = KINDS.indexOf(c)
                    if (kind < 0 || col > 7 || count == 32) return false
                    occupancy = occupancy or (1L shl (row * 8 + col))
                    if (count < 16) low = low or (kind.toLong() shl (4 * count))
                    else high = high or (kind.toLong() shl (4 * (count - 16)))
                    count++
                    col++
                }
            }
            if (col > 8) return false
            i++
        }
        if (row != 7 || col != 8 || i + 1 >= first) return false
        val whiteToMove = when (line[i + 1]) {
            'w' -> true
            'b' -> false
            else -> return false
        }

        val score = line.substring(first + 1, second).trim().toFloatOrNull() ?: return false
        val result = line.substring(second + 1).trim().toFloatOrNull() ?: return false
        val result2 = (result * 2f).roundToInt()
        if (result2 !in 0..2 || kotlin.math.abs(result * 2f - result2) > 1e-3f || score.isNaN()) return false

        p.occupancy = occupancy
        p.kindsLow = low
        p.kindsHigh = high
        p.whiteToMove = whiteToMove
        p.whiteScore = score.roundToInt()
        p.whiteResult2 = result2
        return true
    }

    fun writeHeader(out: DataOutputStream) {
        out.writeInt(MAGIC)
        out.writeInt(VERSION)
    }

    fun writeRecord(out: DataOutputStream, p: PackedPosition) {
        out.writeByte((if (p.whiteToMove) 1 else 0) or (p.whiteResult2 shl 1))
        out.writeShort(p.whiteScore.coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()))
        out.writeLong(p.occupancy)
        val count = java.lang.Long.bitCount(p.occupancy)
        var k = 0
        while (k < count) {
            out.writeByte(kindAt(p, k) or (if (k + 1 < count) kindAt(p, k + 1) shl 4 else 0))
            k += 2
        }
    }

    private fun readRecord(input: DataInputStream, p: PackedPosition): Boolean {
        val flags = input.read()
        if (flags < 0) return false
        p.whiteToMove = (flags and 1) != 0
        p.whiteResult2 = (flags ushr 1) and 3
        p.whiteScore = input.readShort().toInt()
        p.occupancy = input.readLong()
        val count = java.lang.Long.bitCount(p.occupancy)
        var low = 0L
        var high = 0L
        var k = 0
        while (k < count) {
            val byte = input.readUnsignedByte()
            val first = (byte and 15).toLong()
            if (k < 16) low = low or (first shl (4 * k)) else high = high or (first shl (4 * (k - 16)))
            if (k + 1 < count) {
                val second = (byte ushr 4).toLong()
                val index = k + 1
                if (index < 16) low = low or (second shl (4 * index)) else high = high or (second shl (4 * (index - 16)))
            }
            k += 2
        }
        p.kindsLow = low
        p.kindsHigh = high
        return true
    }

    private fun kindAt(p: PackedPosition, k: Int): Int =
        (if (k < 16) p.kindsLow ushr (4 * k) else p.kindsHigh ushr (4 * (k - 16))).toInt() and 15

    /**
     * Converts a text data file to the binary format, keeping every line in order (duplicates
     * included; the trainer drops them at load time, across all its files). Returns the number of
     * positions written.
     */
    fun pack(text: Path, out: Path): Int {
        out.toAbsolutePath().parent?.let(Files::createDirectories)
        val part = out.resolveSibling(out.fileName.toString() + ".part")
        val position = PackedPosition()
        var written = 0
        var skipped = 0
        DataOutputStream(BufferedOutputStream(Files.newOutputStream(part), 1 shl 20)).use { output ->
            writeHeader(output)
            openText(text).useLines { lines ->
                for (line in lines) {
                    if (parseText(line, position)) {
                        writeRecord(output, position)
                        written++
                    } else if (line.isNotBlank()) skipped++
                }
            }
        }
        Files.move(part, out, java.nio.file.StandardCopyOption.REPLACE_EXISTING)
        if (skipped > 0) println("  skipped $skipped unparseable line(s) in $text")
        return written
    }

    /**
     * Opens a text file honouring its byte-order mark. Windows tools (PowerShell `>` redirection,
     * `Set-Content`) write UTF-16 by default, so naive UTF-8 reading of a combined data file dies
     * with MalformedInputException; here that just works. Undecodable bytes are replaced rather than
     * thrown, and the resulting garbage lines are skipped (and counted) by the callers.
     */
    fun openText(file: Path): BufferedReader {
        val input = BufferedInputStream(Files.newInputStream(file), 1 shl 20)
        input.mark(3)
        val bom = ByteArray(3)
        var read = 0
        while (read < 3) {
            val n = input.read(bom, read, 3 - read)
            if (n < 0) break
            read += n
        }
        input.reset()

        fun byte(i: Int) = bom[i].toInt() and 0xFF
        val (charset, bomLength) = when {
            read >= 2 && byte(0) == 0xFF && byte(1) == 0xFE -> StandardCharsets.UTF_16LE to 2
            read >= 2 && byte(0) == 0xFE && byte(1) == 0xFF -> StandardCharsets.UTF_16BE to 2
            read >= 3 && byte(0) == 0xEF && byte(1) == 0xBB && byte(2) == 0xBF -> StandardCharsets.UTF_8 to 3
            else -> StandardCharsets.UTF_8 to 0
        }
        repeat(bomLength) { input.read() }
        return BufferedReader(InputStreamReader(input as InputStream, charset.newDecoder()
            .onMalformedInput(CodingErrorAction.REPLACE)
            .onUnmappableCharacter(CodingErrorAction.REPLACE)), 1 shl 16)
    }

    /** The `.bin` file that packs [text]: same name, extension replaced. */
    fun binaryFor(text: Path): Path {
        val name = text.fileName.toString()
        val stem = if ('.' in name) name.substringBeforeLast('.') else name
        return text.resolveSibling("$stem.bin")
    }

    private const val KINDS = "PNBRQKpnbrqk"
}

/**
 * `:engine:packNnueData "-PnnueArgs=data=a.txt,b.txt"` — writes `a.bin`, `b.bin` next to each text
 * file, skipping any whose `.bin` is already newer than its text (`force=true` rewrites them). With
 * a single input, `out=` names the output instead.
 */
fun main(args: Array<String>) {
    val opts = args.associate { it.substringBefore('=') to it.substringAfter('=') }
    val inputs = (opts["data"] ?: error("data= is required")).split(',').filter { it.isNotBlank() }.map(Paths::get)
    require(opts["out"] == null || inputs.size == 1) { "out= only works with a single data= file" }
    val force = opts["force"] == "true"
    for (input in inputs) {
        require(Files.exists(input)) { "no such file: $input" }
        if (TrainingData.isBinary(input)) {
            println("$input is already binary")
            continue
        }
        val out = opts["out"]?.let(Paths::get) ?: TrainingData.binaryFor(input)
        if (!force && Files.exists(out) &&
            Files.getLastModifiedTime(out) >= Files.getLastModifiedTime(input)
        ) {
            println("$out is up to date")
            continue
        }
        val start = System.currentTimeMillis()
        val written = TrainingData.pack(input, out)
        println(
            "Packed $written positions: $input (${Files.size(input) / 1_000_000} MB) -> " +
                    "$out (${Files.size(out) / 1_000_000} MB) in ${formatDuration(System.currentTimeMillis() - start)}"
        )
    }
}
