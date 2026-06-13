package com.tward.logging

import java.util.logging.Level
import java.util.logging.Logger

/**
 * Small Kotlin facade over java.util.logging. Messages are passed as lambdas so that
 * the cost of building a string is only paid when the level is actually enabled — this
 * keeps debug logging cheap on hot paths (e.g. per-move during a headless tournament).
 *
 * Convention used across the app:
 *  - info  : lifecycle and headline events (app start, game over, tournament progress)
 *  - debug : per-move detail (each move, book-move selection, bot search summaries)
 *  - warn / error : unexpected situations
 */
class AppLogger(private val delegate: Logger) {

    val isDebugEnabled: Boolean get() = delegate.isLoggable(Level.FINE)

    fun info(message: () -> String) = log(Level.INFO, null, message)

    fun debug(message: () -> String) = log(Level.FINE, null, message)

    fun warn(throwable: Throwable? = null, message: () -> String) = log(Level.WARNING, throwable, message)

    fun error(throwable: Throwable? = null, message: () -> String) = log(Level.SEVERE, throwable, message)

    private fun log(level: Level, throwable: Throwable?, message: () -> String) {
        if (!delegate.isLoggable(level)) return

        if (throwable != null) {
            delegate.log(level, message(), throwable)
        } else {
            delegate.log(level, message())
        }
    }
}

object Log {

    fun of(name: String): AppLogger = AppLogger(Logger.getLogger(name))

    inline fun <reified T> of(): AppLogger = of(T::class.qualifiedName ?: T::class.java.name)
}
