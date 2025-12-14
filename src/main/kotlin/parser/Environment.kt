package org.example.parser

data class Environment(
    private val values: Map<String, Value> = emptyMap(),
    private val parent: Environment? = null
) {
    fun define(name: String, value: Value): Environment =
        Environment(values + (name to value), parent)

    fun get(name: String): Value {
        return values[name]
            ?: parent?.get(name)
            ?: throw runtimeError(
                "Undefined variable '$name'",
                Token(TokenType.IDENTIFIER, name, null, 0, 0)
            )
    }
}
