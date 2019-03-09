package com.css.kitchensync.common

import com.google.common.collect.Maps

class Shelf(val name: String, val capacity: Int) {

    private val map = Maps.newConcurrentMap<String, PreparedOrder>()

    /**
     * Attempts to push an order in the shelf.
     *  - if it fails then returns a false
     * @return boolean indicating the success of the operation
     */
    @Synchronized fun putOrder(order: PreparedOrder): Boolean {
        return if (isFull()) false else {
            map[order.id] = order
            true
        }
    }

    /**
     * Attempts to remove an order from the shelf
     * - if it fails returns false
     * @return boolean indicating the success of the operation
     */
    @Synchronized fun removeFromShelf(order: PreparedOrder): Boolean {
        return map.remove(order.id, order)
    }

    @Synchronized fun getSize() = map.size

    @Synchronized fun isFull() = map.size >= capacity

    @Synchronized fun getAllValues() = map.values

    /**
     * Attempts to clean up the shelf.
     * @return a list of items that have been removed because they have expired
     */
    @Synchronized fun cleanup(): Collection<PreparedOrder> {
        val expiredOrders = map.filterValues {
            it.isExpired(isInOverflowShelf = name == "overflow")
        }
        expiredOrders.forEach { id, value ->  map.remove(id, value) }
        return expiredOrders.values
    }

    internal fun getOrder(id: String): PreparedOrder? = map[id]
}
