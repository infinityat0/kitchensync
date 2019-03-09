package com.css.kitchensync.service

import com.css.kitchensync.common.PreparedOrder
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch


class FakeDispatcher: DriverDispatchService {

    val dispatchedOrders = mutableListOf<PreparedOrder>()
    val cancelledDrivers = mutableListOf<PreparedOrder>()

    override fun initialize(): Job = GlobalScope.launch { }

    override fun cancelDriverForOrder(order: PreparedOrder) {
        cancelledDrivers.add(order)
    }

    override fun dispatchDriver(order: PreparedOrder) {
        dispatchedOrders.add(order)
    }
}
