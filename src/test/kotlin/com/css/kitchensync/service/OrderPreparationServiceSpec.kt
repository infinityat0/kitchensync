package com.css.kitchensync.service

import com.css.kitchensync.common.Order
import com.css.kitchensync.common.PreparedOrder
import com.css.kitchensync.testConfig
import io.kotlintest.shouldBe
import io.kotlintest.specs.StringSpec
import kotlinx.coroutines.*

class OrderPreparationServiceSpec : StringSpec() {

    private fun withDispatcher(func: (FakeDispatcher) -> Unit) {
        func(FakeDispatcher())
    }

    private fun withKitchen(dispatcher: FakeDispatcher, f: (OrderPreparationService) -> Unit) {
        val kitchen = OrderPreparationService(testConfig(), dispatcher)
        runBlocking {
            kitchen.initialize()
            f(kitchen)
        }
    }

    private fun makeOrder(name: String): Order = Order(name, "hot", 250, 0.5f)

    init{
        "kitchen should prepare orders that are in the pipeline" {
            withDispatcher { fakeDispatcher ->
                withKitchen(fakeDispatcher) { kitchen ->
                    runBlocking {
                        val order = makeOrder("Banana Split")
                        kitchen.prepareOrder(order)
                        delay(100)
                        fakeDispatcher.dispatchedOrders.first().name shouldBe order.name
                    }
                }
            }
        }
    }

    class FakeDispatcher: DriverDispatchService {

        val dispatchedOrders = mutableListOf<PreparedOrder>()

        override fun initialize(): Job = GlobalScope.launch { }

        override fun cancelDriverForOrder(order: PreparedOrder) {
            // TODO("not implemented")
        }

        override fun dispatchDriver(order: PreparedOrder) {
            dispatchedOrders.add(order)
        }
    }
}
