package com.css.kitchensync.common

import io.kotlintest.matchers.numerics.shouldBeLessThanOrEqual
import io.kotlintest.shouldBe
import io.kotlintest.specs.StringSpec

/**
 * Tests for {@link Order} class
 */
class PreparedOrderSpec: StringSpec({

    "check creation of the order" {
        val order = PreparedOrder("Yogurt", "cold", 263, 0.37f)
        order.preparedAt shouldBeLessThanOrEqual System.currentTimeMillis()
    }

    "check expiry of the order" {
        val order = PreparedOrder("Yogurt", "cold", 263, 0.37f)
        order.lastMeasuredTimestamp = System.currentTimeMillis() / 1000 - 300
        order.isExpired() shouldBe true
    }

    "check current value of order should be initialValue at creationTime" {
        val now = System.currentTimeMillis()
        val initialValue = 500
        val order = PreparedOrder("Yogurt", "cold", initialValue, 0.37f)
        order.lastMeasuredTimestamp = now
        order.valueNow() shouldBe initialValue.toFloat()
    }

    // This test could be flaky but the probability is quite low
    "check current value of order" {
        val initialValue = 500
        val now = System.currentTimeMillis()
        val order = PreparedOrder("Yogurt", "cold", initialValue, 0.5f,
            valueExpression = "shelfLife - decayRate * orderAge")
        order.lastMeasuredTimestamp = now - 200_000
        order.valueNow() shouldBe 400f
    }

    "check value after orderAge" {
        val initialValue = 500
        val order = PreparedOrder("Yogurt", "cold", initialValue, 0.5f,
            valueExpression = "shelfLife - decayRate * orderAge")
        order.valueAfter(200) shouldBe 400f
    }

    "check value should be zero after long time" {
        val initialValue = 500
        val order = PreparedOrder("Yogurt", "cold", initialValue, 0.5f,
            valueExpression = "shelfLife - decayRate * orderAge")
        order.valueAfter(1000) shouldBe 0f
    }
})