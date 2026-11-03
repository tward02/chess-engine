package engine.openingBook

import com.tward.engine.board.Board
import com.tward.engine.game.ChessGame
import com.tward.engine.openingBook.OpeningBook
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class OpeningBookTest {

    private val book = OpeningBook("/moveBook/Book.txt")

    private fun afterMoves(vararg uci: String): ChessGame {
        val game = ChessGame(Board.getStartingBoard())
        for (move in uci) game.makeMove(game.getLegalMoves().first { it.toAlgebraic() == move })
        return game
    }

    @Test
    fun `book answers after a double pawn push`() {
        // The engine writes "e3" as the en-passant square here; the book stores "-".
        val fen = afterMoves("e2e4").board.toFEN(isFullFEN = false)
        assertTrue(" e3" in fen, "precondition: FEN carries the en-passant square: $fen")
        assertNotNull(book.movesFor(fen), "no book entry for $fen")
        assertNotNull(book.getBookMove(fen))
    }

    @Test
    fun `book follows a main line several moves deep`() {
        val game = afterMoves("e2e4", "c7c5", "g1f3", "d7d6", "d2d4")
        assertTrue(book.hasBookMove(game.board.toFEN(isFullFEN = false)))
    }
}
