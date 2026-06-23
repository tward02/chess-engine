package com.tward.ui.board

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Card
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.tward.engine.board.Move
import com.tward.engine.board.Piece
import com.tward.engine.board.PieceType

@Composable
fun PromotionPopupView(promotionMoves: List<Move>, onMoveSelected: (Move) -> Unit) {

    if (promotionMoves.isNotEmpty()) {

        Dialog(onDismissRequest = {}) {

            Card(
                shape = RoundedCornerShape(20.dp),
                elevation = 12.dp,
                backgroundColor = Color(0xFF2B2B2B),
                modifier = Modifier.width(420.dp)
            ) {

                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {

                    Text(
                        text = "Choose Promotion",
                        color = Color.White
                    )

                    Spacer(Modifier.height(20.dp))

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {

                        val orderedMoves =
                            promotionMoves.sortedBy {
                                when (it.promotionType) {
                                    PieceType.QUEEN -> 0
                                    PieceType.ROOK -> 1
                                    PieceType.BISHOP -> 2
                                    PieceType.KNIGHT -> 3
                                    else -> 99
                                }
                            }

                        orderedMoves.forEach { move ->
                            var hovered by remember { mutableStateOf(false) }
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .aspectRatio(1f)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(
                                        if (hovered)
                                            Color(0xFF505050)
                                        else
                                            Color(0xFF3A3A3A)
                                    )
                                    .border(
                                        2.dp,
                                        Color(0xFF606060),
                                        RoundedCornerShape(12.dp)
                                    )
                                    .clickable {
                                        onMoveSelected(move)
                                    },
                                contentAlignment = Alignment.Center
                            ) {

                                move.promotionType?.let { type ->

                                    PieceImage(
                                        Piece(
                                            type,
                                            move.piece!!.colour
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
