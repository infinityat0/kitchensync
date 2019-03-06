package com.css.kitchensync.client

import com.beust.klaxon.Klaxon
import com.css.kitchensync.common.Order
import com.github.h0tk3y.betterParse.grammar.parser
import io.kotlintest.matchers.collections.shouldHaveSize
import io.kotlintest.matchers.collections.shouldNotHaveSize
import io.kotlintest.shouldBe
import io.kotlintest.shouldNotBe
import io.kotlintest.shouldThrow
import io.kotlintest.specs.StringSpec
import java.io.File

class JsonParserSpec : StringSpec({
    val jsonParser = Klaxon()

    "parser should create a correct Order instance" {
        val order: Order? = jsonParser.parse<Order>(
            """{
                "name": "Banana Split",
                "temp": "frozen",
                "shelfLife": 20,
                "decayRate": 0.63
                }
        """.trimIndent()
        )
        order shouldBe Order(name = "Banana Split", temp = "frozen", shelfLife = 20, decayRate = 0.63f)
        order?.valueExpression shouldBe "shelfLife - (1 + decayRate)*orderAge"
    }

    "parser should overwrite order expression" {
        val valueExpression = "shelfLife - 0.42*orderAge"
        val order: Order? = jsonParser.parse<Order>(
            """{
                "name": "Banana Split",
                "temp": "frozen",
                "shelfLife": 20,
                "decayRate": 0.63,
                "orderDecayFormula": "$valueExpression"
                }
            """.trimIndent()
        )
        order?.valueExpression shouldBe valueExpression
    }


    "parser should ignore unknown tags" {
        val valueExpression = "shelfLife - 0.42*orderAge"
        val order: Order? = jsonParser.parse<Order>(
            """{
                "name": "Banana Split",
                "temp": "frozen",
                "shelfLife": 20,
                "decayRate": 0.63,
                "orderFormula": "$valueExpression"
                }
            """.trimIndent()
        )
        // should resort to the default value expression
        order?.valueExpression shouldBe "shelfLife - (1 + decayRate)*orderAge"
    }

    "parser should parse a list of orders" {
        val orders = jsonParser.parseArray<Order>(
           """
           [
              {
                "name": "Banana Split",
                "temp": "frozen",
                "shelfLife": 20,
                "decayRate": 0.63
              },
              {
                "name": "McFlury",
                "temp": "frozen",
                "shelfLife": 375,
                "decayRate": 0.4
              },
           ]""".trimIndent()
        )
        orders?.shouldHaveSize(2)
    }

    "parser should throw an exception when failed to read to orders" {
        shouldThrow<Exception> {
            jsonParser.parse<Order>("{}")
        }
    }

    "check if we can read order file" {
        val orderFile = File("src/test/resources/orders.json")
        val orders = jsonParser.parseArray<Order>(orderFile)
        orders?.shouldNotHaveSize(0)
    }
})