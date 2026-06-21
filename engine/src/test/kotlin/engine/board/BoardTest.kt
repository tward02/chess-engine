package engine.board

import com.tward.engine.board.Board
import com.tward.engine.board.Colour
import com.tward.engine.board.Move
import com.tward.engine.board.Piece
import com.tward.engine.board.PieceType
import com.tward.engine.board.Square
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class BoardTest {

    @Test
    fun `new board is empty`() {

        val board = Board()

        for (row in 0..7) {
            for (col in 0..7) {
                assertNull(
                    board.getPiece(Square(col, row))
                )
            }
        }
    }

    @Test
    fun `starting board has 32 pieces`() {

        val board = Board.getStartingBoard()

        assertEquals(
            32,
            board.getPieces().size
        )
    }

    @Test
    fun `starting board active colour is white`() {

        val board = Board.getStartingBoard()

        assertEquals(
            Colour.WHITE,
            board.activeColour
        )
    }

    @Test
    fun `set piece stores piece`() {

        val board = Board()

        val square = Square.fromString("e4")
        val piece = Piece(PieceType.QUEEN, Colour.WHITE)

        board.setPiece(square, piece)

        assertEquals(
            piece,
            board.getPiece(square)
        )
    }

    @Test
    fun `set null removes piece`() {

        val board = Board()

        val square = Square.fromString("e4")

        board.setPiece(
            square,
            Piece(PieceType.ROOK, Colour.WHITE)
        )

        board.setPiece(square, null)

        assertNull(
            board.getPiece(square)
        )
    }

    @Test
    fun `white king starts on e1`() {

        val board = Board.getStartingBoard()

        assertEquals(
            Piece(PieceType.KING, Colour.WHITE),
            board.getPiece(Square.fromString("e1"))
        )
    }

    @Test
    fun `black king starts on e8`() {

        val board = Board.getStartingBoard()

        assertEquals(
            Piece(PieceType.KING, Colour.BLACK),
            board.getPiece(Square.fromString("e8"))
        )
    }

    @Test
    fun `white pawns start on rank 2`() {

        val board = Board.getStartingBoard()

        for (file in 'a'..'h') {

            assertEquals(
                Piece(PieceType.PAWN, Colour.WHITE),
                board.getPiece(
                    Square.fromString("${file}2")
                )
            )
        }
    }

    @Test
    fun `copy creates independent board`() {

        val board = Board.getStartingBoard()

        val copy = board.copy()

        copy.setPiece(
            Square.fromString("e2"),
            null
        )

        assertNotNull(
            board.getPiece(
                Square.fromString("e2")
            )
        )
    }

    @Test
    fun `copy preserves state`() {

        val board = Board()

        board.activeColour = Colour.BLACK
        board.enPassantTarget = Square.fromString("e3")
        board.halfMoveClock = 12
        board.fullMoveNumber = 34

        val copy = board.copy()

        assertEquals(
            board.activeColour,
            copy.activeColour
        )

        assertEquals(
            board.enPassantTarget,
            copy.enPassantTarget
        )

        assertEquals(
            board.halfMoveClock,
            copy.halfMoveClock
        )

        assertEquals(
            board.fullMoveNumber,
            copy.fullMoveNumber
        )
    }

    @Test
    fun `starting position fen`() {

        val board = Board.getStartingBoard()

        assertEquals(
            "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 0",
            board.toFEN()
        )
    }

    @Test
    fun `fen round trip`() {

        val board = Board.getStartingBoard()

        val fen = board.toFEN()

        val loaded = Board.fromFEN(fen)

        assertEquals(
            fen,
            loaded.toFEN()
        )
    }

    @Test
    fun `empty board fen round trip`() {

        val fen = "8/8/8/8/8/8/8/8 w - - 0 0"

        val loaded = Board.fromFEN(fen)

        assertEquals(
            fen,
            loaded.toFEN()
        )
    }

    @Test
    fun `find white king`() {

        val board = Board.getStartingBoard()

        assertEquals(
            Square.fromString("e1"),
            board.findKing(Colour.WHITE)
        )
    }

    @Test
    fun `find king throws when missing`() {

        val board = Board()

        assertFailsWith<IllegalStateException> {
            board.findKing(Colour.WHITE)
        }
    }

    @Test
    fun `find piece returns location`() {

        val board = Board.getStartingBoard()

        assertEquals(
            Square.fromString("e1"),
            board.findPiece(
                Piece(PieceType.KING, Colour.WHITE)
            )
        )
    }

    @Test
    fun `find piece returns null when absent`() {

        val board = Board()

        assertNull(
            board.findPiece(
                Piece(PieceType.QUEEN, Colour.WHITE)
            )
        )
    }

    @Test
    fun `make move moves piece`() {

        val board = Board.getStartingBoard()

        val move = Move(
            Square.fromString("e2"),
            Square.fromString("e4")
        )

        board.makeMove(move)

        assertNull(
            board.getPiece(
                Square.fromString("e2")
            )
        )

        assertEquals(
            Piece(PieceType.PAWN, Colour.WHITE),
            board.getPiece(
                Square.fromString("e4")
            )
        )
    }

    @Test
    fun `undo move restores board`() {

        val board = Board.getStartingBoard()

        val before = board.toFEN()

        val move = Move(
            Square.fromString("e2"),
            Square.fromString("e4")
        )

        board.makeMove(move)
        board.undoMove(move)

        assertEquals(
            before,
            board.toFEN()
        )
    }

    @Test
    fun `moving king removes castling rights`() {

        val board = Board.fromFEN(
            "4k3/8/8/8/8/8/8/4K3 w KQkq - 0 1"
        )

        board.makeMove(
            Move(
                Square.fromString("e1"),
                Square.fromString("e2")
            )
        )

        assertFalse(board.whiteCanCastleKingside)
        assertFalse(board.whiteCanCastleQueenside)
    }

    @Test
    fun `double pawn push sets en passant target`() {

        val board = Board.getStartingBoard()

        board.makeMove(
            Move(
                Square.fromString("e2"),
                Square.fromString("e4")
            )
        )

        assertEquals(
            Square.fromString("e3"),
            board.enPassantTarget
        )
    }
}
