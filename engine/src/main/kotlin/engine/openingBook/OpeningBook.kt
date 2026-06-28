package com.tward.engine.openingBook

import java.util.concurrent.ConcurrentHashMap
import kotlin.jvm.java
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.pow
import kotlin.random.Random

data class BookMove(val moveStr: String, val numTimesPlayed: Int)

class OpeningBook(file: String) {

    // Parsed once per file path and cached read-only; safe to share across many bot instances
    val movesByPosition: Map<String, List<BookMove>> = parsedBooks.getOrPut(file) { parse(file) }

    fun hasBookMove(fen: String): Boolean {
        return movesByPosition.containsKey(fen)
    }

    fun getBookMove(fen: String, weight: Double = 0.5): BookMove? {

        val trueWeight = weight.coerceIn(0.0, 1.0)

        fun weightedPlayCount(playCount: Int): Int {
            return ceil(playCount.toDouble().pow(trueWeight)).toInt()
        }

        val moves = movesByPosition[fen] ?: return null

        var totalPlayCount = 0
        for (move in moves) {
            totalPlayCount += weightedPlayCount(move.numTimesPlayed)
        }

        val weights = mutableListOf<Double>()
        var weightsSum = 0.0
        for (move in moves) {
            val weight = weightedPlayCount(move.numTimesPlayed) / totalPlayCount.toDouble()
            weightsSum += weight
            weights.add(weight)
        }

        val probabilityCumulation = MutableList(moves.size) { 0.0 }
        for ((i, element) in weights.withIndex()) {
            val prob = element / weightsSum
            probabilityCumulation[i] = probabilityCumulation[max(0, i - 1)] + prob
        }

        val random = Random.nextDouble()
        for ((i, move) in moves.withIndex()) {
            if (random < probabilityCumulation[i]) {
                return move
            }
        }

        return null
    }

    companion object {

        private val parsedBooks = ConcurrentHashMap<String, Map<String, List<BookMove>>>()

        private fun parse(file: String): Map<String, List<BookMove>> {
            // Normalise line endings so a CRLF checkout (Windows git autocrlf) doesn't leave a
            // trailing '\r' on each play count, which would break the toInt() below.
            val contents = (OpeningBook::class.java.getResource(file)?.readText() ?: return emptyMap())
                .replace("\r\n", "\n")
                .replace('\r', '\n')

            val movesByPosition = mutableMapOf<String, List<BookMove>>()
            val entries = contents.trim(' ', '\n').split("pos").drop(1)

            for (entry in entries) {
                val entryData = entry.trim('\n').split('\n')
                val fen = entryData[0].trim()
                val movesData = entryData.drop(1)

                val moves = mutableListOf<BookMove>()

                for (moveDataStr in movesData) {
                    val moveData = moveDataStr.split(' ')
                    moves.add(BookMove(moveData[0], moveData[1].toInt()))
                }

                movesByPosition[fen] = moves
            }

            return movesByPosition
        }
    }
}
