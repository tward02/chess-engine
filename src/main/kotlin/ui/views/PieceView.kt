package com.tward.ui.views

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.tward.engine.board.Piece

@Composable
fun PieceView(piece: Piece?, size: Int = 40) {

    if (piece != null) {
        Image(
            painter = painterResource("pieces/${piece.resourceName()}"),
            contentDescription = null,
            modifier = Modifier.size(size.dp)
        )
    }
}
