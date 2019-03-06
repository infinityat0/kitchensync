package com.css.kitchensync.duration

import java.time.Duration

// Some extension functions for Int and Long to convert them into durations

/**
 * Create duration out of an integer ex: 2.seconds
 */
fun Int.seconds(): Duration = Duration.ofSeconds(this.toLong())

fun Long.seconds(): Duration = Duration.ofSeconds(this)

fun Long.milliseconds(): Duration = Duration.ofMillis(this)