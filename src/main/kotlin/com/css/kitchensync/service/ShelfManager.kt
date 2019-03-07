package com.css.kitchensync.service

import com.css.kitchensync.common.*
import com.css.kitchensync.config.getInt
import com.css.kitchensync.config.getLong
import com.css.kitchensync.logging.debug
import com.css.kitchensync.logging.error
import com.css.kitchensync.logging.ifDebug
import com.css.kitchensync.metrics.Stats
import com.google.common.annotations.VisibleForTesting
import com.typesafe.config.Config
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ticker
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.util.logging.Logger

class ShelfManager(
    private val kitchenSyncConfig: Config,
    private val dispatcher: DriverDispatchService) {

    companion object {
        // channel that contains all drivers that have arrived
        val arrivedDriversChannel = Channel<Driver>(capacity = 10)
        // channel that accepts prepared orders
        val preparedOrdersChannel = Channel<PreparedOrder>(capacity = 10)
        // channel that contains status of the shelves
        val shelfStatusChannel = Channel<ShelfStatus>(capacity = 10)

        private fun getShelf(shelfManager: ShelfManager, order: PreparedOrder): Shelf? = when (order.temp) {
            "hot" -> shelfManager.hotShelf
            "cold" -> shelfManager.coldShelf
            "frozen" -> shelfManager.frozenShelf
            else -> {
                shelfManager.logger.error( // shouldn't have come here!
                    "[${order.id.hex()}] wrong temperature for the order. " +
                            "name=${order.name}, temp=${order.temp}")
                null
            }
        }
    }

    private val logger = Logger.getLogger(this.javaClass.simpleName)
    @VisibleForTesting val hotShelf: Shelf by lazy {
        Shelf("hot", kitchenSyncConfig.getInt("service.hot-shelf-size", 15))
    }
    @VisibleForTesting val coldShelf: Shelf by lazy {
        Shelf("cold", kitchenSyncConfig.getInt("service.cold-shelf-size", 15))
    }
    @VisibleForTesting val frozenShelf: Shelf by lazy {
        Shelf("frozen", kitchenSyncConfig.getInt("service.frozen-shelf-size", 15))
    }
    @VisibleForTesting val overflowShelf: Shelf by lazy {
        Shelf("overflow", kitchenSyncConfig.getInt("service.overflow-shelf-size", 20))
    }

    suspend fun initialize() = runBlocking {
        logger.info("Shelf manager starting to fulfill orders")
        fulfillOrders()
        logger.info("Shelf manager starting to stack orders")
        addOrdersToShelf()
        logger.info("starting a ticker to check order values")
        doHouseKeeping()
    }

    /**
     * Coroutine to handle prepared orders and put them in a shelf
     */
    private suspend fun CoroutineScope.addOrdersToShelf() = launch {
        for (order in preparedOrdersChannel) {
            addToShelf(order)
        }
    }

    /**
     * Coroutine to fulfill all the orders that are being streamed
     */
    private suspend fun CoroutineScope.fulfillOrders() = launch {
        for (driver in arrivedDriversChannel) {
            val order = driver.order
            removeOrderFromShelf(driver.order)
            Stats.incr("kitchensync_orders_fulfilled")
            logger.info(
                "[${order.id.hex()}] order handed to driver. order=${order.name}, driver=${driver.name}"
            )
        }
    }

    private fun CoroutineScope.doHouseKeeping() = launch {
        val tickerPeriodicity = kitchenSyncConfig.getLong("service.order-value-checker-periodicity", 1000)
        // start a ticker that ticks every second after the first second.
        val tickerChannel = ticker(delayMillis = tickerPeriodicity, initialDelayMillis = 1000)
        for (tick in tickerChannel) {
            logger.ifDebug { "received a tick: doing housekeeping" }
            sweepShelf(hotShelf)
            sweepShelf(coldShelf)
            sweepShelf(frozenShelf)
            sweepAndUpdateOverflowShelf()
        }
    }

    /**
     * Adds an order to the appropriate shelf.
     * - If it fits in it's corresponding shelf, add it there
     * - If it doesn't, then try adding it to the overflow shelf
     * - If overflow is full, drop it and cancel the order
     *
     * @param order that needs to be added to the shelf
     */
    @Synchronized private fun addToShelf(order: PreparedOrder) {
        var shelf: Shelf? = getShelf(this, order)
        val orderKept: Boolean? = shelf?.putOrder(order)?.let { success ->
            if (success) true else {
                shelf = overflowShelf
                overflowShelf.putOrder(order)
            }
        }
        orderKept?.let { kept ->
            if (kept) {
                logger.info("[${order.id.hex()}] order placed in ${shelf?.name} shelf: size=${shelf?.getSize()}")
            }
            else {
                logger.info("[${order.id.hex()}] order discarded. name=${order.name}")
                // cancel the driver
                dispatcher.cancelDriverForOrder(order)
            }
        }
    }

    @Synchronized private fun removeOrderFromShelf(order: PreparedOrder) {
        // First look for it in it's shelf, if it's not present, then look for it in overflow shelf
        // and remove it from there
        getShelf(this, order)?.let { shelf ->
            if (!shelf.removeFromShelf(order)) overflowShelf.removeFromShelf(order)
        }
    }

    internal fun sweepShelvesOnDemand() = listOf(hotShelf, coldShelf, frozenShelf, overflowShelf).map { sweepShelf(it) }

    @Synchronized internal fun sweepShelf(shelf: Shelf): ShelfStatus {
        shelf.cleanup().forEach { order ->
            logger.info("[${order.id.hex()}] order expired: cancelling driver. order=${order.name}")
            dispatcher.cancelDriverForOrder(order)
        }
        return computeAndLogValueOfOrdersInShelf(shelf)
    }

    private fun computeAndLogValueOfOrdersInShelf(shelf: Shelf): ShelfStatus {
        val orderStatuses = shelf.getAllValues().map { order ->
            val orderValue = order.valueNow(isInOverflowShelf = shelf.name == "overflow")
            // Hack to get 2 digits of precision for float instead of 6
            val normalized = orderValue / order.shelfLife
            logger.ifDebug { "[${order.id.hex()}] ${order.name} currentValue=$orderValue, normalized=$normalized" }
            OrderStatus(order.name, order.temp, orderValue, normalized)
        }
        // Lexical sort this by name for better tracking
        return ShelfStatus(shelf.name, orderStatus = orderStatuses.sortedBy { it.name })
    }

    private fun sweepAndUpdateOverflowShelf() {
        sweepShelf(overflowShelf)
        for (order in overflowShelf.getAllValues()) {
            val shelf = getShelf(this, order)
            // Why didn't we just do shelf?.putOrder(order). Because, we don't want to compute it's value
            // every time we fail to put it in the shelf
            shelf?.isFull()?.let { isFull ->
                if (!isFull) {
                    // update it's value, put it in regular shelf
                    order.computeAndAssignValue(System.currentTimeMillis(), isInOverflowShelf = true)
                    // We may still have an update and shelf could get full
                    if (shelf.putOrder(order)) {
                        overflowShelf.removeFromShelf(order)
                        logger.info("[${order.id.hex()}] ${order.name} moved from overflow -> ${shelf.name}")
                    }
                }
            }
        }
        computeAndLogValueOfOrdersInShelf(overflowShelf)
    }
}
