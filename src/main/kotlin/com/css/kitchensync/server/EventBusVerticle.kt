@file:Suppress("DEPRECATION")

package com.css.kitchensync.server

import com.css.kitchensync.client.OrderGeneratorClient
import com.css.kitchensync.config.getInt
import com.css.kitchensync.config.getString
import com.css.kitchensync.logging.ApplicationLogger
import com.css.kitchensync.service.OrderPreparationService
import com.css.kitchensync.service.RandomTimeDriverDispatcher
import com.css.kitchensync.service.ShelfManager
import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import io.vertx.core.AbstractVerticle
import io.vertx.core.Future
import io.vertx.ext.bridge.BridgeEventType
import io.vertx.ext.web.Router
import io.vertx.ext.web.handler.StaticHandler
import io.vertx.ext.web.handler.sockjs.BridgeOptions
import io.vertx.ext.web.handler.sockjs.PermittedOptions
import io.vertx.ext.web.handler.sockjs.SockJSHandler
import io.vertx.kotlin.coroutines.toChannel
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.SendChannel
import java.util.logging.Logger

/**
 * Class that starts the server,
 * - sets up the application,
 * - loads configuration
 * - initializes stats
 * - creates an instance of the order generator and starts processing orders
 * - creates router and an eventbus at address shelf-sync
 */
@ExperimentalCoroutinesApi
class EventBusVerticle : AbstractVerticle() {

    private val logger = Logger.getLogger(this.javaClass.simpleName)

    override fun start(startFuture: Future<Void>) {
        // Create a http server and start serving on 8080
        createHttpServer()

        // create a publish channel and use it to send updates on the event bus
        val publishChannel = makePublishChannel()

        // Initialize service and components
        initializeApplication(publishChannel)
        // start publisher that publishes on the event bus
        // GlobalScope.launch { startPublishingOnEventBus() }
    }

    private fun createHttpServer() {
        val router = Router.router(vertx)
        val sockJSHandler = makeSockJsHandler(eventBusAddress)

        router.route().handler(StaticHandler.create())
        router.route("/eventbus/*").handler(sockJSHandler)

        vertx.createHttpServer()
            .requestHandler(router)
            .listen(kitchenSyncConfig.getInt("service.port", 8080))
    }

    private fun makeSockJsHandler(address: String): SockJSHandler {
        val outboundOptions = PermittedOptions().setAddress(address)
        val options = BridgeOptions().addOutboundPermitted(outboundOptions)

        return SockJSHandler.create(vertx).bridge(options) { event ->
            if (event.type() === BridgeEventType.SOCKET_CREATED) {
                logger.info("socket was created at address: $eventBusAddress")
            }
            // This signals that it's ok to process the event
            event.complete(true)
        }
    }

    private fun makePublishChannel(): SendChannel<String> {
        val statusPublisher = vertx.eventBus().publisher<String>(eventBusAddress)
        return statusPublisher.toChannel(vertx, 10)
    }

    private fun initializeApplication(publishChannel: SendChannel<String>) {
        ApplicationLogger.initialize(kitchenSyncConfig)
        logger.info("initializing the application")
        // Make sure to shutdown gracefully when killed.
        Runtime.getRuntime().addShutdownHook(Thread { stop() })
        logger.info("shutdown hook attached")
        // start service that handles orders
        val shelfManager = ShelfManager(kitchenSyncConfig, dispatcher, publishChannel)
        GlobalScope.launch {
            coroutineScope {
                kitchen.initialize()
                dispatcher.initialize()
                shelfManager.initialize()
            }
        }
        // start the order generator client.
        GlobalScope.launch {
            OrderGeneratorClient(kitchenSyncConfig, kitchen).startSendingOrders()
        }
    }

    /**
     * Stop the server, and possibly the client.
     * Clean up any resources that we are holding on to
     */
    override fun stop() = runBlocking {
        logger.info("cleaning up and shutting down the server")
        vertx.eventBus().close {
            logger.info("shut down event bus at address: $eventBusAddress")
        }
    }

    companion object {
        private val kitchenSyncConfig: Config by lazy {
            val config = ConfigFactory.load()
            val env = System.getProperty("env", "production")
            config.getConfig(env).getConfig("kitchensync")
        }

        private val eventBusAddress = kitchenSyncConfig.getString("event-bus-address", "shelf-status")
        private val dispatcher = RandomTimeDriverDispatcher(kitchenSyncConfig)
        private val kitchen = OrderPreparationService(kitchenSyncConfig, dispatcher)
    }
}
