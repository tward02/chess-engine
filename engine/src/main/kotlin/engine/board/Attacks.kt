package com.tward.engine.board

import kotlin.random.Random

/**
 * Precomputed attack bitboards (square indices per [Bitboards.index]). Knights, kings and pawns
 * are plain per-square tables; rooks and bishops use **magic bitboards**: the blockers on a
 * slider's rays are multiplied by a per-square magic number whose top bits index a table of
 * precomputed attack sets, so a slider's attacks cost one multiply and one lookup.
 *
 * The magics are searched for at class load with a fixed seed (deterministic, ~100 ms once per
 * JVM) rather than hard-coded, since published magics assume a different square numbering.
 */
object Attacks {

    private val knightTable = LongArray(64)
    private val kingTable = LongArray(64)
    private val pawnTable = LongArray(128)
    private val betweenTable = LongArray(64 * 64)
    private val knightLists = Array(64) { IntArray(0) }
    private val kingLists = Array(64) { IntArray(0) }
    private val rayLists = Array(8 * 64) { IntArray(0) }

    /**
     * The eight directions as (dCol, dRow), diagonals first: the order [MoveGenerator]'s offset
     * lists use (bishop = 0..3, rook = 4..7, queen and king = 0..7). Keeping it means bitboard code
     * generates moves in exactly the old order, so move ordering ties break the same way.
     */
    val DIRECTIONS: Array<IntArray> = arrayOf(
        intArrayOf(1, 1), intArrayOf(1, -1), intArrayOf(-1, 1), intArrayOf(-1, -1),
        intArrayOf(1, 0), intArrayOf(-1, 0), intArrayOf(0, 1), intArrayOf(0, -1)
    )

    private val rookMagics = Magics(arrayOf(intArrayOf(1, 0), intArrayOf(-1, 0), intArrayOf(0, 1), intArrayOf(0, -1)))
    private val bishopMagics = Magics(arrayOf(intArrayOf(1, 1), intArrayOf(1, -1), intArrayOf(-1, 1), intArrayOf(-1, -1)))

    init {
        val knightSteps = arrayOf(1 to 2, 2 to 1, -1 to 2, -2 to 1, 1 to -2, 2 to -1, -1 to -2, -2 to -1)
        val kingSteps = arrayOf(1 to 0, -1 to 0, 0 to 1, 0 to -1, 1 to 1, 1 to -1, -1 to 1, -1 to -1)
        for (sq in 0 until 64) {
            val col = Bitboards.col(sq)
            val row = Bitboards.row(sq)
            knightTable[sq] = steps(col, row, knightSteps)
            kingTable[sq] = steps(col, row, kingSteps)
            // White pawns advance towards row 0, Black towards row 7.
            pawnTable[sq] = steps(col, row, arrayOf(-1 to -1, 1 to -1))
            pawnTable[64 + sq] = steps(col, row, arrayOf(-1 to 1, 1 to 1))
        }
        for (sq in 0 until 64) {
            knightLists[sq] = stepList(sq, knightSteps.map { intArrayOf(it.first, it.second) })
            kingLists[sq] = stepList(sq, DIRECTIONS.toList())
            for (d in 0 until 8) {
                val ray = ArrayList<Int>()
                var col = Bitboards.col(sq) + DIRECTIONS[d][0]
                var row = Bitboards.row(sq) + DIRECTIONS[d][1]
                while (col in 0..7 && row in 0..7) {
                    ray += Bitboards.index(col, row)
                    col += DIRECTIONS[d][0]
                    row += DIRECTIONS[d][1]
                }
                rayLists[d * 64 + sq] = ray.toIntArray()
            }
        }
        for (a in 0 until 64) {
            for ((dCol, dRow) in kingSteps) {
                var col = Bitboards.col(a) + dCol
                var row = Bitboards.row(a) + dRow
                var path = 0L
                while (col in 0..7 && row in 0..7) {
                    val b = Bitboards.index(col, row)
                    betweenTable[a * 64 + b] = path
                    path = path or (1L shl b)
                    col += dCol
                    row += dRow
                }
            }
        }
    }

    fun knight(sq: Int): Long = knightTable[sq]

    fun king(sq: Int): Long = kingTable[sq]

    /** Squares a [colour] pawn on [sq] attacks. */
    fun pawn(colour: Colour, sq: Int): Long = pawnTable[colour.ordinal * 64 + sq]

    fun rook(sq: Int, occupied: Long): Long = rookMagics.attacks(sq, occupied)

    fun bishop(sq: Int, occupied: Long): Long = bishopMagics.attacks(sq, occupied)

    fun queen(sq: Int, occupied: Long): Long = rook(sq, occupied) or bishop(sq, occupied)

    /** Squares strictly between [a] and [b] if they share a rank, file or diagonal; otherwise 0. */
    fun between(a: Int, b: Int): Long = betweenTable[a * 64 + b]

    /** Knight targets from [sq], in MoveGenerator.knightOffsets order. */
    fun knightTargets(sq: Int): IntArray = knightLists[sq]

