package com.css.kitchensync.service

import com.css.kitchensync.common.PreparedOrder
import com.css.kitchensync.testConfig
import io.kotlintest.shouldBe
import io.kotlintest.specs.StringSpec
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking

class ShelfManagerSpec: StringSpec () {

    private val shelfManager = ShelfManager(
        kitchenSyncConfig = testConfig(),
        dispatcher = RandomTimeDriverDispatcher()
    )

    private fun addOrderToShelf(order: PreparedOrder) = runBlocking {
        ShelfManager.preparedOrdersChannel.send(order)
    }

    @ExperimentalCoroutinesApi
    private fun waitTillChannelIsEmpty() {
        while(!ShelfManager.preparedOrdersChannel.isEmpty) { }
    }

    init {
//        "adding order to the shelf should increase shelf size" {
//            shelfManager.initialize()
//            for (i in 1..20) {
//                addOrderToShelf(PreparedOrder("foo-$i", "hot", 100, 0.5f))
//            }
//            waitTillChannelIsEmpty()
//            shelfManager.hotShelf.getSize() shouldBe 15
//            shelfManager.overflowShelf.getSize() shouldBe 5
//        }

    }

}