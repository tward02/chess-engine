package com.tward.engine.player.ordering

import com.tward.engine.board.Move

/**
 * [moves] ordered by [score], highest first, ties kept in their original order: exactly what
 * `sortedByDescending(score)` returns, but each move is scored once and nothing is boxed. A
 * stable insertion sort suits move lists, which are short (~40) and ordered on every search node.
 */
internal inline fun orderByScoreDescending(moves: List<Move>, score: (Move) -> Int): List<Move> {
    val n = moves.size
    val sorted = arrayOfNulls<Move>(n)
    val scores = IntArray(n)
    for (i in 0 until n) {
        val move = moves[i]
        val value = score(move)
        var j = i
        while (j > 0 && scores[j - 1] < value) {
            scores[j] = scores[j - 1]
            sorted[j] = sorted[j - 1]
            j--
        }
        scores[j] = value
        sorted[j] = move
    }
    val result = ArrayList<Move>(n)
    for (move in sorted) result.add(move!!)
    return result
}
