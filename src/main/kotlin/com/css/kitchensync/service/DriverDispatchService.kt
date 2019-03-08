package com.css.kitchensync.service

import com.css.kitchensync.common.PreparedOrder
import com.css.kitchensync.common.hex
import com.css.kitchensync.config.getInt
import com.css.kitchensync.duration.seconds
import com.css.kitchensync.logging.ifDebug
import com.css.kitchensync.metrics.Stats
import com.google.common.annotations.VisibleForTesting
import com.google.common.collect.Maps
import com.typesafe.config.Config
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import java.time.Duration
import java.util.concurrent.ConcurrentMap
import java.util.logging.Logger
import kotlin.random.Random

data class Driver(val name: String, val order: PreparedOrder, val arrivalTime: Duration)

/**
 * Service to dispatch a driver and assign them an order
 */
interface DriverDispatchService {

    /**
     * Initializes dispatch service
     */
    fun initialize(): Job

    /**
     * Dispatches a driver for an order
     * @param order that's prepared in the kitchen
     */
    fun dispatchDriver(order: PreparedOrder)

    /**
     * Cancels the driver (if dispatched) for the order
     * @param order that's prepared in the kitchen
     */
    fun cancelDriverForOrder(order: PreparedOrder)
}

/**
 * Handles dispatches and assigns drivers that arrive anytime between
 * [2, 10] seconds
 */
internal class RandomTimeDriverDispatcher(kitchenSyncConfig: Config): DriverDispatchService {

    private val logger = Logger.getLogger(this.javaClass.simpleName)
    private val driverDispatcherChannel = Channel<PreparedOrder>(10)
    private val minArrivalTime = kitchenSyncConfig.getInt("driver-min-arrival-time", 2)
    private val maxArrivalTime = kitchenSyncConfig.getInt("driver-max-arrival-time", 10)

    // Has a mapping from order-id -> driver that's running it
    @VisibleForTesting val driverMap: ConcurrentMap<String, Driver> = Maps.newConcurrentMap<String, Driver>()
    // Has details of driver's run
    @VisibleForTesting val driverTracker: ConcurrentMap<String, Job> = Maps.newConcurrentMap<String, Job>()

    // Someday I'll have to figure how to tame these coroutines into a CoroutineScope
    override fun initialize() = GlobalScope.launch {
        logger.info("dispatcher starting to dispatch drivers")
        dispatchDrivers()
    }

    override fun dispatchDriver(order: PreparedOrder) = runBlocking {
        driverDispatcherChannel.send(order)
    }

    private suspend fun dispatchDrivers() = coroutineScope {
        for (order in driverDispatcherChannel) {
            val arrivalTime = getArrivalTime()
            val driver = makeDriver(order, arrivalTime)

            logger.info(
                "[${order.id}] driver ${driver.name} dispatched: order=${order.name}. " +
                        "Will arrive in ${driver.arrivalTime.seconds} seconds"
            )
            Stats.incr("kitchensync_service_orders_dispatched")

            // fire off into a channel consumed by shelf manager
            val job = launch {
                delay(arrivalTime.toMillis())
                if (!isDriverCancelled(driver)) {
                    logger.ifDebug { "[${order.id}] ${driver.name} is arriving..." }
                    // Stop tracking that driver since the driver is going to be at the door
                    driverMap.remove(driver.order.id)
                    driverTracker.remove(driver.name)
                    ShelfManager.arrivedDriversChannel.send(driver)
                }
            }
            // start tracking driver's arrival and order -> driver mapping
            driverTracker[driver.name] = job
            driverMap[driver.order.id] = driver
        }
    }

    private fun makeDriver(order: PreparedOrder, arrivalTime: Duration) =
        Driver("driver-${Random.nextInt().hex()}", order, arrivalTime)

    @VisibleForTesting fun getArrivalTime(): Duration {
        // need arrival time to be between [2, 10] seconds
        return (Random.nextInt(until = (maxArrivalTime - minArrivalTime) + 1) + minArrivalTime).seconds()
    }

    /**
     * Check if the driver is cancelled.
     * @return true if the driver isn't tracked anymore
     */
    private fun isDriverCancelled(driver: Driver): Boolean {
        // return !driverMap.containsKey(driver.name) would work as well
        return !driverTracker.containsKey(driver.name)
    }

    /**
     * Cancel the driver. Remove them from tracking
     */
    @Synchronized override fun cancelDriverForOrder(order: PreparedOrder) {
        driverMap[order.id]?.let { driver ->
            driverMap.remove(order.id)
            driverTracker.remove(driver.name)
            driverTracker[driver.name]?.cancel()

            logger.info("[${driver.order.id}] driver cancelled: name=${driver.name}")
            Stats.incr("kitchensync_service_drivers_cancelled")
        }
    }
}
