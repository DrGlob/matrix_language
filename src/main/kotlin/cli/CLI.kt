package cli

import org.example.parser.Interpreter
import org.example.parser.Lexer
import org.example.parser.MatrixLangRuntimeException
import org.example.parser.Parser
import org.example.parser.Token
import org.example.parser.TokenType
import java.io.File
import kotlin.system.exitProcess

object FileRunner {
    fun runFile(path: String) {
        val file = File(path)
        if (!file.exists()) {
            println("File not found: $path")
            exitProcess(66)
        }

        try {
            val source = file.readText()
            run(source, Interpreter())

            if (hadError) exitProcess(65)
            if (hadRuntimeError) exitProcess(70)
        } catch (e: Exception) {
            println("Error reading file: ${e.message}")
            exitProcess(1)
        }
    }

    fun runPrompt() {
        println("Matrix DSL REPL")
        println("Type 'exit' to quit")

        val interpreter = Interpreter()

        while (true) {
            print("> ")
            val line = readLine() ?: break

            if (line.trim() == "exit") break

            run(line, interpreter)
            hadError = false
            hadRuntimeError = false
        }
    }

    fun run(source: String, interpreter: Interpreter = Interpreter()) {
        val lexer = Lexer(source)
        val tokens = lexer.scanTokens()

        val parser = Parser(tokens)
        val statements = parser.parse()

        if (hadError) return

        try {
            val lastValue = interpreter.executePipeline(statements)
            if (statements.size == 1 && statements.first() is org.example.parser.Stmt.Expression) {
                if (lastValue != null) {
                    println(interpreter.evaluator.stringify(lastValue))
                }
            }
        } catch (e: MatrixLangRuntimeException) {
            println("Runtime error at line ${e.line}, column ${e.column}: ${e.message}")
            hadRuntimeError = true
        }
    }

    fun error(line: Int, column: Int, message: String) {
        report(line, column, "", message)
    }

    fun error(token: Token, message: String) {
        if (token.type == TokenType.EOF) {
            report(token.line, token.column, " at end", message)
        } else {
            report(token.line, token.column, " at '${token.lexeme}'", message)
        }
    }

    private fun report(line: Int, column: Int, where: String, message: String) {
        println("[line $line:$column] Error$where: $message")
        hadError = true
    }

//    fun runtimeError(error: Interpreter.RuntimeError) {
//        println("${error.message}\n[line ${error.token.line}:${error.token.column}]")
//        hadRuntimeError = true
//    }

    private var hadError = false
    private var hadRuntimeError = false
}