    /** King targets from [sq], in [DIRECTIONS] order. */
    fun kingTargets(sq: Int): IntArray = kingLists[sq]

    /** The squares along direction [direction] (index into [DIRECTIONS]) from [sq], nearest first. */
    fun ray(direction: Int, sq: Int): IntArray = rayLists[direction * 64 + sq]

    private fun stepList(sq: Int, deltas: List<IntArray>): IntArray =
        deltas.mapNotNull { (dCol, dRow) ->
            val c = Bitboards.col(sq) + dCol
            val r = Bitboards.row(sq) + dRow
            if (c in 0..7 && r in 0..7) Bitboards.index(c, r) else null
        }.toIntArray()

    private fun steps(col: Int, row: Int, deltas: Array<Pair<Int, Int>>): Long {
        var bits = 0L
        for ((dCol, dRow) in deltas) {
            val c = col + dCol
            val r = row + dRow
            if (c in 0..7 && r in 0..7) bits = bits or (1L shl Bitboards.index(c, r))
        }
        return bits
    }

    /** Ray-walking reference for one slider: what the magic tables are filled from (and tested against). */
    internal fun slidingAttacks(sq: Int, occupied: Long, directions: Array<IntArray>): Long {
        var bits = 0L
        for (dir in directions) {
            var col = Bitboards.col(sq) + dir[0]
            var row = Bitboards.row(sq) + dir[1]
            while (col in 0..7 && row in 0..7) {
                val bit = 1L shl Bitboards.index(col, row)
                bits = bits or bit
                if (occupied and bit != 0L) break
                col += dir[0]
                row += dir[1]
            }
        }
        return bits
    }

    internal fun rookReference(sq: Int, occupied: Long): Long = slidingAttacks(sq, occupied, rookMagics.directions)

    internal fun bishopReference(sq: Int, occupied: Long): Long = slidingAttacks(sq, occupied, bishopMagics.directions)

    private class Magics(val directions: Array<IntArray>) {
        private val masks = LongArray(64)
        private val magics = LongArray(64)
        private val shifts = IntArray(64)
        private val offsets = IntArray(64)
        private val table: LongArray

        init {
            val random = Random(0x7EA5_B0A8D)
            var size = 0
            val subsets = arrayOfNulls<LongArray>(64)
            val attacks = arrayOfNulls<LongArray>(64)
            for (sq in 0 until 64) {
                val mask = relevantMask(sq)
                val bits = java.lang.Long.bitCount(mask)
                masks[sq] = mask
                shifts[sq] = 64 - bits
                offsets[sq] = size
                size += 1 shl bits
                // Every subset of the mask (carry-rippler) and the attacks it produces.
                val occ = LongArray(1 shl bits)
                val att = LongArray(1 shl bits)
                var subset = 0L
                var i = 0
                do {
                    occ[i] = subset
                    att[i] = slidingAttacks(sq, subset, directions)
                    i++
                    subset = (subset - mask) and mask
                } while (subset != 0L)
                subsets[sq] = occ
                attacks[sq] = att
            }
            table = LongArray(size)
            for (sq in 0 until 64) {
                magics[sq] = findMagic(sq, subsets[sq]!!, attacks[sq]!!, random)
            }
        }

        fun attacks(sq: Int, occupied: Long): Long =
            table[offsets[sq] + (((occupied and masks[sq]) * magics[sq]) ushr shifts[sq]).toInt()]

        // The rays without their last square: a piece on the edge never blocks anything beyond it.
        private fun relevantMask(sq: Int): Long {
            var bits = 0L
            for (dir in directions) {
                var col = Bitboards.col(sq) + dir[0]
                var row = Bitboards.row(sq) + dir[1]
                while (col + dir[0] in 0..7 && row + dir[1] in 0..7) {
                    bits = bits or (1L shl Bitboards.index(col, row))
                    col += dir[0]
                    row += dir[1]
                }
            }
            return bits
        }

        /** Tries sparse random numbers until one maps every subset without a harmful collision. */
        private fun findMagic(sq: Int, occ: LongArray, att: LongArray, random: Random): Long {
            val size = occ.size
            val epoch = IntArray(size)
            var attempt = 0
            while (true) {
                val magic = random.nextLong() and random.nextLong() and random.nextLong()
                if (java.lang.Long.bitCount((masks[sq] * magic) and TOP_BYTE) < 6) continue
                attempt++
                var ok = true
                for (i in 0 until size) {
                    val index = offsets[sq] + ((occ[i] * magic) ushr shifts[sq]).toInt()
                    val slot = index - offsets[sq]
                    if (epoch[slot] != attempt) {
                        epoch[slot] = attempt
                        table[index] = att[i]
                    } else if (table[index] != att[i]) {
                        ok = false
                        break
                    }
                }
                if (ok) return magic
            }
        }
    }

    private const val TOP_BYTE = -0x100000000000000L   // 0xFF00_0000_0000_0000
}
