package com.css.kitchensync.common

import com.css.kitchensync.logging.error
import com.github.h0tk3y.betterParse.grammar.parseToEnd
import com.google.common.annotations.VisibleForTesting
import java.util.logging.Logger
import kotlin.random.Random

data class PreparedOrder(
    val name: String,
    val temp: String,
    val shelfLife: Int,
    val decayRate: Float,
    val id: Int = Random.nextInt(),
    val valueExpression: String = "shelfLife - (1 + decayRate)*orderAge") {

    companion object {
        val logger: Logger = Logger.getLogger("PreparedOrder")

        // Generate prepared order from order request
        fun fromOrderRequest(order: Order): PreparedOrder {
            return PreparedOrder(
                id = order.id,
                name = order.name,
                temp = order.temp,
                shelfLife = order.shelfLife,
                decayRate = order.decayRate,
                valueExpression = order.valueExpression
            )
        }
    }
    // Should be updatable later since the time we create an instance may not
    // be the time we actually send the order
    val preparedAt: Long = System.currentTimeMillis()

    // Initial value should coincide with the shelfLife of the order
    // At order created time, value is shelf value
    @VisibleForTesting var valueAtLastMeasured: Float = shelfLife.toFloat()
    @VisibleForTesting var lastMeasuredTimestamp: Long = preparedAt

    @Synchronized fun computeAndAssignValue(computeTimeInMs: Long, isInOverflowShelf: Boolean) {
        val elapsed = (computeTimeInMs - preparedAt)/1000
        valueAtLastMeasured = valueAfter(elapsed.toInt(), isInOverflowShelf)
        lastMeasuredTimestamp = computeTimeInMs
    }

    /**
     * checks if the order has expired at the time of calling
     */
    fun isExpired(isInOverflowShelf: Boolean = false): Boolean {
        return valueNow(isInOverflowShelf) <= 0
    }

    @VisibleForTesting fun valueNow(isInOverflowShelf: Boolean = false): Float {
        return valueAfter(
            orderAgeInSeconds = ((System.currentTimeMillis() - lastMeasuredTimestamp)/1000).toInt(),
            isInOverflowShelf = isInOverflowShelf
        )
    }

    @VisibleForTesting fun valueAfter(orderAgeInSeconds: Int, isInOverflowShelf: Boolean = false): Float {
        // if order is in overflow then it decays faster
        val decayRate: Float = if (isInOverflowShelf) 2 * decayRate else decayRate
        val expression = valueExpression
            .replace("shelfLife", "$valueAtLastMeasured")
            .replace("decayRate", "$decayRate")
            .replace("orderAge", "$orderAgeInSeconds")
        return try {
            val value = Instances.expressionEvaluator.parseToEnd(expression)
            if (value > 0.0f) value else 0f
        } catch (ex: Exception) {
            logger.error("[${id.hex()}] Failed parsing value expression: formula=$valueExpression, " +
                    "postSubstitution=$expression")
            // call the order expired and move on
            0f
        }
    }
}