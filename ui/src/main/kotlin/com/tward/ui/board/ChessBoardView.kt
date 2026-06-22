package com.tward.ui.board

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.tward.engine.board.Colour
import com.tward.engine.board.Piece
import com.tward.engine.board.Square
import com.tward.engine.board.SquareType

const val DEFAULT_SQUARE_SIZE = 80

/**
 * A model-agnostic 8x8 chess board. It knows nothing about games, clocks, bots or networking — it
 * just draws the squares and whatever pieces [pieceAt] reports, with optional highlights, and calls
 * [onSquareClick]. Both the desktop game UI (which layers drag/animation overlays on top) and the
 * server test client render through this, so board styling and click handling live in one place.
 *
 * @param orientation which colour sits at the bottom (so each player sees their own pieces nearest).
 * @param hiddenSquares squares whose piece is not drawn (the desktop UI hides a piece while it slides
 *   or is dragged, and renders a moving copy on top).
 */
@Composable
fun ChessBoardView(
    pieceAt: (Square) -> Piece?,
    modifier: Modifier = Modifier,
    orientation: Colour = Colour.WHITE,
    squareSize: Int = DEFAULT_SQUARE_SIZE,
    selected: Square? = null,
    lastMoveFrom: Square? = null,
    lastMoveTo: Square? = null,
    legalTargets: Set<Square> = emptySet(),
    hiddenSquares: Set<Square> = emptySet(),
    clickEnabled: Boolean = true,
    onSquareClick: (Square) -> Unit = {}
) {
    val rows = if (orientation == Colour.WHITE) (0..7) else (7 downTo 0)
    val cols = if (orientation == Colour.WHITE) (0..7) else (7 downTo 0)

    Column(modifier) {
        for (row in rows) {
            Row {
                for (col in cols) {
                    val square = Square(col, row)
                    val light = square.getSquareType() == SquareType.LIGHT
                    val background = when {
                        square == selected -> Color.Yellow
                        square == lastMoveFrom || square == lastMoveTo ->
                            if (light) Color(0xFFCDD26A) else Color(0xFFAAA23A)
                        light -> Color(0xFFF0D9B5)
                        else -> Color(0xFFB58863)
                    }

                    Box(
                        modifier = Modifier
                            .size(squareSize.dp)
                            .testTag(square.toString())
                            .background(background)
                            .clickable(enabled = clickEnabled) { onSquareClick(square) },
                        contentAlignment = Alignment.Center
                    ) {
                        if (square in legalTargets) {
                            Box(
                                modifier = Modifier
                                    .size((squareSize * 0.5f).dp)
                                    .background(Color.Gray.copy(alpha = 0.5f), CircleShape)
                            )
                        }
                        if (square !in hiddenSquares) {
                            PieceImage(pieceAt(square), squareSize / 2)
                        }
                    }
                }
            }
        }
    }
}
