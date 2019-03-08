package com.css.kitchensync.common

import com.beust.klaxon.Json
import com.github.h0tk3y.betterParse.grammar.parseToEnd
import kotlin.random.Random

data class Order(
    val name: String,
    val temp: String,
    val shelfLife: Int,
    val decayRate: Float,
    @Json(name = "orderDecayFormula")
    val valueExpression: String = "shelfLife - (1 + decayRate)*orderAge") {

    val id: String = Random.nextInt().hex()

    companion object {
        val temperatureValues = setOf("hot", "cold", "frozen")
    }

    fun isValid(): Boolean = validateTemp() && validateShelfLife() && validateDecayRate() && validateValueExpression()

    private fun validateTemp() = temp in temperatureValues

    private fun validateShelfLife() = shelfLife >= 0

    private fun validateDecayRate() = decayRate >= 0

    private fun validateValueExpression(): Boolean {
        val expression = valueExpression
            .replace("shelfLife", "$shelfLife")
            .replace("decayRate", "$decayRate")
            .replace("orderAge", "0")
        return try {
            Instances.expressionEvaluator.parseToEnd(expression)
            true
        } catch (ex: Exception) {
            false
        }
    }
}
