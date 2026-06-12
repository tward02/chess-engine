package com.tward.ui.views

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tward.engine.board.Piece

@Composable
fun CapturedPieces(pieces: List<Piece>, advantage: Int) {

    val orderedPieces = remember(pieces) {
        pieces.sortedByDescending { it.value() }
    }

    Row(verticalAlignment = Alignment.CenterVertically) {

        orderedPieces.forEach { piece ->
            PieceView(piece, size = 26)
        }

        if (advantage > 0) {
            Spacer(Modifier.width(6.dp))
            Text(
                text = "+$advantage",
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
