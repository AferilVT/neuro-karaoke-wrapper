package com.soul.neurokaraoke

import com.soul.neurokaraoke.ui.tv.neurolings.ShimejiExpr
import com.soul.neurokaraoke.ui.tv.neurolings.ShimejiExpr.MISSING
import org.junit.Assert.*
import org.junit.Test

class ShimejiExprTest {
    private fun scope(vars: Map<String, Any?> = emptyMap(),
                      methods: Map<String, Any?> = emptyMap()) = object : ShimejiExpr.Scope {
        override fun variable(path: String): Any? = if (path in vars) vars[path] else MISSING
        override fun method(path: String, args: List<Any?>): Any? = methods[path] ?: MISSING
    }

    @Test fun arithmetic_precedence() =
        assertEquals(7.0, ShimejiExpr.evalDouble("1 + 2 * 3", scope()), 1e-9)

    @Test fun duration_expression_range() {
        val v = ShimejiExpr.evalDouble("500+Math.random()*1000", scope())
        assertTrue(v in 500.0..1500.0)
    }

    @Test fun ternary_on_lookRight() =
        assertEquals(-10.0, ShimejiExpr.evalDouble("mascot.lookRight ? -10 : 10",
            scope(vars = mapOf("mascot.lookRight" to true))), 1e-9)

    @Test fun comparison_true() =
        assertTrue(ShimejiExpr.evalBoolean("mascot.totalCount < 50",
            scope(vars = mapOf("mascot.totalCount" to 3.0))))

    @Test fun isOn_method_true() =
        assertTrue(ShimejiExpr.evalBoolean("mascot.environment.floor.isOn(mascot.anchor)",
            scope(vars = mapOf("mascot.anchor" to Any()),
                  methods = mapOf("mascot.environment.floor.isOn" to true))))

    @Test fun cursor_reference_is_false() =
        assertFalse(ShimejiExpr.evalBoolean("mascot.environment.cursor.y < mascot.environment.screen.height/2",
            scope(vars = mapOf("mascot.environment.screen.height" to 1080.0))))

    @Test fun logical_and_or_not() {
        val s = scope(vars = mapOf("a" to true, "b" to false))
        assertTrue(ShimejiExpr.evalBoolean("a && !b", s))
        assertFalse(ShimejiExpr.evalBoolean("a && b", s))
        assertTrue(ShimejiExpr.evalBoolean("a || b", s))
    }

    @Test fun hashbrace_and_dollarbrace_stripped() =
        assertEquals(4.0, ShimejiExpr.evalDouble("\${2+2}", scope()), 1e-9)

    @Test fun combined_conditions_with_embedded_delimiters() {
        val s = object : ShimejiExpr.Scope {
            override fun variable(path: String): Any? = when (path) {
                "mascot.totalCount" -> 3.0
                "mascot.anchor" -> Any()
                else -> MISSING
            }
            override fun method(path: String, args: List<Any?>): Any? =
                if (path == "mascot.environment.floor.isOn") true else MISSING
        }
        assertTrue(ShimejiExpr.evalBoolean(
            "(#{mascot.totalCount < 50}) && (#{mascot.environment.floor.isOn(mascot.anchor)})", s))
    }
}
