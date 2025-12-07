package org.example.dsl

import org.example.core.Matrix

class MatrixContext {
    private val variables: MutableMap<String, Matrix> = mutableMapOf()

    fun declare(name: String, value: Matrix) {
        variables[name] = value
        println("Declared $name = $value")
    }

    operator fun get(name: String): Matrix {
        return variables[name] ?: throw IllegalArgumentException("Variable $name not found")
    }

    operator fun set(name: String, value: Matrix) {
        variables[name] = value
    }

    fun clear() {
        variables.clear()
    }

    fun getAllVariables(): Map<String, Matrix> = variables.toMap()
}