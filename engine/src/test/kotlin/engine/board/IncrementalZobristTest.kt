package engine.board

import com.tward.engine.board.Board
import com.tward.engine.board.Colour
import com.tward.engine.board.ZobristKeys
import com.tward.engine.game.ChessGame
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The incrementally maintained [Board.zobristKey] must always equal a hash recomputed from scratch,
 * through every kind of move (captures, castling, promotions, en passant) and through undo.
 */
class IncrementalZobristTest {

    private fun referenceHash(board: Board): Long {
        var h = 0L
        board.forEachPiece { col, row, piece ->
            h = h xor ZobristKeys.pieceKey(piece, col, row)
        }
        if (board.activeColour == Colour.BLACK) h = h xor ZobristKeys.blackToMove
        if (board.whiteCanCastleKingside) h = h xor ZobristKeys.castling[0]
        if (board.whiteCanCastleQueenside) h = h xor ZobristKeys.castling[1]
        if (board.blackCanCastleKingside) h = h xor ZobristKeys.castling[2]
        if (board.blackCanCastleQueenside) h = h xor ZobristKeys.castling[3]
        board.enPassantTarget?.let { h = h xor ZobristKeys.enPassantFile[it.col] }
        return h
    }

    @Test
    fun `incremental key matches full recompute through random playouts`() {
        val rng = Random(42)
        repeat(20) {
            val game = ChessGame(Board.getStartingBoard())
            assertEquals(referenceHash(game.board), game.board.zobristKey)

            val played = mutableListOf<com.tward.engine.board.Move>()
            for (ply in 0 until 120) {
                val moves = game.getLegalMoves()
                if (moves.isEmpty() || game.getGameResult() != null) break
                val move = moves[rng.nextInt(moves.size)]
                game.makeMove(move)
                played.add(move)
                assertEquals(referenceHash(game.board), game.board.zobristKey, "after ${move.toAlgebraic()} at ply $ply")
            }

            // Undo the whole game; the key must retrace exactly.
            for (move in played.asReversed()) {
                game.undoMove(move)
                assertEquals(referenceHash(game.board), game.board.zobristKey, "undoing ${move.toAlgebraic()}")
            }
        }
    }

    @Test
    fun `fromFEN and copy carry the correct key`() {
        val board = Board.fromFEN("r3k2r/p1ppqpb1/bn2pnp1/3PN3/1p2P3/2N2Q1p/PPPBBPPP/R3K2R w KQkq - 0 1")
        assertEquals(referenceHash(board), board.zobristKey)
        val copy = board.copy()
        assertEquals(board.zobristKey, copy.zobristKey)
    }
}
