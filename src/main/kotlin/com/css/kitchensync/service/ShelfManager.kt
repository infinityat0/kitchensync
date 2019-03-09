package com.css.kitchensync.service

import com.css.kitchensync.common.*
import com.css.kitchensync.config.getInt
import com.css.kitchensync.config.getLong
import com.css.kitchensync.logging.ifDebug
import com.css.kitchensync.metrics.Stats
import com.typesafe.config.Config
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.channels.ticker
import java.util.logging.Logger

@ObsoleteCoroutinesApi
class ShelfManager(
    private val kitchenSyncConfig: Config,
    private val dispatcher: DriverDispatchService,
    private val orderStatusChannel: SendChannel<String>) {

    companion object {
        // channel that contains all drivers that have arrived
        val arrivedDriversChannel = Channel<Driver>(capacity = 10)
        // channel that accepts prepared orders
        val preparedOrdersChannel = Channel<PreparedOrder>(capacity = 10)
    }

    private val logger = Logger.getLogger(this.javaClass.simpleName)
    internal val hotShelf: Shelf by lazy {
        Shelf("hot", kitchenSyncConfig.getInt("service.hot-shelf-size", 15))
    }
    internal val coldShelf: Shelf by lazy {
        Shelf("cold", kitchenSyncConfig.getInt("service.cold-shelf-size", 15))
    }
    internal val frozenShelf: Shelf by lazy {
        Shelf("frozen", kitchenSyncConfig.getInt("service.frozen-shelf-size", 15))
    }
    internal val overflowShelf: Shelf by lazy {
        Shelf("overflow", kitchenSyncConfig.getInt("service.overflow-shelf-size", 20))
    }

    /**
     * Initialize shelf manager.
     * - Start adding orders to shelf by checking the pipeline
     * - Start fulfilling orders and handing them to driver
     * - Start housekeeping and clearing up shelves
     */
    fun initialize() = runBlocking {
        logger.info("Shelf manager starting to stack orders")
        startHandlingOrders()
        logger.info("Shelf manager starting to fulfill orders")
        startFulfillingOrders()
        logger.info("Shelf manager starting a ticker to check order values")
        doHouseKeeping()
    }

    /**
     * Coroutine to handle prepared orders and put them in a shelf
     */
    private suspend fun CoroutineScope.startHandlingOrders() = launch {
        for (order in preparedOrdersChannel) {
            addOrderToShelf(order)
        }
    }

    /**
     * Coroutine to fulfill all the orders that are being streamed
     */
    private suspend fun CoroutineScope.startFulfillingOrders() = launch {
        for (driver in arrivedDriversChannel) {
            val order = driver.order
            removeOrderFromShelf(driver.order)
            Stats.incr("kitchensync_orders_fulfilled")
            logger.info(
                "[${order.id}] order handed to driver. order=${order.name}, driver=${driver.name}")
        }
    }

    /**
     * Ticker to run housekeeping (checking and updating values of the orders) every second
     */
    @ObsoleteCoroutinesApi
    private fun CoroutineScope.doHouseKeeping() = launch {
        val serviceConfig = kitchenSyncConfig.getConfig("service")
        val tickerPeriodicity = serviceConfig.getLong("order-value-checker-periodicity", 1000)
        val tickerInitialDelay = serviceConfig.getLong("order-value-checker-initial-delay", 1000)
        // start a ticker that ticks every second after the first second.
        val tickerChannel = ticker(delayMillis = tickerPeriodicity, initialDelayMillis = tickerInitialDelay)
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
    @Synchronized internal fun addOrderToShelf(order: PreparedOrder) {
        if (order.isExpired(isInOverflowShelf = false)) {
            logger.warning("[${order.id}] order discarded: name=${order.name}. already expired")
        } else {
            var shelf: Shelf = getShelf(order)
            val kept = if (shelf.putOrder(order)) true else {
                shelf = overflowShelf
                overflowShelf.putOrder(order)
            }
            if (kept) {
                sendStatusMessage(AddOrder(shelf.name, order.status(shelf.name)).toJson())
                logger.info("[${order.id}] ${order.name} placed in ${shelf.name} shelf: size=${shelf.getSize()}")
            } else {
                // cancel the driver
                dispatcher.cancelDriverForOrder(order)
                logger.warning("[${order.id}] order discarded: name=${order.name}. cancelling driver")
            }
        }
    }

    /**
     * Sends order status on to the channel
     */
    private fun sendStatusMessage(message: String) = GlobalScope.launch {
        orderStatusChannel.send(message)
    }

    private fun getShelf(order: PreparedOrder): Shelf = when (order.temp) {
        "hot" -> hotShelf
        "cold" -> coldShelf
        else -> frozenShelf
        // since we load PreparedOrders from Orders, we can be sure it belongs to a shelf
    }

    @Synchronized internal fun removeOrderFromShelf(order: PreparedOrder) {
        // First look for it in it's shelf, if it's not present, then look for it in overflow shelf
        // and remove it from there
        val removed = getShelf(order)
            .removeFromShelf(order)
            .or(overflowShelf.removeFromShelf(order))
        if (removed) {
           sendStatusMessage(RemoveOrder(order.status(order.temp)).toJson()) // really doesn't matter which shelf!
        } else {
            logger.warning("[${order.id}] order not found in shelf")
        }
    }

    @Synchronized internal fun sweepShelf(shelf: Shelf): ShelfStatus {
        shelf.cleanup().forEach { order ->
            logger.warning("[${order.id}] order expired: cancelling driver. order=${order.name}")
            sendStatusMessage(RemoveOrder(order.status(shelf.name)).toJson())
            dispatcher.cancelDriverForOrder(order)
        }
        return computeAndLogValueOfOrdersInShelf(shelf)
    }

    private fun computeAndLogValueOfOrdersInShelf(shelf: Shelf): ShelfStatus {
        val orderStatuses = shelf.getAllValues().map { order ->
            val orderValue = order.valueNow(isInOverflowShelf = shelf.name == "overflow")
            val normalized = orderValue / order.shelfLife
            logger.ifDebug {
                "[${order.id}] shelf=${shelf.name} ${order.name} orderValue=$orderValue, normalized=$normalized"
            }
            val status = OrderStatus(order.id, order.name, shelf.name, order.temp, orderValue, normalized)
            sendStatusMessage(UpdateValue(status, shelf.name).toJson())
            status
        }
        // Lexical sort this by name for better tracking
        return ShelfStatus(shelf.name, orderStatuses = orderStatuses.sortedBy { it.name })
    }

    internal fun sweepAndUpdateOverflowShelf() {
        sweepShelf(overflowShelf)
        for (order in overflowShelf.getAllValues()) {
            val shelf = getShelf(order)
            // Why didn't we just do shelf.putOrder(order). Because, we don't want to compute it's value
            // every time we fail to put it in the shelf
            if (!shelf.isFull()) {
                // update it's value, put it in regular shelf
                order.computeAndAssignValue(System.currentTimeMillis(), wasInOverflowShelf = true)
                // We may still have an update and shelf could get full
                if (shelf.putOrder(order)) {
                    overflowShelf.removeFromShelf(order)
                    sendStatusMessage(
                        MoveOrder(
                            fromShelf = overflowShelf.name,
                            toShelf = shelf.name,
                            orderStatus = order.status(shelf.name)
                        ).toJson()
                    )
                    logger.info("[${order.id}] ${order.name} moved: [overflow -> ${shelf.name}]")
                }
            }
        }
        computeAndLogValueOfOrdersInShelf(overflowShelf)
    }
}
