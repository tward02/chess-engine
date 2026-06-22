package com.tward.ui.board

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.tward.engine.board.Piece

/**
 * Renders a single chess piece from the shared `pieces/` image assets. Null draws nothing.
 * Shared by the desktop game UI and the server test client so piece artwork lives in one place.
 */
@Composable
fun PieceImage(piece: Piece?, size: Int = 40) {
    if (piece != null) {
        Image(
            painter = painterResource("pieces/${piece.resourceName()}"),
            contentDescription = "${piece.colour} ${piece.type}".lowercase(),
            modifier = Modifier.size(size.dp)
        )
    }
}
