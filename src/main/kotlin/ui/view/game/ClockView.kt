package com.tward.ui.view.game

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Card
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tward.engine.board.Colour
import com.tward.ui.model.ClockManager
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds

// The clock polls its own time so the ticking only recomposes this composable
@Composable
fun ChessClock(clockManager: ClockManager, colour: Colour, isActive: Boolean, name: String = "") {

    fun currentMillis() =
        if (colour == Colour.WHITE) {
            clockManager.currentWhite()
        } else {
            clockManager.currentBlack()
        }

    var millis by remember { mutableLongStateOf(currentMillis()) }

    LaunchedEffect(Unit) {
        while (true) {
            millis = currentMillis()
            delay(100.milliseconds)
        }
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        if (colour == Colour.BLACK) {
            Text(
                text = name,
                color = Color(0xFFBBBBBB),
                fontSize = 14.sp
            )
        }

        Card(
            shape = RoundedCornerShape(10.dp),
            backgroundColor = if (isActive) Color(0xFF3A3A3A) else Color(0xFF1E1E1E),
            border = if (isActive) BorderStroke(2.dp, Color(0xFFF0D9B5)) else null
        ) {
            Text(
                text = formatClock(millis),
                modifier = Modifier.padding(
                    horizontal = 20.dp,
                    vertical = 10.dp
                ),
                color = when {
                    millis < 60_000 -> Color(0xFFFF5252)
                    isActive -> Color.White
                    else -> Color(0xFF9E9E9E)
                },
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
        }

        if (colour == Colour.WHITE) {
            Text(
                text = name,
                color = Color(0xFFBBBBBB),
                fontSize = 14.sp
            )
        }

    }
}

fun formatClock(millis: Long): String {

    val clamped = millis.coerceAtLeast(0)
    val totalSeconds = clamped / 1000

    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60

    // Tenths are only worth showing in the final minute
    return if (totalSeconds < 60) {
        String.format("%d:%02d.%d", minutes, seconds, (clamped % 1000) / 100)
    } else {
        String.format("%d:%02d", minutes, seconds)
    }
}
