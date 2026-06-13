package com.tward.ui.views

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import kotlin.math.pow

const val EVALUATION_BAR_WIDTH = 24

@Composable
fun EvaluationBar(evaluation: Int) {

    val whiteFraction by animateFloatAsState(
        targetValue = evaluationToWhiteFraction(evaluation),
        animationSpec = tween(400)
    )

    Box(
        modifier = Modifier
            .width(EVALUATION_BAR_WIDTH.dp)
            .height(BOARD_SIZE.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(Color(0xFF403D39))
    ) {

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(whiteFraction)
                .align(Alignment.BottomCenter)
                .background(Color(0xFFF0F0F0))
        )

        Text(
            text = formatEvaluation(evaluation),
            modifier = Modifier
                .align(if (evaluation >= 0) Alignment.BottomCenter else Alignment.TopCenter)
                .padding(vertical = 2.dp),
            color = if (evaluation >= 0) Color.Black else Color.White,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

// Maps a centipawn evaluation onto the bar as a win probability so the
// bar fills gradually and never completely disappears for either side
private fun evaluationToWhiteFraction(evaluation: Int): Float {
    val winProbability = 1.0 / (1.0 + 10.0.pow(-evaluation / 400.0))
    return winProbability.toFloat().coerceIn(0.01f, 0.99f)
}

private fun formatEvaluation(evaluation: Int): String {
    return String.format("%.1f", abs(evaluation) / 100.0)
}
