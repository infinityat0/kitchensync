package com.css.kitchensync.common

import com.css.kitchensync.logging.error
import com.github.h0tk3y.betterParse.grammar.parseToEnd
import java.util.logging.Logger
import kotlin.random.Random

data class PreparedOrder(
    val name: String,
    val temp: String,
    val shelfLife: Int,
    val decayRate: Float,
    val id: String = Random.nextInt().hex(),
    val valueExpression: String = "shelfLife - (1 + decayRate)*orderAge") {

    // Should be updatable later since the time we create an instance may not
    // be the time we actually send the order
    val preparedAt: Long = System.currentTimeMillis()

    // Initial value should coincide with the shelfLife of the order
    // At order created time, value is shelf value
    internal var valueAtLastMeasured: Float = shelfLife.toFloat()
    internal var lastMeasuredTimestamp: Long = preparedAt

    @Synchronized fun computeAndAssignValue(computeTimeInMs: Long, wasInOverflowShelf: Boolean) {
        val elapsed = (computeTimeInMs - lastMeasuredTimestamp)/1000
        valueAtLastMeasured = valueAfter(elapsed.toInt(), wasInOverflowShelf)
        lastMeasuredTimestamp = computeTimeInMs
    }

    fun status(shelfName: String) =
        OrderStatus(id, name, shelfName, temp, valueAtLastMeasured, valueAtLastMeasured / shelfLife)

    /**
     * checks if the order has expired at the time of calling
     */
    fun isExpired(isInOverflowShelf: Boolean = false): Boolean {
        return valueNow(isInOverflowShelf) <= 0
    }

    internal fun valueNow(isInOverflowShelf: Boolean = false): Float {
        return valueAfter(
            orderAgeInSeconds = ((System.currentTimeMillis() - lastMeasuredTimestamp)/1000).toInt(),
            isInOverflowShelf = isInOverflowShelf
        )
    }

    internal fun valueAfter(orderAgeInSeconds: Int, isInOverflowShelf: Boolean = false): Float {
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
            logger.error("[$id] Failed parsing value expression: formula=$valueExpression, " +
                    "postSubstitution=$expression")
            // call the order expired and move on
            0f
        }
    }

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
}
