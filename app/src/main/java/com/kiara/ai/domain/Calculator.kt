package com.kiara.ai.domain

import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.pow
import kotlin.math.sqrt

/** Small offline expression evaluator: + - * / % ^ ( ) sqrt() abs() */
object Calculator {
    fun eval(src: String): Double {
        val clean = src.lowercase().replace("×", "*").replace("÷", "/").replace(" ", "")
            .replace(Regex("(?<=[\\d)])x(?=[\\d(])"), "*")
        val p = Parser(clean)
        val v = p.expr()
        if (p.i != p.s.length) error("Unexpected '${p.s[p.i]}'")
        return v
    }

    fun format(v: Double): String {
        if (v.isNaN() || v.isInfinite()) return "undefined"
        if (v == floor(v) && abs(v) < 1e15) return v.toLong().toString()
        return "%.6f".format(v).trimEnd('0').trimEnd('.')
    }

    private class Parser(val s: String) {
        var i = 0

        fun expr(): Double {
            var v = term()
            while (i < s.length && (s[i] == '+' || s[i] == '-')) {
                val op = s[i++]
                val r = term()
                v = if (op == '+') v + r else v - r
            }
            return v
        }

        fun term(): Double {
            var v = power()
            while (i < s.length && (s[i] == '*' || s[i] == '/' || s[i] == '%')) {
                val op = s[i++]
                val r = power()
                v = when (op) {
                    '*' -> v * r
                    '/' -> { if (r == 0.0) error("Division by zero"); v / r }
                    else -> v % r
                }
            }
            return v
        }

        fun power(): Double {
            val b = unary()
            if (i < s.length && s[i] == '^') {
                i++
                return b.pow(power())
            }
            return b
        }

        fun unary(): Double {
            if (i < s.length && s[i] == '-') { i++; return -unary() }
            if (i < s.length && s[i] == '+') { i++; return unary() }
            return atom()
        }

        fun atom(): Double {
            if (i >= s.length) error("Incomplete expression")
            if (s[i] == '(') {
                i++
                val v = expr()
                if (i >= s.length || s[i] != ')') error("Missing )")
                i++
                return v
            }
            if (s[i].isLetter()) {
                val st = i
                while (i < s.length && s[i].isLetter()) i++
                val name = s.substring(st, i)
                if (i >= s.length || s[i] != '(') error("Unknown word '$name'")
                val arg = atom()
                return when (name) {
                    "sqrt" -> sqrt(arg)
                    "abs" -> abs(arg)
                    else -> error("Unknown function '$name'")
                }
            }
            val st = i
            while (i < s.length && (s[i].isDigit() || s[i] == '.')) i++
            if (st == i) error("Unexpected '${s[i]}'")
            return s.substring(st, i).toDouble()
        }
    }
}
