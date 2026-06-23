package com.tward.ui.board

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Card
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds

/** "m:ss", with tenths shown only in the final minute. Negative time clamps to zero. */
fun formatClock(millis: Long): String {
    val clamped = millis.coerceAtLeast(0)
    val totalSeconds = clamped / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return if (totalSeconds < 60) {
        String.format("%d:%02d.%d", minutes, seconds, (clamped % 1000) / 100)
    } else {
        String.format("%d:%02d", minutes, seconds)
    }
}

/**
 * A clock that ticks down locally so it stays smooth between server updates. [millis] is the latest
 * authoritative snapshot for this side; while [running] it counts down from that snapshot in real
 * time, and it resyncs whenever a new snapshot arrives (keyed on [millis]). Display-only — the server
 * remains the source of truth.
 */
@Composable
fun GameClock(millis: Long, running: Boolean, label: String, modifier: Modifier = Modifier) {
    var display by remember(millis, running) { mutableLongStateOf(millis) }

    LaunchedEffect(millis, running) {
        if (!running) {
            display = millis
            return@LaunchedEffect
        }
        val base = millis
        val start = System.currentTimeMillis()
        while (true) {
            display = (base - (System.currentTimeMillis() - start)).coerceAtLeast(0)
            delay(100.milliseconds)
        }
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = modifier) {
        Card(
            shape = RoundedCornerShape(10.dp),
            backgroundColor = if (running) MaterialTheme.colors.primary else MaterialTheme.colors.surface,
            border = if (running) BorderStroke(2.dp, MaterialTheme.colors.primaryVariant) else null
        ) {
            Text(
                text = formatClock(display),
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                color = when {
                    display < 60_000 -> Color(0xFFE53935)
                    running -> MaterialTheme.colors.onPrimary
                    else -> MaterialTheme.colors.onSurface
                },
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
        }
        Text(label, fontSize = 13.sp, color = MaterialTheme.colors.onBackground.copy(alpha = 0.7f))
    }
}
