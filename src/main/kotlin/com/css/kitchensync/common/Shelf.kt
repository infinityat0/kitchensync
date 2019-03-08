package com.css.kitchensync.common

import com.google.common.annotations.VisibleForTesting
import com.google.common.collect.Maps

class Shelf(val name: String, private val size: Int) {

    private val shelf = Maps.newConcurrentMap<String, PreparedOrder>()

    /**
     * Attempts to push an order in the shelf.
     *  - if it fails then returns a false
     * @return boolean indicating the success of the operation
     */
    @Synchronized fun putOrder(order: PreparedOrder): Boolean {
        return if (isFull()) false else {
            shelf[order.id] = order
            true
        }
    }

    /**
     * Attempts to remove an order from the shelf
     * - if it fails returns false
     * @return boolean indicating the success of the operation
     */
    @Synchronized fun removeFromShelf(order: PreparedOrder): Boolean {
        return shelf.remove(order.id, order)
    }

    @Synchronized fun getSize() = shelf.size

    @Synchronized fun isFull() = shelf.size >= size

    @Synchronized fun getAllValues() = shelf.values

    /**
     * Attempts to clean up the shelf.
     * @return a list of items that have been removed because they have expired
     */
    @Synchronized fun cleanup(): Collection<PreparedOrder> {
        val expiredOrders = shelf.filterValues {
            it.isExpired(isInOverflowShelf = name == "overflow")
        }
        expiredOrders.forEach { id, value ->  shelf.remove(id, value) }
        return expiredOrders.values
    }

    @VisibleForTesting fun getOrder(id: String): PreparedOrder? = shelf[id]
}
