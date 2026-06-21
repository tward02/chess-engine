package com.tward.logging

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.logging.ConsoleHandler
import java.util.logging.Formatter
import java.util.logging.Level
import java.util.logging.LogRecord
import java.util.logging.Logger

/**
 * Installs a concise console handler on the root logger.
 * Call [configure] once at app start; raise to [Level.FINE] for per-move detail.
 */
object LogConfig {

    @Volatile
    private var configured = false

    fun configure(level: Level = Level.INFO) {

        val root = Logger.getLogger("")

        synchronized(this) {
            if (!configured) {
                root.handlers.forEach { root.removeHandler(it) }
                root.addHandler(
                    ConsoleHandler().apply {
                        this.level = Level.ALL
                        formatter = ConciseFormatter()
                    }
                )
                configured = true
            }
        }

        root.level = level
    }

    private class ConciseFormatter : Formatter() {

        private val timeFormat =
            DateTimeFormatter.ofPattern("HH:mm:ss.SSS").withZone(ZoneId.systemDefault())

        override fun format(record: LogRecord): String {

            val time = timeFormat.format(Instant.ofEpochMilli(record.millis))
            val source = record.loggerName?.substringAfterLast('.') ?: "?"

            val base = "$time ${record.level.name.padEnd(7)} [$source] ${formatMessage(record)}\n"

            val throwable = record.thrown ?: return base

            return base + throwable.stackTraceToString()
        }
    }
}
