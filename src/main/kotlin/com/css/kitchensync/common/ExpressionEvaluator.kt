package com.css.kitchensync.common

import com.github.h0tk3y.betterParse.combinators.*
import com.github.h0tk3y.betterParse.grammar.Grammar
import com.github.h0tk3y.betterParse.grammar.parser
import com.github.h0tk3y.betterParse.parser.Parser

/**
 * Evaluates an arithmetic expression and spits out a value.
 * Example from com.github.h0tk3y.betterParse slightly modified
 * to handle fractional algebra
 */
class ExpressionEvaluator : Grammar<Float>() {
    private val num by token("([+-]?(\\d+\\.)?\\d+)")
    private val lpar by token("\\(")
    private val rpar by token("\\)")
    private val mul by token("\\*")
    private val pow by token("\\^")
    private val div by token("/")
    private val minus by token("-")
    private val plus by token("\\+")
    private val ws by token("\\s+", ignore = true)

    private val number by num use { text.toFloat() }
    private val term: Parser<Float> by number or
            (skip(minus) and parser(this::term) map { -it }) or
            (skip(lpar) and parser(this::rootParser) and skip(rpar))

    private val powChain by leftAssociative(term, pow) { a, _, b ->
        Math.pow(a.toDouble(), b.toDouble()).toFloat()
    }

    private val divMulChain by leftAssociative(powChain, div or mul use { type }) { a, op, b ->
        if (op == div) a / b else a * b
    }

    private val subSumChain by leftAssociative(divMulChain, plus or minus use { type }) { a, op, b ->
        if (op == plus) a + b else a - b
    }

    override val rootParser: Parser<Float> by subSumChain
}