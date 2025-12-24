package cli

import org.example.parser.Expr
import org.example.parser.Interpreter
import org.example.parser.Lexer
import org.example.parser.MatrixLangRuntimeException
import org.example.parser.Parser
import org.example.parser.Stmt
import org.example.parser.Token
import org.example.parser.TokenType
import org.example.type.formatType
import org.example.type.inferType
import planner.GraphNode
import planner.Plan
import planner.Planner
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

            val trimmed = line.trim()
            if (trimmed.startsWith(":type")) {
                handleTypeCommand(trimmed.removePrefix(":type").trim(), interpreter)
                hadError = false
                hadRuntimeError = false
                continue
            }
            if (trimmed.startsWith(":plan")) {
                handlePlanCommand(trimmed.removePrefix(":plan").trim())
                hadError = false
                hadRuntimeError = false
                continue
            }

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

    private fun handleTypeCommand(source: String, interpreter: Interpreter) {
        if (source.isBlank()) {
            println("Usage: :type <expr>")
            return
        }
        try {
            val expr = parseExpression(source)
            val type = inferType(expr, interpreter.typeEnvironment)
            println(formatType(type))
        } catch (e: MatrixLangRuntimeException) {
            println("Type error at line ${e.line}, column ${e.column}: ${e.message}")
        } catch (e: Exception) {
            println("Type error: ${e.message}")
        }
    }

    private fun handlePlanCommand(source: String) {
        if (source.isBlank()) {
            println("Usage: :plan <expr>")
            return
        }
        try {
            val expr = parseExpression(source)
            val plan = Planner().plan(expr)
            println(renderPlan(plan))
        } catch (e: MatrixLangRuntimeException) {
            println("Plan error at line ${e.line}, column ${e.column}: ${e.message}")
        } catch (e: Exception) {
            println("Plan error: ${e.message}")
        }
    }

    private fun parseExpression(source: String): Expr {
        val normalized = if (source.trimEnd().endsWith(";")) source else "$source;"
        val lexer = Lexer(normalized)
        val parser = Parser(lexer.scanTokens())
        val statements = parser.parse()
        if (statements.size != 1 || statements.first() !is Stmt.Expression) {
            throw IllegalArgumentException("Expected a single expression")
        }
        return (statements.first() as Stmt.Expression).expression
    }

    private fun renderPlan(plan: Plan): String {
        val lines = mutableListOf<String>()
        lines.add("Plan root: ${plan.root.id}")

        val ordered = topoSort(plan.root)
        lines.add("Nodes:")
        for (node in ordered) {
            val deps = if (node.inputs.isEmpty()) "-" else node.inputs.joinToString(",") { it.id }
            val summary = buildNodeSummary(node)
            lines.add("- ${node.id} ${summary} deps=[${deps}]")
        }

        if (plan.warnings.isNotEmpty()) {
            lines.add("Warnings:")
            plan.warnings.forEach { lines.add("- $it") }
        }
        return lines.joinToString("\n")
    }

    private fun topoSort(root: GraphNode): List<GraphNode> {
        val ordered = mutableListOf<GraphNode>()
        val visited = mutableSetOf<String>()

        fun visit(node: GraphNode) {
            if (!visited.add(node.id)) return
            node.inputs.forEach { visit(it) }
            ordered.add(node)
        }

        visit(root)
        return ordered
    }

    private fun buildNodeSummary(node: GraphNode): String {
        val kind = when (node) {
            is GraphNode.ValueNode -> "Value(${node.label})"
            is GraphNode.AddNode -> "Add"
            is GraphNode.MulNode -> "Mul"
            is GraphNode.ScaleNode -> "Scale"
            is GraphNode.MapNode -> "Map"
            is GraphNode.ReduceNode -> "Reduce"
            is GraphNode.ZipNode -> "Zip"
            is GraphNode.UnzipNode -> "Unzip"
        }
        val meta = node.meta
        val metaParts = mutableListOf<String>()
        meta.rows?.let { metaParts.add("rows=$it") }
        meta.cols?.let { metaParts.add("cols=$it") }
        meta.length?.let { metaParts.add("len=$it") }
        val metaSuffix = if (metaParts.isNotEmpty()) " meta{${metaParts.joinToString(",")}}" else ""
        return "$kind$metaSuffix"
    }
}
