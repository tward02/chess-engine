package com.tward.logging

import java.util.logging.Level
import java.util.logging.Logger

/**
 * Kotlin facade over java.util.logging with lazy message evaluation (strings aren't built unless
 * the level is enabled, keeping debug calls cheap on hot paths).
 *
 * Levels: info = lifecycle/headline; debug = per-move detail; warn/error = unexpected.
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
