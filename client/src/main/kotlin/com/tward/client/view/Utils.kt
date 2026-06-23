package com.tward.client.view

import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.Composable

class Utils {

    companion object {
        @Composable
        fun subtle() = MaterialTheme.colors.onBackground.copy(alpha = 0.6f)
    }

}
