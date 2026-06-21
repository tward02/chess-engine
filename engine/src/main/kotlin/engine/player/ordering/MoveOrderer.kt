package com.tward.engine.player.ordering

import com.tward.engine.board.Move

/**
 * Reorders legal moves so the most promising are searched first, letting alpha-beta cut sooner.
 * Ordering never changes which move is best — only the traversal order.
 *
 * Stateless orderers (e.g. MVV-LVA) can ignore [onBetaCutoff] and [reset].
 */
interface MoveOrderer {

    /** Returns [moves] sorted best-first for [ply]. Must return the exact same set of moves. */
    fun order(moves: List<Move>, ply: Int): List<Move>

    /** Called when [move] at [ply] caused a beta cutoff with [depth] plies remaining. */
    fun onBetaCutoff(move: Move, ply: Int, depth: Int) {}

    /** Clears per-search state. Called once before each root search. */
    fun reset() {}
}

/** Leaves moves in their original order. Use as a baseline to measure ordering benefit. */
object NoOpMoveOrderer : MoveOrderer {
    override fun order(moves: List<Move>, ply: Int): List<Move> = moves
}
