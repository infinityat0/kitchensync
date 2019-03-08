package com.css.kitchensync.common

import io.kotlintest.shouldBe
import io.kotlintest.specs.StringSpec

class ShelfSpec: StringSpec() {

    private fun makeOrder(name: String): PreparedOrder {
        return PreparedOrder(name, "hot", 300, 0.5f)
    }

    init {
        "should be able to put an order" {
            val shelf = Shelf("hot", size = 5)
            val order = makeOrder("foo")
            shelf.putOrder(order)
            shelf.getOrder(order.id) shouldBe order
        }

        "should accept orders until the max size" {
            val shelf = Shelf("hot", size = 5)
            for (i in 1..5) {
                shelf.putOrder(makeOrder("foo-$i")) shouldBe true
            }
            shelf.getSize() shouldBe 5
        }

        "should stop accepting orders after size limit is reached" {
            val shelf = Shelf("hot", size = 5)
            for (i in 1..5) {
                shelf.putOrder(makeOrder("foo-$i")) shouldBe true
            }
            shelf.putOrder(makeOrder("bar")) shouldBe false
            shelf.getSize() shouldBe 5
        }

        "should be able to remove an order from the shelf" {
            val shelf = Shelf("hot", size = 5)
            val order = makeOrder("foo")
            shelf.putOrder(order) shouldBe true
            shelf.removeFromShelf(order = order) shouldBe true
        }

        "removing an unknown order should return false" {
            val shelf = Shelf("hot", size = 5)
            val order = makeOrder("foo")
            shelf.putOrder(order) shouldBe true
            shelf.removeFromShelf(makeOrder("bar")) shouldBe false
        }

        "check size of the shelf" {
            val shelf = Shelf("hot", size = 5)
            shelf.putOrder(makeOrder("foo"))
            shelf.getSize() shouldBe 1

            shelf.putOrder(makeOrder("foo"))
            shelf.getSize() shouldBe 2
        }

        "check if shelf is full" {
            val shelf = Shelf("hot", size = 2)
            // these are distinct orders since they have different id
            shelf.putOrder(makeOrder("foo"))
            shelf.putOrder(makeOrder("foo"))

            shelf.isFull() shouldBe true
        }

        "get all values in the shelf" {
            val shelf = Shelf("hot", size = 3)
            for (i in 1..3) {
                shelf.putOrder(makeOrder("foo-$i"))
            }
            shelf.getAllValues().size shouldBe 3
        }

        "cleanup should return nothing if orders haven't expired" {
            val shelf = Shelf("hot", size = 3)
            for (i in 1..3) {
                shelf.putOrder(makeOrder("foo-$i"))
            }
            shelf.cleanup().size shouldBe 0
        }

        "cleanup should only return expired orders" {
            val shelf = Shelf("hot", size = 5)
            for (i in 1..3) {
                shelf.putOrder(makeOrder("foo-$i"))
            }
            val expiredOrder = PreparedOrder("fish", "frozen", 10, 0.5f)
            expiredOrder.lastMeasuredTimestamp = expiredOrder.preparedAt - 20_000
            expiredOrder.isExpired() shouldBe true
            shelf.putOrder(expiredOrder)

            val expiredOrders = shelf.cleanup()
            expiredOrders.size shouldBe 1
            expiredOrders.first() shouldBe expiredOrder
        }

        "order should decay when kept in it's shelf" {
            // order should decay 2/sec
            val order = PreparedOrder("Ice Cream", "frozen", shelfLife = 10, decayRate = 1.0f)
            order.computeAndAssignValue(
                computeTimeInMs = System.currentTimeMillis() + 2000,
                wasInOverflowShelf = false)
            order.valueAtLastMeasured shouldBe 6.0f
        }

        "order should decay twice as fast in overflow shelf" {
            // order should decay 2/sec
            val order = PreparedOrder("Ice Cream", "frozen", shelfLife = 10, decayRate = 1.0f)
            // when kept in overflow shelf, it should have decayed twice as much
            order.computeAndAssignValue(
                computeTimeInMs = System.currentTimeMillis() + 2000,
                wasInOverflowShelf = true)
            order.valueAtLastMeasured shouldBe 4.0f

        }

        "order should decay at normal rate after order is moved from the shelf" {
            val order = PreparedOrder("Ice Cream", "frozen", shelfLife = 12, decayRate = 1.0f)
            // when kept in overflow shelf, it should have decayed twice as much
            order.computeAndAssignValue(
                computeTimeInMs = System.currentTimeMillis() + 2000,
                wasInOverflowShelf = true)
            // in 2 seconds order would have decayed by 6 points
            order.valueAtLastMeasured shouldBe 6.0f

            // move it to it's own shelf and wait for two more seconds
            order.computeAndAssignValue(
                computeTimeInMs = order.lastMeasuredTimestamp + 2000,
                wasInOverflowShelf = false)
            // it should have decayed by 4 points
            order.valueAtLastMeasured shouldBe 2.0f
        }
    }
}
