/*
 * Copyright (C) 2026 Aferil
 *
 * This file is part of Neuro Karaoke.
 *
 * Neuro Karaoke is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation, version 3.
 *
 * Neuro Karaoke is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 * PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with
 * Neuro Karaoke. If not, see <https://www.gnu.org/licenses/>.
 *
 * This file is part of the Neurolings feature, ported from Shimeji-ee (maintained
 * by Kilkakon; original Shimeji by Yuki Yamada / Group Finity), which is under a
 * permissive BSD/zlib-style license. See THIRD_PARTY_NOTICES.md.
 */

package com.soul.neurokaraoke.ui.tv.neurolings

/**
 * Pure-Kotlin recursive-descent evaluator for the small EL-subset used by Shimeji
 * behavior/action definitions (e.g. `${mascot.lookRight ? -10 : 10}`,
 * `500+Math.random()*1000`, `mascot.environment.floor.isOn(mascot.anchor)`).
 *
 * No Android APIs are used here so this can be unit-tested on the plain JVM.
 */
object ShimejiExpr {

    /** Sentinel for unresolved cursor/IE/target references. */
    val MISSING: Any = Any()

    /** Supplies variable and method values referenced by an expression. */
    interface Scope {
        fun variable(path: String): Any?
        fun method(path: String, args: List<Any?>): Any?
    }

    fun eval(expr: String, scope: Scope): Any? {
        val stripped = stripBraces(expr)
        val parser = Parser(stripped, scope)
        val result = parser.parseExpression()
        parser.skipWhitespace()
        return result
    }

    fun evalDouble(expr: String, scope: Scope, default: Double = 0.0): Double {
        val result = eval(expr, scope)
        return toDoubleOrNull(result) ?: default
    }

    fun evalBoolean(expr: String, scope: Scope): Boolean {
        val result = eval(expr, scope)
        return toBoolean(result)
    }

    private fun stripBraces(expr: String): String {
        var s = expr.trim()
        // Strip all EL delimiter markers (${, #{, and }) anywhere in the expression.
        // This handles combined conditions like "(#{cond1}) && (#{cond2})".
        s = s.replace("\${", "").replace("#{", "").replace("}", "")
        return s.trim()
    }

    private fun toBoolean(value: Any?): Boolean = when (value) {
        null -> false
        MISSING -> false
        is Boolean -> value
        is Double -> value != 0.0
        else -> false
    }

    private fun toDoubleOrNull(value: Any?): Double? = when (value) {
        is Double -> value
        is Boolean -> if (value) 1.0 else 0.0
        else -> null
    }

    private fun isNumeric(value: Any?): Boolean = value is Double

    /** Recursive-descent parser + evaluator over the expression grammar. */
    private class Parser(private val s: String, private val scope: Scope) {
        private var pos = 0

        fun skipWhitespace() {
            while (pos < s.length && s[pos].isWhitespace()) pos++
        }

        private fun peekChar(): Char? {
            skipWhitespace()
            return if (pos < s.length) s[pos] else null
        }

        private fun consumeChar(c: Char): Boolean {
            skipWhitespace()
            if (pos < s.length && s[pos] == c) {
                pos++
                return true
            }
            return false
        }

        private fun consumeString(str: String): Boolean {
            skipWhitespace()
            if (s.regionMatches(pos, str, 0, str.length)) {
                pos += str.length
                return true
            }
            return false
        }

        private fun matchAt(str: String): Boolean {
            skipWhitespace()
            return s.regionMatches(pos, str, 0, str.length)
        }

        // expression := ternary
        fun parseExpression(): Any? = parseTernary()

        // ternary := or ('?' expression ':' expression)?
        private fun parseTernary(): Any? {
            val cond = parseOr()
            skipWhitespace()
            if (consumeChar('?')) {
                val whenTrue = parseExpression()
                if (!consumeChar(':')) {
                    throw IllegalArgumentException("Expected ':' in ternary at pos $pos in \"$s\"")
                }
                val whenFalse = parseExpression()
                return if (toBoolean(cond)) whenTrue else whenFalse
            }
            return cond
        }

        // or := and ('||' and)*
        private fun parseOr(): Any? {
            var left = parseAnd()
            while (true) {
                skipWhitespace()
                if (matchAt("||")) {
                    pos += 2
                    val right = parseAnd()
                    left = toBoolean(left) || toBoolean(right)
                } else break
            }
            return left
        }

        // and := equality ('&&' equality)*
        private fun parseAnd(): Any? {
            var left = parseEquality()
            while (true) {
                skipWhitespace()
                if (matchAt("&&")) {
                    pos += 2
                    val right = parseEquality()
                    left = toBoolean(left) && toBoolean(right)
                } else break
            }
            return left
        }

        // equality := comparison (('==' | '!=') comparison)*
        private fun parseEquality(): Any? {
            var left = parseComparison()
            while (true) {
                skipWhitespace()
                if (matchAt("==")) {
                    pos += 2
                    val right = parseComparison()
                    left = valuesEqual(left, right)
                } else if (matchAt("!=")) {
                    pos += 2
                    val right = parseComparison()
                    left = logicalNot(valuesEqual(left, right))
                } else break
            }
            return left
        }

        private fun logicalNot(a: Any): Any {
            if (a === MISSING) return MISSING
            return !(a as Boolean)
        }

        private fun valuesEqual(a: Any?, b: Any?): Any {
            if (a === MISSING || b === MISSING) return MISSING
            val da = toDoubleOrNull(a)
            val db = toDoubleOrNull(b)
            if (da != null && db != null) return da == db
            return a == b
        }

