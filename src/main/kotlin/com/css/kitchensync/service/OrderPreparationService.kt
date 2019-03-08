package com.css.kitchensync.service

import com.css.kitchensync.common.Order
import com.css.kitchensync.common.PreparedOrder
import com.css.kitchensync.config.getInt
import com.css.kitchensync.metrics.Stats
import com.google.common.annotations.VisibleForTesting
import com.typesafe.config.Config
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.util.logging.Logger

/**
 * Service that handles preparation of food and submitting it to the shelf.
 */
class OrderPreparationService(
    private val kitchenSyncConfig: Config,
    private val dispatcherService: DriverDispatchService) {

    companion object {
        // Have this accessible for other classes.
        // Channel that contains orders that need to be prepared in the kitchen
        val orderPipeline = Channel<Order>()
    }

    private val logger = Logger.getLogger(this.javaClass.simpleName)

    suspend fun initialize() = GlobalScope.launch {
        val chefCount = kitchenSyncConfig.getInt("service.chef-count", 10)
        logger.info("kitchen starting to prepare orders. chefCount=$chefCount")
        repeat(chefCount) { prepareFood(it, orderPipeline) }
    }

    /**
     * Read through all the order pipeline and prepare food.
     * @param id - chef that handles the order
     * @param channel - channel to read from
     */
    private suspend fun prepareFood(id: Int, channel: Channel<Order>) {
        // increment the prepared orders counter
        for (order in channel) {
            // update the order start-time
            val preparedOrder = PreparedOrder.fromOrderRequest(order)

            logger.info("[${preparedOrder.id}] prepared order: ${order.name}")
            // every time a chef prepares an order, increment the counter
            Stats.incr("kitchensync_service_orders_prepared_in_kitchen")
            Stats.incr("kitchensync_service_orders_prepared_by_chef$id")

            // Dispatch a driver, assign them the order and put the order in a shelf
            dispatcherService.dispatchDriver(preparedOrder)
            ShelfManager.preparedOrdersChannel.send(preparedOrder)
        }
    }

    @VisibleForTesting fun prepareOrder(order: Order) = runBlocking {
        orderPipeline.send(order)
    }
}
