package com.tward.engine.player.ordering

import com.tward.engine.board.Move

/**
 * Reorders a node's legal moves so the moves most likely to be best are searched first.
 *
 * This is the reusable contract every alpha-beta bot can depend on. Ordering does NOT change
 * which move minimax considers best, nor its value — it only changes the order moves are
 * visited, which lets alpha-beta produce cutoffs sooner and prune far more of the tree.
 *
 * Some orderers are stateless (e.g. MVV-LVA); others learn during the search (killer / history).
 * The [onBetaCutoff] and [reset] hooks support the stateful ones and default to doing nothing, so
 * stateless implementations can ignore them entirely.
 */
interface MoveOrderer {

    /**
     * Returns [moves] sorted best-first for the node that is [ply] plies from the root.
     * Implementations must return exactly the same moves — never adding, dropping or altering any —
     * so the search still considers every legal move.
     */
    fun order(moves: List<Move>, ply: Int): List<Move>

    /**
     * Tells the orderer that [move] produced a beta cutoff at [ply], with [depth] plies of search
     * still remaining below it. Stateful orderers use this to learn which moves refute positions;
     * stateless ones ignore it.
     */
    fun onBetaCutoff(move: Move, ply: Int, depth: Int) {}

    /** Clears any per-search state. Called once at the start of each root search. */
    fun reset() {}
}

/**
 * Leaves moves in their original order. Useful as a baseline: comparing a search that uses a
 * real orderer against one using this shows exactly how many nodes the ordering saved.
 */
object NoOpMoveOrderer : MoveOrderer {
    override fun order(moves: List<Move>, ply: Int): List<Move> = moves
}
