package com.tward.ui.views

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.AlertDialog
import androidx.compose.material.Card
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.tward.engine.board.*
import com.tward.engine.player.BotPlayer
import com.tward.engine.player.HumanPlayer
import com.tward.engine.player.evaluator.StandardEvaluator
import com.tward.ui.model.ChessMatch
import com.tward.ui.playDoneSound
import com.tward.ui.playMoveSound
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.milliseconds

private const val SQUARE_SIZE = 80
const val BOARD_SIZE = SQUARE_SIZE * 8
private const val MOVE_ANIMATION_MILLIS = 200

@Composable
fun BoardView(match: ChessMatch, showEvaluationBar: Boolean = false, showLegalMoves: Boolean = true) {

    val version = match.moveVersion

    var optionalMoves by remember {
        mutableStateOf<List<Move>>(emptyList())
    }

    // A drag completing a promotion places the piece directly, so it should not animate
    var promotionAnimates by remember { mutableStateOf(true) }

    var showEndDialog by remember { mutableStateOf(true) }

    var evaluation by remember { mutableIntStateOf(0) }

    // The square currently being dragged from, and the live pointer position on the board
    var dragSquare by remember { mutableStateOf<Square?>(null) }
    var dragOffset by remember { mutableStateOf(Offset.Zero) }

    if (showEvaluationBar) {

        val evaluator = remember { StandardEvaluator() }

        LaunchedEffect(version) {

            // Copy on the UI thread so the evaluation can run safely in the background
            val searchGame = match.game.copy()

            evaluation = withContext(Dispatchers.Default) {
                evaluator.evaluate(searchGame)
            }
        }
    }

    // Commits a move and plays its sound. Animated moves play their sound when the
    // piece lands (see the animation effect), so only non-animated moves sound here
    val commitMove = { move: Move, animate: Boolean ->
        match.makeMove(move, animate)
        if (!animate && match.uiState.gameResult == null) {
            playMoveSound(move, match.sideToMoveInCheck())
        }
    }

    val animatingMove = match.animatingMove

    // Keyed on the move so each animation starts at 0 on its very first frame,
    // otherwise the piece briefly flashes at the target square
    val animationProgress = remember(animatingMove) { Animatable(0f) }

    LaunchedEffect(animatingMove) {
        if (animatingMove != null) {
            animationProgress.animateTo(1f, tween(MOVE_ANIMATION_MILLIS))
            if (match.uiState.gameResult == null) {
                playMoveSound(animatingMove, match.sideToMoveInCheck())
            }
            match.onAnimationFinished()
        }
    }

    // Played once the game ends, after the final move has finished animating
    LaunchedEffect(match.uiState.gameResult, match.isAnimating) {
        if (match.uiState.gameResult != null && !match.isAnimating) {
            playDoneSound()
        }
    }

    LaunchedEffect(version, animatingMove) {

        // The next player (bot or human) may not move until the animation has finished
        if (animatingMove != null) return@LaunchedEffect

        val currentPlayer =
            if (match.game.board.activeColour == Colour.WHITE) {
                match.whitePlayer
            } else {
                match.blackPlayer
            }

        if (currentPlayer is BotPlayer && match.uiState.gameResult == null) {

            delay(200.milliseconds)

            val move =
                withContext(Dispatchers.Default) {

                    val searchGame =
                        match.game.copy()

                    currentPlayer.bot.chooseMove(searchGame)
                }

            match.makeMove(move)
        }
    }

    // Destination squares are hidden while their piece slides in via the overlay,
    // and the drag source is hidden while its piece follows the cursor
    val hiddenSquares = buildSet {
        if (animatingMove != null) {
            add(animatingMove.to)
            rookCastlingMove(animatingMove)?.let { add(it.second) }
        }
        dragSquare?.let { add(it) }
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.fillMaxSize().background(
            Color(0xff2d6e1f)
        )
    ) {

        PlayerPanel(match, Colour.BLACK)

        Spacer(Modifier.height(12.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {

            if (showEvaluationBar) {
                EvaluationBar(evaluation)
                Spacer(Modifier.width(12.dp))
            }

            Box(
                modifier = Modifier.pointerInput(match) {

                    val squarePx = SQUARE_SIZE.dp.toPx()

                    fun squareAt(offset: Offset): Square? {
                        val col = (offset.x / squarePx).toInt()
                        val row = (offset.y / squarePx).toInt()
                        return if (col in 0..7 && row in 0..7) Square(col, row) else null
                    }

                    detectDragGestures(
                        onDragStart = { offset ->
                            if (match.uiState.gameResult == null && !match.isAnimating && currentPlayerIsHuman(match)) {

                                val square = squareAt(offset)
                                val piece = square?.let { match.game.board.getPiece(it) }

                                if (square != null && piece != null &&
                                    piece.colour == match.game.board.activeColour
                                ) {
                                    dragSquare = square
                                    dragOffset = offset
                                    match.select(square)
                                }
                            }
                        },
                        onDrag = { change, amount ->
                            if (dragSquare != null) {
                                change.consume()
                                dragOffset += amount
                            }
                        },
                        onDragEnd = {
                            val from = dragSquare
                            val target = squareAt(dragOffset)
                            dragSquare = null

                            val moves =
                                if (from != null && target != null && from != target) {
                                    match.game.getLegalMoves()
                                        .filter { it.from == from && it.to == target }
                                } else {
                                    emptyList()
                                }

                            when {
                                moves.size == 1 -> commitMove(moves[0], false)
                                moves.isNotEmpty() -> {
                                    promotionAnimates = false
                                    optionalMoves = moves.filter { it.promotionType != null }
                                }

                                else -> match.clearSelection()
                            }
                        },
                        onDragCancel = {
                            dragSquare = null
                            match.clearSelection()
                        }
                    )
                }
            ) {

                Column {
                    for (row in 0..7) {

                        Row {

                            for (col in 0..7) {

                                val square = Square(col, row)
                                val piece = match.game.board.getPiece(square)
                                val isSelected =
                                    match.uiState.selectedSquare == square

                                val isLegalTarget = showLegalMoves &&
                                    square in match.uiState.legalTargets

                                val type = square.getSquareType()

                                Box(
                                    modifier = Modifier
                                        .size(SQUARE_SIZE.dp)
                                        .background(
                                            when {
                                                isSelected -> Color.Yellow
                                                type == SquareType.LIGHT ->
                                                    Color(0xFFF0D9B5)

                                                else ->
                                                    Color(0xFFB58863)
                                            }
                                        ).clickable(
                                            enabled = match.uiState.gameResult == null && !match.isAnimating
                                        ) {
                                            val selected =
                                                match.uiState.selectedSquare

                                            if (selected == null) {

                                                val clickedPiece =
                                                    match.game.board.getPiece(square)

                                                if (
                                                    clickedPiece != null &&
                                                    clickedPiece.colour ==
                                                    match.game.board.activeColour
                                                ) {
                                                    match.select(square)
                                                }

                                            } else {

                                                val moves =
                                                    match.game
                                                        .getLegalMoves()
                                                        .filter {
                                                            it.from == selected &&
                                                                    it.to == square
                                                        }


                                                if (moves.size == 1) {
                                                    commitMove(moves[0], true)
                                                } else {
                                                    if (moves.isNotEmpty()) {
                                                        promotionAnimates = true
                                                        optionalMoves = moves.filter { it.promotionType != null }
                                                    } else {
                                                        match.clearSelection()
                                                    }
                                                }
                                            }

                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (isLegalTarget) {
                                        Box(
                                            modifier = Modifier
                                                .size((SQUARE_SIZE * 0.5f).dp)
                                                .background(
                                                    Color.Gray.copy(alpha = 0.5f),
                                                    shape = CircleShape
                                                )
                                        )
                                    }

                                    if (square !in hiddenSquares) {
                                        PieceView(piece)
                                    }
                                }
                            }
                        }
                    }
                }

                if (animatingMove != null) {

                    MovingPiece(
                        piece = match.game.board.getPiece(animatingMove.to),
                        from = animatingMove.from,
                        to = animatingMove.to,
                        progress = { animationProgress.value }
                    )

                    rookCastlingMove(animatingMove)?.let { (rookFrom, rookTo) ->
                        MovingPiece(
                            piece = match.game.board.getPiece(rookTo),
                            from = rookFrom,
                            to = rookTo,
                            progress = { animationProgress.value }
                        )
                    }
                }

                // The dragged piece floats under the cursor, centred on the pointer
                dragSquare?.let { square ->
                    val draggedPiece = match.game.board.getPiece(square)
                    Box(
                        modifier = Modifier
                            .offset {
                                IntOffset(
                                    (dragOffset.x - SQUARE_SIZE.dp.toPx() / 2).roundToInt(),
                                    (dragOffset.y - SQUARE_SIZE.dp.toPx() / 2).roundToInt()
                                )
                            }
                            .size(SQUARE_SIZE.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        PieceView(draggedPiece)
                    }
                }
            }

            if (showEvaluationBar) {
                // Balance the bar so the board stays centred beneath the player panels
                Spacer(Modifier.width((EVALUATION_BAR_WIDTH + 12).dp))
            }
        }

        Spacer(Modifier.height(12.dp))

        PlayerPanel(match, Colour.WHITE)

        if (match.uiState.gameResult != null && !match.isAnimating && showEndDialog) {

            AlertDialog(
                onDismissRequest = { },
                title = {
                    Text(text = if (match.uiState.gameResult?.isDraw() ?: false) "Draw" else "Game Over")
                },
                text = {
                    Text(text = match.uiState.gameResult?.toString() ?: "")
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showEndDialog = false
                        }
                    ) {
                        Text("Close Game")
                    }
                })
        }

        if (optionalMoves.isNotEmpty()) {

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
                                optionalMoves.sortedBy {
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
                                            val chosen = move
                                            optionalMoves = emptyList()
                                            commitMove(chosen, promotionAnimates)
                                        },
                                    contentAlignment = Alignment.Center
                                ) {

                                    move.promotionType?.let { type ->

                                        PieceView(
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
}

// Overlay piece sliding from one square to another, offset is applied at layout
// time so the animation never recomposes the board
@Composable
private fun MovingPiece(
    piece: Piece?,
    from: Square,
    to: Square,
    progress: () -> Float
) {
    Box(
        modifier = Modifier
            .offset {
                val fraction = progress()
                IntOffset(
                    (lerp(from.col, to.col, fraction) * SQUARE_SIZE.dp.toPx()).roundToInt(),
                    (lerp(from.row, to.row, fraction) * SQUARE_SIZE.dp.toPx()).roundToInt()
                )
            }
            .size(SQUARE_SIZE.dp),
        contentAlignment = Alignment.Center
    ) {
        PieceView(piece)
    }
}

@Composable
private fun PlayerPanel(match: ChessMatch, colour: Colour) {

    val captured =
        if (colour == Colour.WHITE) match.capturedByWhite else match.capturedByBlack

    val capturedByOpponent =
        if (colour == Colour.WHITE) match.capturedByBlack else match.capturedByWhite

    val advantage =
        captured.sumOf { it.value() } -
                capturedByOpponent.sumOf { it.value() }

    val isActive =
        match.game.board.activeColour == colour && match.uiState.gameResult == null

    Row(
        modifier = Modifier.width(BOARD_SIZE.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        CapturedPieces(captured, advantage)
        ChessClock(match.clockManager, colour, isActive)
    }
}

private fun lerp(from: Int, to: Int, fraction: Float): Float {
    return from + (to - from) * fraction
}

private fun currentPlayerIsHuman(match: ChessMatch): Boolean {
    val player =
        if (match.game.board.activeColour == Colour.WHITE) match.whitePlayer else match.blackPlayer
    return player is HumanPlayer
}

// The from and to squares of the rook's part of a castling move
private fun rookCastlingMove(move: Move): Pair<Square, Square>? {

    if (!move.isCastling) return null

    val row = move.to.row

    return if (move.to.col == 6) {
        Square(7, row) to Square(5, row)
    } else {
        Square(0, row) to Square(3, row)
    }
}
