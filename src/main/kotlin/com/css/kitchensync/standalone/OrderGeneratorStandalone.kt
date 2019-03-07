@file:JvmName("OrderGeneratorStandalone")
package com.css.kitchensync.standalone

import com.beust.klaxon.JsonReader
import com.beust.klaxon.Klaxon
import com.css.kitchensync.common.Order
import com.css.kitchensync.common.hex
import com.css.kitchensync.logging.ApplicationLogger
import com.css.kitchensync.logging.error
import com.css.kitchensync.logging.ifDebug
import com.css.kitchensync.metrics.Stats
import com.css.kitchensync.service.OrderHandlerService
import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.apache.commons.math3.distribution.PoissonDistribution
import java.io.FileReader
import java.util.*
import java.util.concurrent.CountDownLatch
import java.util.logging.Logger
import kotlin.concurrent.timerTask
import com.twitter.common.stats.Stats as StatsLib

/**
 * Class that generates and sends orders to a service in real time.
 * It reads orders from a file and models traffic based on PoissonDistribution
 * with a configurable mean.
 *
 * This is a standalone application. When run independently, it will start
 * - reading orders,
 * - processing orders
 * - dispatching driver
 * - managing the shelfs
 *
 * and dumps all results in the console/log file.
 */
object OrderHandler {

    private val service: OrderHandlerService = OrderHandlerService
    private val logger = Logger.getLogger(this.javaClass.simpleName)

    private val timer: Timer by lazy { Timer(/* isDaemon = */ true) }

    private val kitchenSyncConfig: Config by lazy {
        val config = ConfigFactory.load()
        val env = System.getProperty("env", "production")
        config.getConfig(env).getConfig("kitchensync")
    }

    private val distribution: PoissonDistribution by lazy {
        val meanTrafficPerSecond = kitchenSyncConfig.getDouble("client.orders.mean-traffic")
        val meanTimePerOrder = 1000/meanTrafficPerSecond
        PoissonDistribution(meanTimePerOrder)
    }

    /**
     * This is a streaming API which
     *
     * - Reads orders from the file,
     * - schedules them to be sent at a future time (based on the traffic distribution)
     * - waits for an order to be processed before proceeding with another
     *
     * @param orderConfig config object containing the parameters like where to read orders from
     *    and traffic speed etc.
     */
    private fun readOrdersAndSend(orderConfig: Config) {
        val orderSource = orderConfig.getString("source-path")
        val ordersReader = FileReader(orderSource)
        JsonReader(ordersReader).use { reader ->
            logger.info("streaming order from a file: source=$orderSource")
            val jsonParser = Klaxon()
            reader.beginArray {
                while (reader.hasNext()) {
                    val order = jsonParser.parse<Order>(reader)
                    // This, in the future could be replaced by a coroutine
                    order?.let {
                        if (order.isValid()) {
                            val latch = CountDownLatch(1)
                            timer.schedule(timerTask {
                                Stats.incr("kitchensync_client_orders_created")
                                sendOrder(order)
                                latch.countDown()
                            }, distribution.sample().toLong())
                            latch.await()
                        } else {
                            logger.warning("ignoring order ${order.name}. Validation failed")
                        }
                    }
                }
            }
        }
    }

    /**
     * Sends the order out to the service.
     */
    private fun sendOrder(order: Order) {
        logger.ifDebug { "[${order.id.hex()}] Sending order to kitchen: ${order.name}" }
        GlobalScope.launch { service.handleOrder(order) }
    }

    private fun start() {
        try {
            readOrdersAndSend(kitchenSyncConfig.getConfig("client.orders"))
            val ordersCreated = Stats.getCounter("kitchensync_client_orders_created")
            logger.info("Total orders processed: $ordersCreated")
        } catch (ex: Exception) {
            logger.error("failed while generating/sending orders.", ex)
        } finally {
            // no matter what happens, clean up
            logger.info("shutting the application down")
            cleanUp()
        }
    }

    private fun printStats() {
        StatsLib.getVariables().forEach { stat ->
            logger.info("Stat: ${stat.name} = ${stat.read()}")
        }
    }

    fun initialize() {
        ApplicationLogger.initialize(kitchenSyncConfig)
        logger.info("initializing the application")
        // Make sure to shutdown gracefully when killed.
        Runtime.getRuntime().addShutdownHook(Thread { cleanUp() })
        logger.info("shutdown hook attached")
        start()
    }

    /**
     * Cleans up any resources that we are currently holding on to
     * and terminates the application.
     */
    private fun cleanUp() = runBlocking {
        delay(10_000) // wait until all the orders are dispatched and picked up
        printStats() // print any statistics that we have gathered
        logger.info("cleaning up and canceling all active timer tasks")
        // clean up the timer and end remaining tasks
        timer.cancel()
    }
}

fun main() {
    // start the service
    GlobalScope.launch { OrderHandlerService.initialize() }
    // start the client and generate orders
    OrderHandler.initialize()
}
