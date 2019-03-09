package com.css.kitchensync.standalone

import com.css.kitchensync.common.Order
import com.css.kitchensync.service.OrderPreparationService
import com.css.kitchensync.service.RandomTimeDriverDispatcher
import com.css.kitchensync.service.ShelfManager
import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking

/**
 * Main Service that handles the orders and redirects them to kitchen, dispatcher and ShelfManager
 */
object OrderHandlerService {

    private val kitchenSyncConfig: Config by lazy {
        val config = ConfigFactory.load()
        val env = System.getProperty("env", "production")
        config.getConfig(env).getConfig("kitchensync")
    }

    suspend fun initialize() {
        val dispatcher = RandomTimeDriverDispatcher(kitchenSyncConfig)
        val kitchen = OrderPreparationService(kitchenSyncConfig, dispatcher)
        // assign a dummy channel.
        val shelfManager = ShelfManager(kitchenSyncConfig, dispatcher, Channel())
        coroutineScope {
            kitchen.initialize()
            dispatcher.initialize()
            shelfManager.initialize()
        }
    }

    /**
     * Dispatches it to the kitchen to handle it
     */
    fun handleOrder(order: Order) = runBlocking {
       OrderPreparationService.orderPipeline.send(order)
    }
}
