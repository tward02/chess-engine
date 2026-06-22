package com.tward.ui.view.game

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
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
import com.tward.engine.game.GameResult
import com.tward.engine.player.BotPlayer
import com.tward.engine.player.HumanPlayer
import com.tward.engine.player.evaluator.PositionalEvaluator
import com.tward.engine.player.evaluator.QuiescenceEvaluator
import com.tward.ui.board.ChessBoardView
import com.tward.ui.board.PieceImage
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
fun BoardView(
    match: ChessMatch,
    showEvaluationBar: Boolean = false,
    showLegalMoves: Boolean = true,
    showResultDialog: Boolean = true,
    onGameOver: ((GameResult) -> Unit)? = null
) {

    val version = match.moveVersion

    var optionalMoves by remember {
        mutableStateOf<List<Move>>(emptyList())
    }

    // Drag-completed promotions place the piece directly, so they must not animate
    var promotionAnimates by remember { mutableStateOf(true) }

    var showEndDialog by remember { mutableStateOf(true) }

    var evaluation by remember { mutableIntStateOf(0) }

    var dragSquare by remember { mutableStateOf<Square?>(null) }
    var dragOffset by remember { mutableStateOf(Offset.Zero) }

    if (showEvaluationBar) {

        // Quiescence search resolves pending captures before scoring, so the bar no longer swings
        // mid-exchange (e.g. during a queen trade); PositionalEvaluator supplies the static score.
        val evaluator = remember { QuiescenceEvaluator(PositionalEvaluator()) }

        LaunchedEffect(version) {

                // Copy on the UI thread before handing off to the background evaluator
            val searchGame = match.game.copy()

            evaluation = withContext(Dispatchers.Default) {
                evaluator.evaluate(searchGame)
            }
        }
    }

    val commitMove = { move: Move, animate: Boolean ->
        match.makeMove(move, animate)
        if (!animate && match.uiState.gameResult == null) {
            playMoveSound(move, match.sideToMoveInCheck())
        }
    }

    val animatingMove = match.animatingMove

    // Keyed on move so animationProgress resets to 0 for each new move; without this the piece
    // briefly flashes at the destination before the animation begins
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

    // Wait for the final move animation to finish before playing the end sound
    LaunchedEffect(match.uiState.gameResult, match.isAnimating) {
        val result = match.uiState.gameResult
        if (result != null && !match.isAnimating) {
            playDoneSound()
            onGameOver?.invoke(result)
        }
    }

    // Poll for timeout so flagging is detected immediately, not on the next move
    LaunchedEffect(Unit) {
        while (match.uiState.gameResult == null) {
            match.checkTimeout()
            delay(100.milliseconds)
        }
    }

    LaunchedEffect(version, animatingMove) {

        if (animatingMove != null) return@LaunchedEffect  // wait for animation to finish

        val activeColour = match.game.board.activeColour
        val currentPlayer =
            if (activeColour == Colour.WHITE) {
                match.whitePlayer
            } else {
                match.blackPlayer
            }

        if (currentPlayer is BotPlayer && match.uiState.gameResult == null) {

            delay(200.milliseconds)

            // Read the clock just before the bot starts so it can budget its search accurately
            val timeLeft = (
                if (activeColour == Colour.WHITE) {
                    match.clockManager.currentWhite()
                } else {
                    match.clockManager.currentBlack()
                }
                ).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()

            val move =
                withContext(Dispatchers.Default) {

                    val searchGame =
                        match.game.copy()

                    currentPlayer.bot.chooseMove(searchGame, timeLeft)
                }

            match.makeMove(move)
        }
    }

    // Destination hidden while the animated piece slides in; drag source hidden while dragging
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

                ChessBoardView(
                    pieceAt = { match.game.board.getPiece(it) },
                    squareSize = SQUARE_SIZE,
                    selected = match.uiState.selectedSquare,
                    lastMoveFrom = match.lastMove?.from,
                    lastMoveTo = match.lastMove?.to,
                    legalTargets = if (showLegalMoves) match.uiState.legalTargets else emptySet(),
                    hiddenSquares = hiddenSquares,
                    clickEnabled = match.uiState.gameResult == null && !match.isAnimating,
                    onSquareClick = { square ->
                        val selected = match.uiState.selectedSquare
                        if (selected == null) {
                            val clickedPiece = match.game.board.getPiece(square)
                            if (clickedPiece != null && clickedPiece.colour == match.game.board.activeColour) {
                                match.select(square)
                            }
                        } else {
                            val moves = match.game.getLegalMoves().filter { it.from == selected && it.to == square }
                            when {
                                moves.size == 1 -> commitMove(moves[0], true)
                                moves.isNotEmpty() -> {
                                    promotionAnimates = true
                                    optionalMoves = moves.filter { it.promotionType != null }
                                }
                                else -> match.clearSelection()
                            }
                        }
                    }
                )

                if (animatingMove != null) {

                    // Captured piece stays visible under the moving piece until it arrives
                    match.animatingCapture?.let { capture ->
                        Box(
                            modifier = Modifier
                                .offset {
                                    IntOffset(
                                        (capture.square.col * SQUARE_SIZE.dp.toPx()).roundToInt(),
                                        (capture.square.row * SQUARE_SIZE.dp.toPx()).roundToInt()
                                    )
                                }
                                .size(SQUARE_SIZE.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            PieceImage(capture.piece)
                        }
                    }

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
                        PieceImage(draggedPiece)
                    }
                }
            }

            if (showEvaluationBar) {
                // Balances the evaluation bar width so the board stays centred
                Spacer(Modifier.width((EVALUATION_BAR_WIDTH + 12).dp))
            }
        }

        Spacer(Modifier.height(12.dp))

        PlayerPanel(match, Colour.WHITE)

        if (match.uiState.gameResult != null && !match.isAnimating && showResultDialog && showEndDialog) {

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
}

// Overlay piece that slides between squares; offset applied at layout time to avoid recomposing the board
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
        PieceImage(piece)
    }
}

@Composable
private fun PlayerPanel(match: ChessMatch, colour: Colour) {

    val captured =
        if (colour == Colour.WHITE) match.capturedByWhite else match.capturedByBlack

    val capturedByOpponent =
        if (colour == Colour.WHITE) match.capturedByBlack else match.capturedByWhite

    val name =
        if (colour == Colour.WHITE) match.whitePlayer.name else match.blackPlayer.name

    val advantage =
        captured.sumOf { it.value() } -
                capturedByOpponent.sumOf { it.value() }

    val isActive =
        match.activeColour == colour && match.uiState.gameResult == null

    Row(
        modifier = Modifier.width(BOARD_SIZE.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        CapturedPieces(captured, advantage)
        ChessClock(match.clockManager, colour, isActive, name)
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

private fun rookCastlingMove(move: Move): Pair<Square, Square>? {

    if (!move.isCastling) return null

    val row = move.to.row

    return if (move.to.col == 6) {
        Square(7, row) to Square(5, row)
    } else {
        Square(0, row) to Square(3, row)
    }
}
