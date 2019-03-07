package com.css.kitchensync.common

import com.beust.klaxon.Klaxon

// Common extension functions
/**
 * Makes the integer a hex string
 */
fun Int.hex(): String = "%08X".format(this)

// Common instances that can be shared inside the package
object Instances {
    val expressionEvaluator = ExpressionEvaluator()
    val jsonParser = Klaxon()
}
