package com.tward.engine.nnue

/**
 * Rate-limited progress lines for the NNUE command-line tools, e.g.
 * `  42% — 1680/4000 games, 12m30s elapsed, ~17m left`. Prints at most one line per
 * [intervalMillis] so a long run stays readable at a glance. These tools report to stdout
 * (not the app's logging facade) because the console is their UI.
 */
internal class ProgressReporter(
    private val total: Int,
    private val unit: String,
    private val intervalMillis: Long = 10_000
) {
    private val startMillis = System.currentTimeMillis()
    private var lastPrintMillis = startMillis

    fun update(done: Int, detail: String = "") {
        val now = System.currentTimeMillis()
        if (done == 0 || now - lastPrintMillis < intervalMillis) return
        lastPrintMillis = now
        val elapsed = now - startMillis
        val remaining = elapsed * (total - done) / done
        println(
            "  ${done * 100 / total}% — $done/$total $unit, " +
                    "${formatDuration(elapsed)} elapsed, ~${formatDuration(remaining)} left$detail"
        )
    }

    fun finish(summary: String) {
        println("$summary in ${formatDuration(System.currentTimeMillis() - startMillis)}")
    }
}

internal fun formatDuration(millis: Long): String {
    val totalSeconds = millis / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return when {
        hours > 0 -> "${hours}h${minutes}m"
        minutes > 0 -> "${minutes}m${seconds}s"
        else -> "${seconds}s"
    }
}
