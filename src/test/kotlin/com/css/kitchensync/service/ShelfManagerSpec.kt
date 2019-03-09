package com.css.kitchensync.service

import com.css.kitchensync.common.PreparedOrder
import com.css.kitchensync.testConfig
import io.kotlintest.matchers.between
import io.kotlintest.shouldBe
import io.kotlintest.specs.StringSpec
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay

class ShelfManagerSpec : StringSpec() {

    private val fakeDispatcher = FakeDispatcher()

    private fun makeShelfManager(): ShelfManager = ShelfManager(
        kitchenSyncConfig = testConfig(),
        dispatcher = fakeDispatcher,
        orderStatusChannel = Channel()
    )

    private fun withShelfManager(f: (ShelfManager) -> Unit) {
        f(makeShelfManager())
    }

    @ExperimentalCoroutinesApi
    private fun waitTillChannelIsEmpty() {
        while (!ShelfManager.preparedOrdersChannel.isEmpty) {
        }
    }

    init {
        "adding an order to a shelf" {
            withShelfManager { shelfManager ->
                val order = PreparedOrder("Ice cream", "frozen", 100, 0.5f)
                shelfManager.addOrderToShelf(order)
                shelfManager.frozenShelf.getSize() shouldBe 1
                shelfManager.frozenShelf.getOrder(order.id) shouldBe order
            }
        }

        "adding an order that has expired" {
            val shelfManager = makeShelfManager()
            val order = PreparedOrder("Ice cream", "cold", 100, 101.0f)
            delay(1000)
            shelfManager.addOrderToShelf(order)
            shelfManager.coldShelf.getSize() shouldBe 0
            shelfManager.coldShelf.getOrder(order.id) shouldBe null
        }

        "adding more orders than shelf size should leave it in overflow" {
            withShelfManager { shelfManager ->
                for (i in 1..shelfManager.hotShelf.capacity + 1) {
                    val order = PreparedOrder("Tacos - $i", "hot", 100, 0.5f)
                    shelfManager.addOrderToShelf(order)
                }
                shelfManager.hotShelf.isFull() shouldBe true
                shelfManager.overflowShelf.getSize() shouldBe 1
            }
        }

        "should discard if overflow is full as well" {
            withShelfManager { shelfManager ->
                val totalOrders = shelfManager.frozenShelf.capacity + shelfManager.overflowShelf.capacity + 1
                for (i in 1..totalOrders) {
                    val order = PreparedOrder("Ice cream - $i", "frozen", 100, 0.5f)
                    shelfManager.addOrderToShelf(order)
                }
                shelfManager.frozenShelf.isFull() shouldBe true
                shelfManager.overflowShelf.isFull() shouldBe true
                // should have cancelled one driver because the rest of the orders were handled correctly
                fakeDispatcher.cancelledDrivers.size shouldBe 1
            }
        }

        "removing an order to from the shelf" {
            withShelfManager { shelfManager ->
                val order = PreparedOrder("Ice cream", "frozen", 100, 0.5f)
                shelfManager.addOrderToShelf(order)
                shelfManager.frozenShelf.getSize() shouldBe 1

                shelfManager.removeOrderFromShelf(order)
                shelfManager.frozenShelf.getSize() shouldBe 0
            }
        }

        "sweeping the shelf should return Shelf status" {
            withShelfManager { shelfManager ->
                val order = PreparedOrder("Ice cream", "frozen", 100, 0.5f)
                shelfManager.addOrderToShelf(order)
                val status = shelfManager.sweepShelf(shelfManager.frozenShelf)
                status.shelfName shouldBe shelfManager.frozenShelf.name
                status.orderStatuses.size shouldBe 1

                val orderStatus = status.orderStatuses[0]
                orderStatus.name shouldBe order.name
                orderStatus.value shouldBe between (90, 101)
            }
        }

        "sweeping overflow shelf should update other shelves that are not full" {
            withShelfManager { shelfManager ->
                // add more tacos than hot shelf can handle
                val orders = (1..shelfManager.hotShelf.capacity + 1).map { i ->
                    val order = PreparedOrder("Tacos - $i", "hot", 100, 0.5f)
                    shelfManager.addOrderToShelf(order)
                    order
                }

                shelfManager.hotShelf.isFull() shouldBe true
                shelfManager.overflowShelf.getSize() shouldBe 1

                val movedOrder = shelfManager.overflowShelf.getAllValues().first()
                shelfManager.removeOrderFromShelf(order = orders[0])
                shelfManager.hotShelf.isFull() shouldBe false

                shelfManager.sweepAndUpdateOverflowShelf()
                shelfManager.hotShelf.isFull() shouldBe true
                shelfManager.overflowShelf.getSize() shouldBe 0
                shelfManager.hotShelf.getOrder(movedOrder.id) shouldBe movedOrder
            }
        }
    }

}
