package com.css.kitchensync.logging

import java.util.logging.Level
import java.util.logging.Logger

/**
 * Provides new extension methods to [java.util.logging.Logger]
 */
fun Logger.debug(log: String) {
    this.log(Level.FINE, log)
}

/**
 * For lazy string evaluation.
 * Don't evaluate the log message if we don't have to log it
 */
fun Logger.ifDebug(log: () -> String) {
    if(this.isLoggable(Level.FINE)) {
        this.log(Level.FINE, log())
    }
}

fun Logger.info(log: String) {
    this.log(Level.INFO, log)
}

fun Logger.ifInfo(log: () -> String) {
    if(this.isLoggable(Level.INFO)) {
        this.log(Level.INFO, log())
    }
}

fun Logger.warn(log: String) {
    this.log(Level.WARNING, log)
}

fun Logger.error(log: String) {
    this.log(Level.SEVERE, log)
}

fun Logger.error(log: String, ex: Exception) {
    this.log(Level.SEVERE, log, ex)
}
