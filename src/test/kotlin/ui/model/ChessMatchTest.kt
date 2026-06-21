package ui.model

import com.tward.engine.board.Board
import com.tward.engine.board.Colour
import com.tward.engine.board.PieceType
import com.tward.engine.board.Square
import com.tward.engine.game.ChessGame
import com.tward.engine.game.GameResult
import com.tward.engine.player.HumanPlayer
import com.tward.ui.model.ChessMatch
import com.tward.ui.model.ClockManager
import com.tward.ui.model.TimeControl
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ChessMatchTest {

    @Test
    fun `select piece updates ui state`() {

        val match = createMatch()

        match.select(Square(4, 6))

        assertEquals(
            Square(4, 6),
            match.uiState.selectedSquare
        )

        assertTrue(
            Square(4, 5) in match.uiState.legalTargets
        )

        assertTrue(
            Square(4, 4) in match.uiState.legalTargets
        )
    }

    @Test
    fun `select empty square produces no legal targets`() {

        val match = createMatch()

        match.select(Square(4, 4))

        assertEquals(
            Square(4, 4),
            match.uiState.selectedSquare
        )

        assertTrue(
            match.uiState.legalTargets.isEmpty()
        )
    }

    @Test
    fun `clear selection resets ui state`() {

        val match = createMatch()

        match.select(Square(4, 6))

        match.clearSelection()

        assertNull(match.uiState.selectedSquare)

        assertTrue(
            match.uiState.legalTargets.isEmpty()
        )

        assertNull(match.uiState.gameResult)
    }

    @Test
    fun `move increments version`() {

        val match = createMatch()

        val before = match.moveVersion

        match.makeMove(
            match.game.findMove("e2", "e4")!!
        )

        assertEquals(
            before + 1,
            match.moveVersion
        )
    }

    @Test
    fun `move clears selection`() {

        val match = createMatch()

        match.select(Square(4, 6))

        match.makeMove(
            match.game.findMove("e2", "e4")!!
        )

        assertNull(match.uiState.selectedSquare)

        assertTrue(
            match.uiState.legalTargets.isEmpty()
        )
    }

    @Test
    fun `last move is null before any move and tracks the from and to squares after`() {

        val match = createMatch()
        assertNull(match.lastMove)

        match.makeMove(match.game.findMove("e2", "e4")!!)

        assertEquals(Square(4, 6), match.lastMove?.from)
        assertEquals(Square(4, 4), match.lastMove?.to)
    }

    @Test
    fun `move updates board position`() {

        val match = createMatch()

        match.makeMove(
            match.game.findMove("e2", "e4")!!
        )

        assertNull(
            match.game.board.getPiece(
                Square(4, 6)
            )
        )

        assertNotNull(
            match.game.board.getPiece(
                Square(4, 4)
            )
        )
    }

    @Test
    fun `cannot select when game is over`() {

        val match = createMatch()

        match.game.result =
            GameResult.WHITE_WIN

        match.select(Square(4, 6))

        assertNull(
            match.uiState.selectedSquare
        )
    }

    @Test
    fun `cannot make move when game is over`() {

        val match = createMatch()

        match.game.result =
            GameResult.WHITE_WIN

        val versionBefore =
            match.moveVersion

        match.makeMove(
            match.game.findMove("e2", "e4")!!
        )

        assertEquals(
            versionBefore,
            match.moveVersion
        )
    }

    @Test
    fun `check game over updates ui result`() {

        val match = createMatch()

        match.game.result =
            GameResult.DRAW_STALEMATE

        match.checkGameOver()

        assertEquals(
            GameResult.DRAW_STALEMATE,
            match.uiState.gameResult
        )
    }

    @Test
    fun `white timeout gives black win`() {

        val match = createMatch()

        match.clockManager.whiteMillis = 0

        match.checkGameOver()

        assertEquals(
            GameResult.BLACK_TIME_WIN,
            match.game.result
        )
    }

    @Test
    fun `black timeout gives white win`() {

        val match = createMatch()

        match.clockManager.blackMillis = 0

        match.checkGameOver()

        assertEquals(
            GameResult.WHITE_TIME_WIN,
            match.game.result
        )
    }

    @Test
    fun `live timeout ends the game when white flags while thinking`() {

        var now = 0L
        val clock = ClockManager(TimeControl(initialMillis = 1_000, incrementMillis = 0), now = { now })
        val match = matchWithClock(clock)

        // White is to move; let real time pass its 1s budget without a move being played
        now = 1_500
        match.checkTimeout()

        assertEquals(GameResult.BLACK_TIME_WIN, match.game.result)
        assertEquals(GameResult.BLACK_TIME_WIN, match.uiState.gameResult)
    }

    @Test
    fun `live timeout ends the game when black flags while thinking`() {

        var now = 0L
        val clock = ClockManager(
            TimeControl(initialMillis = 1_000, incrementMillis = 0),
            activeColor = Colour.BLACK,
            now = { now }
        )
        val match = matchWithClock(clock)

        now = 1_500
        match.checkTimeout()

        assertEquals(GameResult.WHITE_TIME_WIN, match.game.result)
    }

    @Test
    fun `no timeout while the active player still has time`() {

        var now = 0L
        val clock = ClockManager(TimeControl(initialMillis = 1_000, incrementMillis = 0), now = { now })
        val match = matchWithClock(clock)

        now = 500
        match.checkTimeout()

        assertNull(match.game.result)
        assertNull(match.uiState.gameResult)
    }

    @Test
    fun `fools mate propagates result into ui`() {

        val match = createMatch()

        match.makeMove(match.game.findMove("f2", "f3")!!)
        match.makeMove(match.game.findMove("e7", "e5")!!)

        match.makeMove(match.game.findMove("g2", "g4")!!)
        match.makeMove(match.game.findMove("d8", "h4")!!)

        assertEquals(
            GameResult.BLACK_WIN,
            match.game.result
        )

        assertEquals(
            GameResult.BLACK_WIN,
            match.uiState.gameResult
        )
    }

    @Test
    fun `capture keeps the captured piece on its square for the animation`() {

        // White pawn on e4 captures the black pawn on d5
        val match = matchFromFen("4k3/8/8/3p4/4P3/8/8/4K3 w - - 0 1")

        match.makeMove(match.game.findMove("e4", "d5")!!)

        val capture = match.animatingCapture
        assertNotNull(capture)
        assertEquals(Square.fromString("d5"), capture.square)
        assertEquals(PieceType.PAWN, capture.piece.type)
        assertEquals(Colour.BLACK, capture.piece.colour)
    }

    @Test
    fun `en passant keeps the captured pawn on its own square not the destination`() {

        // White pawn e5 captures en passant onto d6; the captured pawn sits on d5
        val match = matchFromFen("4k3/8/8/3pP3/8/8/8/4K3 w - d6 0 1")

        match.makeMove(match.game.findMove("e5", "d6")!!)

        val capture = match.animatingCapture
        assertNotNull(capture)
        assertEquals(Square.fromString("d5"), capture.square)
    }

    @Test
    fun `active colour flips after a move`() {

        val match = createMatch()

        assertEquals(Colour.WHITE, match.activeColour)

        match.makeMove(match.game.findMove("e2", "e4")!!)

        assertEquals(Colour.BLACK, match.activeColour)
    }

    @Test
    fun `quiet move has no animating capture`() {

        val match = createMatch()

        match.makeMove(match.game.findMove("e2", "e4")!!)

        assertNull(match.animatingCapture)
    }

    @Test
    fun `non-animated capture has no animating capture`() {

        val match = matchFromFen("4k3/8/8/3p4/4P3/8/8/4K3 w - - 0 1")

        match.makeMove(match.game.findMove("e4", "d5")!!, animate = false)

        assertNull(match.animatingCapture)
    }

    @Test
    fun `animation finished clears the animating capture`() {

        val match = matchFromFen("4k3/8/8/3p4/4P3/8/8/4K3 w - - 0 1")

        match.makeMove(match.game.findMove("e4", "d5")!!)
        assertNotNull(match.animatingCapture)

        match.onAnimationFinished()

        assertNull(match.animatingCapture)
    }

    private fun createMatch(): ChessMatch {
        return matchFromFen(null)
    }

    private fun matchWithClock(clock: ClockManager): ChessMatch {
        return ChessMatch(
            game = ChessGame(Board.getStartingBoard()),
            whitePlayer = HumanPlayer("test"),
            blackPlayer = HumanPlayer("test"),
            clockManager = clock
        )
    }

    private fun matchFromFen(fen: String?): ChessMatch {
        val board = if (fen == null) Board.getStartingBoard() else Board.fromFEN(fen)
        return ChessMatch(
            game = ChessGame(board),
            whitePlayer = HumanPlayer("test"),
            blackPlayer = HumanPlayer("test"),
            clockManager = ClockManager(
                TimeControl(
                    initialMillis = 300_000,
                    incrementMillis = 0
                )
            )
        )
    }
}
