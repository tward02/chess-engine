package com.tward.ui.board

import com.tward.engine.board.Board
import com.tward.engine.board.Colour
import com.tward.engine.board.PieceType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CapturedMaterialTest {

    @Test
    fun `starting position has no captures and level material`() {
        val cm = capturedMaterial(Board.getStartingBoard())
        assertTrue(cm.capturedByWhite.isEmpty())
        assertTrue(cm.capturedByBlack.isEmpty())
        assertEquals(0, cm.whiteAdvantage)
    }

    @Test
    fun `a missing black knight is captured by white and gives a +3 advantage`() {
        // Black's g8 knight is gone.
        val board = Board.fromFEN("rnbqkb1r/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1")
        val cm = capturedMaterial(board)

        assertEquals(1, cm.capturedByWhite.count { it.type == PieceType.KNIGHT })
        assertEquals(Colour.BLACK, cm.capturedByWhite.first().colour)
        assertTrue(cm.capturedByBlack.isEmpty())
        assertEquals(3, cm.whiteAdvantage)
    }
}
