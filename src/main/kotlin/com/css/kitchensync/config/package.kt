package com.css.kitchensync.config

import com.typesafe.config.Config

// Contains some extension functions to typesafe config

/**
 * Returns a default if the config value isn't present at that path
 */
fun Config.getInt(path: String, default: Int) =
    try { this.getInt(path) } catch (ex: Exception) { default }

fun Config.getLong(path: String, default: Long) =
    try { this.getLong(path) } catch (ex: Exception) { default }