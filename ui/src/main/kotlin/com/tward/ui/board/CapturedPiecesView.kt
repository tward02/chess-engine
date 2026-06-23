package com.tward.ui.board

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tward.engine.board.Piece

/**
 * A row of the pieces a player has captured, followed by a "+N" material-advantage badge when ahead.
 * Shared by the desktop game UI and the server test client.
 */
@Composable
fun CapturedPieces(pieces: List<Piece>, advantage: Int, modifier: Modifier = Modifier) {
    val orderedPieces = remember(pieces) { pieces.sortedByDescending { it.value() } }

    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        orderedPieces.forEach { piece -> PieceImage(piece, size = 24) }

        if (advantage > 0) {
            Spacer(Modifier.width(6.dp))
            Text(
                text = "+$advantage",
                color = MaterialTheme.colors.onBackground,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