        // comparison := additive (('<' | '<=' | '>' | '>=') additive)*
        private fun parseComparison(): Any? {
            var left = parseAdditive()
            while (true) {
                skipWhitespace()
                if (matchAt("<=")) {
                    pos += 2
                    val right = parseAdditive()
                    left = numericCompare(left, right) { a, b -> a <= b }
                } else if (matchAt(">=")) {
                    pos += 2
                    val right = parseAdditive()
                    left = numericCompare(left, right) { a, b -> a >= b }
                } else if (matchAt("<")) {
                    pos += 1
                    val right = parseAdditive()
                    left = numericCompare(left, right) { a, b -> a < b }
                } else if (matchAt(">")) {
                    pos += 1
                    val right = parseAdditive()
                    left = numericCompare(left, right) { a, b -> a > b }
                } else break
            }
            return left
        }

        private inline fun numericCompare(a: Any?, b: Any?, op: (Double, Double) -> Boolean): Any {
            if (a === MISSING || b === MISSING) return MISSING
            val da = toDoubleOrNull(a) ?: return MISSING
            val db = toDoubleOrNull(b) ?: return MISSING
            return op(da, db)
        }

        // additive := multiplicative (('+' | '-') multiplicative)*
        private fun parseAdditive(): Any? {
            var left = parseMultiplicative()
            while (true) {
                skipWhitespace()
                if (matchAt("+")) {
                    pos += 1
                    val right = parseMultiplicative()
                    left = numericOp(left, right) { a, b -> a + b }
                } else if (matchAt("-")) {
                    // Guard: don't consume '-' if it's not a binary op position (always is here).
                    pos += 1
                    val right = parseMultiplicative()
                    left = numericOp(left, right) { a, b -> a - b }
                } else break
            }
            return left
        }

        // multiplicative := unary (('*' | '/') unary)*
        private fun parseMultiplicative(): Any? {
            var left = parseUnary()
            while (true) {
                skipWhitespace()
                if (matchAt("*")) {
                    pos += 1
                    val right = parseUnary()
                    left = numericOp(left, right) { a, b -> a * b }
                } else if (matchAt("/")) {
                    pos += 1
                    val right = parseUnary()
                    left = numericOp(left, right) { a, b -> a / b }
                } else break
            }
            return left
        }

        private inline fun numericOp(a: Any?, b: Any?, op: (Double, Double) -> Double): Any {
            if (a === MISSING || b === MISSING) return MISSING
            val da = toDoubleOrNull(a) ?: return MISSING
            val db = toDoubleOrNull(b) ?: return MISSING
            return op(da, db)
        }

        // unary := ('!' | '-') unary | primary
        private fun parseUnary(): Any? {
            skipWhitespace()
            if (consumeChar('!')) {
                val v = parseUnary()
                return !toBoolean(v)
            }
            if (matchAt("-")) {
                pos += 1
                val v = parseUnary()
                if (v === MISSING) return MISSING
                val d = toDoubleOrNull(v) ?: return MISSING
                return -d
            }
            return parsePrimary()
        }

        // primary := number | 'true' | 'false' | '(' expression ')' |
        //            'Math.random()' | 'Math.abs(' expr ')' | dottedId ('(' args ')')?
        private fun parsePrimary(): Any? {
            skipWhitespace()
            if (pos >= s.length) {
                throw IllegalArgumentException("Unexpected end of expression: \"$s\"")
            }
            val c = s[pos]

            if (c == '(') {
                pos++
                val v = parseExpression()
                if (!consumeChar(')')) {
                    throw IllegalArgumentException("Expected ')' at pos $pos in \"$s\"")
                }
                return v
            }

            if (c.isDigit() || (c == '.' && pos + 1 < s.length && s[pos + 1].isDigit())) {
                return parseNumber()
            }

            if (c.isLetter() || c == '_') {
                return parseIdentifierOrCall()
            }

            throw IllegalArgumentException("Unexpected character '$c' at pos $pos in \"$s\"")
        }

        private fun parseNumber(): Double {
            val start = pos
            while (pos < s.length && (s[pos].isDigit() || s[pos] == '.')) pos++
            val text = s.substring(start, pos)
            return text.toDouble()
        }

        private fun parseIdentifier(): String {
            val start = pos
            while (pos < s.length && (s[pos].isLetterOrDigit() || s[pos] == '_' || s[pos] == '.')) {
                pos++
            }
            return s.substring(start, pos)
        }

        private fun parseIdentifierOrCall(): Any? {
            val id = parseIdentifier()
            when (id) {
                "true" -> return true
                "false" -> return false
                "Math.random" -> {
                    skipWhitespace()
                    if (consumeChar('(')) {
                        if (!consumeChar(')')) {
                            throw IllegalArgumentException("Expected ')' after Math.random( at pos $pos")
                        }
                        return Math.random()
                    }
                    // Fall through: treat as plain variable lookup if no call parens.
                    return scope.variable(id)
                }
                "Math.abs" -> {
                    skipWhitespace()
                    if (consumeChar('(')) {
                        val arg = parseExpression()
                        if (!consumeChar(')')) {
                            throw IllegalArgumentException("Expected ')' after Math.abs( at pos $pos")
                        }
                        val d = toDoubleOrNull(arg) ?: return MISSING
                        return Math.abs(d)
                    }
                    return scope.variable(id)
                }
            }

            skipWhitespace()
            if (consumeChar('(')) {
                val args = mutableListOf<Any?>()
                skipWhitespace()
                if (!matchAt(")")) {
                    args.add(parseExpression())
                    skipWhitespace()
                    while (consumeChar(',')) {
                        args.add(parseExpression())
                        skipWhitespace()
                    }
                }
                if (!consumeChar(')')) {
                    throw IllegalArgumentException("Expected ')' after call args at pos $pos in \"$s\"")
                }
                return scope.method(id, args)
            }

            return scope.variable(id)
        }
    }
}
