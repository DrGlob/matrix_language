package org.example.parser

class MatrixLangRuntimeException(
    val line: Int,
    val column: Int,
    override val message: String,
    cause: Throwable? = null
) : RuntimeException(message, cause)

fun runtimeError(message: String, token: Token? = null, cause: Throwable? = null): MatrixLangRuntimeException =
    MatrixLangRuntimeException(token?.line ?: 0, token?.column ?: 0, message, cause)
