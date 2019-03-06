package com.css.kitchensync.common

import com.github.h0tk3y.betterParse.grammar.parseToEnd
import io.kotlintest.shouldBe
import io.kotlintest.specs.StringSpec

class ExpressionEvaluatorSpec : StringSpec({
    val evaluator = ExpressionEvaluator()

    "evaluator should evaluate simple expression" {
        val solution = evaluator.parseToEnd("100 - 50")
        solution shouldBe 50.0f
    }

    "evaluator should evaluate complex expression" {
        val solution = evaluator.parseToEnd("100 - (100 * 0.5)/2")
        solution shouldBe 75.0f
    }

    "evaluator should handle power" {
        val solution = evaluator.parseToEnd("(100 - (100 * 0.5))/(2^2)")
        solution shouldBe 12.5f
    }
})
