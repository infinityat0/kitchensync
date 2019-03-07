package com.css.kitchensync.service

import com.css.kitchensync.common.PreparedOrder
import com.css.kitchensync.testConfig
import io.kotlintest.matchers.numerics.shouldBeGreaterThanOrEqual
import io.kotlintest.matchers.numerics.shouldBeLessThanOrEqual
import io.kotlintest.shouldBe
import io.kotlintest.specs.StringSpec
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking

/**
 * Tests for {@link DriverDispatcher}
 */
class DriverDispatcherSpec : StringSpec() {

    private fun makeDispatcher(): RandomTimeDriverDispatcher {
        val dispatcher = RandomTimeDriverDispatcher(testConfig())
        dispatcher.initialize()
        return dispatcher
    }

    private fun dispatchDrivers(dispatcher: RandomTimeDriverDispatcher, n: Int = 1) = runBlocking {
        for (i in 1..n) {
            val order = PreparedOrder("Ice cream", "frozen", 100, 0.5f)
            dispatcher.dispatchDriver(order)
        }
        delay(1000)
    }

    init {
        "dispatcher should start tracking the order" {
            val dispatcher = makeDispatcher()
            dispatchDrivers(dispatcher, n = 1)
            dispatcher.driverMap.size shouldBe 1
        }

        "dispatcher should take in 5 orders" {
            val dispatcher = makeDispatcher()
            dispatchDrivers(dispatcher, n = 5)
            dispatcher.driverMap.size shouldBe 5
        }

        "dispatcher should maintain sync between trackers and drivers" {
            val dispatcher = makeDispatcher()
            dispatchDrivers(dispatcher, n = 10)
            dispatcher.driverMap.size shouldBe dispatcher.driverTracker.size
        }

        "dispatcher should generate an arrival time between [2, 10] seconds" {
            val dispatcher = makeDispatcher()
            for (i in 1..25) {
                val arrivalTime = dispatcher.getArrivalTime().seconds
                arrivalTime shouldBeGreaterThanOrEqual 2
                arrivalTime shouldBeLessThanOrEqual 10
            }
        }

        "dispatcher should cancel driver" {
            val dispatcher = makeDispatcher()
            val order = PreparedOrder("Ice cream", "frozen", 100, 0.5f)
            dispatcher.dispatchDriver(order)
            delay(500)
            dispatcher.cancelDriverForOrder(order)
            delay(500)
            dispatcher.driverMap.containsKey(order.id) shouldBe false
        }
    }
}
