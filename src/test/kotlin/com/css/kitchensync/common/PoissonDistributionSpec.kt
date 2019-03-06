package com.css.kitchensync.common

import io.kotlintest.matchers.between
import io.kotlintest.shouldBe
import io.kotlintest.specs.StringSpec
import org.apache.commons.math3.distribution.PoissonDistribution

class PoissonDistributionSpec : StringSpec({
    "distribution should generate 100 samples with a known mean" {
        val lambda = 300.toDouble()
        val distribution = PoissonDistribution(lambda)
        val samples = (1..100).map { distribution.sample() }
        // This is not really guaranteed but more often than not, it is in that range
        samples.average() shouldBe between (lambda.toInt() - 5, lambda.toInt() + 5)
    }
})