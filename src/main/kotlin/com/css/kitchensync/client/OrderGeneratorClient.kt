package com.css.kitchensync.client

import com.beust.klaxon.JsonReader
import com.beust.klaxon.Klaxon
import com.css.kitchensync.common.Order
import com.css.kitchensync.common.hex
import com.css.kitchensync.logging.error
import com.css.kitchensync.logging.ifDebug
import com.css.kitchensync.metrics.Stats
import com.css.kitchensync.service.OrderPreparationService
import com.typesafe.config.Config
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
 * This is meant to run inside the server instance.
 */
class OrderGeneratorClient(
    private val kitchenSyncConfig: Config,
    private val service: OrderPreparationService) {

    private val logger = Logger.getLogger(this.javaClass.simpleName)
    private val timer: Timer by lazy { Timer(/* isDaemon = */ true) }

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
        GlobalScope.launch { service.prepareOrder(order) }
    }

    internal fun startSendingOrders() {
        try {
            readOrdersAndSend(kitchenSyncConfig.getConfig("client.orders"))
            val ordersCreated = Stats.getCounter("kitchensync_client_orders_created")
            logger.info("Total orders processed: $ordersCreated")
        } catch (ex: Exception) {
            logger.error("failed while generating/sending orders.", ex)
        } finally {
            // no matter what happens, clean up
            logger.info("shutting the client down")
            cleanUp()
        }
    }

    /**
     * Print stats on how we did. Need to add more counters
     */
    private fun printStats() {
        StatsLib.getVariables().forEach { stat ->
            logger.info("Stat: ${stat.name} = ${stat.read()}")
        }
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
