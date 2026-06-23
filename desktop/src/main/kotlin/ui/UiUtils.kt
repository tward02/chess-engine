package com.tward.ui

import com.tward.engine.board.Move
import com.tward.ui.board.Sounds

fun playMoveSound(move: Move, isCheck: Boolean) =
    Sounds.playMove(isCapture = move.capturedPiece != null, isCheck = isCheck)

fun playDoneSound() = Sounds.playGameOver()
