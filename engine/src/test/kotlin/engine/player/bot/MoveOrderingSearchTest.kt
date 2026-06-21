package engine.player.bot

import com.tward.engine.board.Board
import com.tward.engine.board.Colour
import com.tward.engine.game.ChessGame
import com.tward.engine.player.bot.MiniMaxBot
import com.tward.engine.player.ordering.KillerHistoryMoveOrderer
import com.tward.engine.player.ordering.MvvLvaMoveOrderer
import com.tward.engine.player.ordering.NoOpMoveOrderer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MoveOrderingSearchTest {

    private fun game(fen: String) = ChessGame(Board.fromFEN(fen))

    private fun bot(orderer: com.tward.engine.player.ordering.MoveOrderer, depth: Int) =
        MiniMaxBot(
            depth = depth,
            colour = Colour.WHITE,
            useOpeningBookMoves = false,
            moveOrderer = orderer
        )

    @Test
    fun `ordering searches fewer nodes than no ordering`() {

        // "Kiwipete": a famously tactical position full of captures, where ordering helps a lot
        val fen = "r3k2r/p1ppqpb1/bn2pnp1/3PN3/1p2P3/2N2Q1p/PPPBBPPP/R3K2R w KQkq - 0 1"
        val depth = 3

        val ordered = bot(MvvLvaMoveOrderer(), depth)
        val unordered = bot(NoOpMoveOrderer, depth)

        ordered.chooseMove(game(fen))
        unordered.chooseMove(game(fen))

        assertTrue(
            ordered.nodesSearched < unordered.nodesSearched,
            "MVV-LVA ordering should prune more: ordered=${ordered.nodesSearched}, unordered=${unordered.nodesSearched}"
        )
    }

    @Test
    fun `killer and history ordering prunes at least as much as mvv-lva alone`() {

        // A quiet opening position where the ordering of quiet moves (killers/history) matters,
        // not just captures
        val fen = "r1bqkbnr/pppp1ppp/2n5/4p3/4P3/5N2/PPPP1PPP/RNBQKB1R w KQkq - 0 1"
        val depth = 4

        val killerHistory = bot(KillerHistoryMoveOrderer(), depth)
        val mvvOnly = bot(MvvLvaMoveOrderer(), depth)
        val noOrdering = bot(NoOpMoveOrderer, depth)

        killerHistory.chooseMove(game(fen))
        mvvOnly.chooseMove(game(fen))
        noOrdering.chooseMove(game(fen))

        // Killer/history is MVV-LVA plus extra quiet-move ordering, so it never prunes less
        assertTrue(killerHistory.nodesSearched <= mvvOnly.nodesSearched)
        // And both are far ahead of no ordering at all
        assertTrue(mvvOnly.nodesSearched < noOrdering.nodesSearched)
    }

    @Test
    fun `ordering does not change the chosen move when the best is unique - hanging queen`() {

        val fen = "4k3/8/8/3q4/4P3/8/8/4K3 w - - 0 1"

        val orderedMove = bot(MvvLvaMoveOrderer(), depth = 3).chooseMove(game(fen))
        val unorderedMove = bot(NoOpMoveOrderer, depth = 3).chooseMove(game(fen))

        // Both must capture the hanging queen with the pawn
        assertEquals("e4", orderedMove.from.toString())
        assertEquals("d5", orderedMove.to.toString())
        assertEquals(orderedMove, unorderedMove)
    }

    @Test
    fun `ordering does not change the chosen move when the best is unique - mate in one`() {

        // Back-rank mate: Ra8#
        val fen = "6k1/5ppp/8/8/8/8/8/R3K3 w - - 0 1"

        val orderedMove = bot(MvvLvaMoveOrderer(), depth = 2).chooseMove(game(fen))
        val unorderedMove = bot(NoOpMoveOrderer, depth = 2).chooseMove(game(fen))

        assertEquals(unorderedMove, orderedMove)

        // And it really is mate
        val g = game(fen)
        g.makeMove(orderedMove)
        assertEquals(com.tward.engine.game.GameResult.WHITE_WIN, g.getGameResult())
    }
}
