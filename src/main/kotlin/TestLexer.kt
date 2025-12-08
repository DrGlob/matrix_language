// TestLexer.kt
package org.example

import org.example.parser.Lexer


fun main() {
    val code = "for (i in 1..5) { print(i); }"
    println("Testing: $code")

    val lexer = Lexer(code)
    val tokens = lexer.scanTokens()

    println("Tokens found:")
    tokens.forEach { token ->
        println("  ${token.type} ('${token.lexeme}') at ${token.line}:${token.column}")
    }
}
