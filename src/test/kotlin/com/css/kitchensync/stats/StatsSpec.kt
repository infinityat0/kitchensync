package com.css.kitchensync.stats

import com.css.kitchensync.metrics.Stats
import io.kotlintest.shouldBe
import io.kotlintest.specs.StringSpec

class StatsSpec : StringSpec({

    "create a counter" {
        Stats.incr("new_counter")
        Stats.getCounter("new_counter")?.get() shouldBe 1
    }

    "increment a counter" {
        Stats.incr("counter")
        Stats.incr("counter")
        Stats.incr("counter")
        Stats.getCounter("counter")?.get() shouldBe 3
    }

    "create multiple counters" {
        Stats.incr("counter_1")
        Stats.incr("counter_1")

        Stats.incr("counter_2")
        Stats.incr("counter_2")

        Stats.getCounter("counter_1")?.get() shouldBe 2
        Stats.getCounter("counter_2")?.get() shouldBe 2
    }
})
