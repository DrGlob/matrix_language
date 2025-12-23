package org.example.type

import org.example.parser.runtimeError

/**
 * Типы DSL для статической проверки.
 */
sealed interface Type

object NumberType : Type
object BoolType : Type
object StringType : Type
object MatrixType : Type
object VectorType : Type
data class ListType(val elementType: Type) : Type
data class PairType(val first: Type, val second: Type) : Type
data class FunctionType(
    val paramTypes: List<Type>,
    val returnType: Type
) : Type
object UnitType : Type

/**
 * Иммутабельное окружение типов (lexical scope).
 */
class TypeEnv(
    private val bindings: Map<String, Type> = emptyMap(),
    private val parent: TypeEnv? = null
) {
    fun lookup(name: String): Type {
        return bindings[name]
            ?: parent?.lookup(name)
            ?: throw runtimeError("type error: unknown variable '$name'", null)
    }

    fun extend(name: String, type: Type): TypeEnv =
        TypeEnv(mapOf(name to type), this)
}
