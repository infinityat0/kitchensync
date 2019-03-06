package com.css.kitchensync.common

import io.kotlintest.shouldBe
import io.kotlintest.specs.StringSpec

class OrderSpec: StringSpec({

    "check order should be invalid: Bad Temperature" {
        val order = Order("Banana Split", "Mordor", 100, 0.45f)
        order.isValid() shouldBe false
    }

    "check order should be invalid: Bad shelf life" {
        val order = Order("Banana Split", "hot", -10, 0.45f)
        order.isValid() shouldBe false
    }

    "check order should be invalid: Bad decay rate" {
        val order = Order("Banana Split", "hot", 10, -0.45f)
        order.isValid() shouldBe false
    }

    "check order should be invalid: Bad value expression " {
        val order = Order(
            name = "Banana Split",
            temp = "hot",
            shelfLife = 10,
            decayRate = 0.45f,
            valueExpression = "shelfLife - (1 + decayRate)*orderTotalAge")
        // orderTotalAge is not a variable in the formula
        order.isValid() shouldBe false
    }

    "check order should be valid" {
        val order = Order(
            name = "Banana Split",
            temp = "hot",
            shelfLife = 10,
            decayRate = 0.45f,
            valueExpression = "shelfLife - (1 + decayRate)*orderAge")
        order.isValid() shouldBe true
    }

    "check order should be valid with new decay formula" {
        val order = Order(
            name = "Banana Split",
            temp = "hot",
            shelfLife = 10,
            decayRate = 0.45f,
            valueExpression = "shelfLife - decayRate * 2 * orderAge")
        order.isValid() shouldBe true
    }
})
