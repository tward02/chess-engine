package com.tward.engine.bench

import com.tward.engine.board.Board
import com.tward.engine.board.Colour
import com.tward.engine.game.ChessGame
import com.tward.engine.player.bot.NegamaxBot
import com.tward.engine.player.bot.OmegaNegamaxBot
import com.tward.engine.player.evaluator.Evaluator
import com.tward.engine.player.evaluator.NnueEvaluator
import com.tward.engine.player.evaluator.nnue.NnueNetwork

/**
 * A fixed-depth search speed benchmark, like Stockfish's `bench`: the same positions searched to the
 * same depth, so the node count is a fingerprint of the search + evaluation (it changes only when
 * their behaviour does) and nodes per second measures the machine. Run it with `java -jar
 * uci-engine.jar bench [depth]` on the VM, or `:engine:bench` locally.
 */
object Benchmark {

    /** Middlegame-heavy positions with a few endgames, so the eval dominates as it does in play. */
    val POSITIONS = listOf(
        "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1",
        "r1bqkb1r/pppp1ppp/2n2n2/4p3/2B1P3/5N2/PPPP1PPP/RNBQK2R w KQkq - 4 4",
        "r2q1rk1/ppp2ppp/2np1n2/2b1p1B1/2B1P1b1/2NP1N2/PPP2PPP/R2Q1RK1 w - - 6 8",
        "r1bq1rk1/pp2bppp/2n1pn2/3p4/2PP4/2N1PN2/PP3PPP/R1BQKB1R w KQ - 0 8",
        "2rq1rk1/pb2bppp/1pn1pn2/2pp4/2PP4/1PN1PNP1/PB2PPBP/R2Q1RK1 w - - 4 11",
        "r3k2r/pp1n1ppp/2p1pn2/q7/1bPP4/2N1PN2/PP1B1PPP/R2QK2R w KQkq - 3 10",
        "3r1rk1/1pq2ppp/p1n1pn2/8/2B5/1PN1PN2/P1Q2PPP/3R1RK1 b - - 0 16",
        "8/5pk1/6p1/3P3p/1p2P2P/1P3KP1/8/8 w - - 0 45",
        "8/8/2k5/5R2/2K5/8/3r4/8 w - - 0 60",
        "6k1/5ppp/8/8/8/8/5PPP/3R2K1 w - - 0 30"
    )

    class Result(val nodes: Long, val millis: Long) {
        val nodesPerSecond: Long get() = nodes * 1000 / maxOf(1L, millis)
        override fun toString() = "$nodes nodes in ${millis}ms: $nodesPerSecond nodes/s"
    }

    /** Searches every position to [depth] with a fresh bot from [bot] (book off), summing nodes. */
    fun run(
        depth: Int = DEFAULT_DEPTH,
        bot: (Colour) -> NegamaxBot = { colour -> omega(colour, depth) },
        report: (String) -> Unit = {}
    ): Result {
        var nodes = 0L
        var millis = 0L
        for ((i, fen) in POSITIONS.withIndex()) {
            val board = Board.fromFEN(fen)
            val searcher = bot(board.activeColour)
            val start = System.nanoTime()
            searcher.chooseMove(ChessGame(board), 0)
            val elapsed = (System.nanoTime() - start) / 1_000_000
            nodes += searcher.nodesSearched
            millis += elapsed
            report("position ${i + 1}/${POSITIONS.size}: ${searcher.nodesSearched} nodes, ${elapsed}ms")
        }
        return Result(nodes, millis)
    }

    fun omega(colour: Colour, depth: Int, evaluator: Evaluator = NnueEvaluator(NnueNetwork.default())) =
        OmegaNegamaxBot(colour, fixedDepth = depth, useOpeningBookMoves = false, evaluator = evaluator)

    /** Evaluations per second of [evaluator] over the benchmark positions (no search). */
    fun evalSpeed(evaluator: Evaluator, millis: Long = 2000): Long {
        val games = POSITIONS.map { ChessGame(Board.fromFEN(it)) }
        val legal = games.map { it.getLegalMoves() }
        var evals = 0L
        var sink = 0
        val end = System.nanoTime() + millis * 1_000_000
        // Make/evaluate/undo each legal move, as the search does at its leaves.
        while (System.nanoTime() < end) {
            for ((g, game) in games.withIndex()) {
                for (move in legal[g]) {
                    game.makeMove(move)
                    sink += evaluator.evaluate(game)
                    game.undoMove(move)
                    evals++
                }
            }
        }
        if (sink == Int.MIN_VALUE) println()   // keeps the JIT from discarding the evaluations
        return evals * 1000 / millis
    }

    const val DEFAULT_DEPTH = 10
}

fun main(args: Array<String>) {
    val depth = args.firstOrNull()?.toIntOrNull() ?: Benchmark.DEFAULT_DEPTH
    println("Warming up...")
    Benchmark.run(depth = minOf(depth, 7))
    println("Eval speed: ${Benchmark.evalSpeed(NnueEvaluator(NnueNetwork.default()))} evals/s (make + eval + undo)")
    println("Benchmark: depth $depth over ${Benchmark.POSITIONS.size} positions")
    val result = Benchmark.run(depth = depth) { println("  $it") }
    println("Total: $result")
}
