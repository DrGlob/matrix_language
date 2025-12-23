package org.example.type

import org.example.parser.Expr
import org.example.parser.MatrixLangRuntimeException
import org.example.parser.Token
import org.example.parser.TokenType
import org.example.parser.runtimeError

fun inferType(expr: Expr, env: TypeEnv): Type = infer(expr, env, null)

private fun infer(expr: Expr, env: TypeEnv, expectedLambdaParams: List<Type>?): Type {
    return when (expr) {
        is Expr.NumberLiteral -> NumberType
        is Expr.StringLiteral -> StringType
        is Expr.MatrixLiteral -> MatrixType
        is Expr.Variable -> env.lookup(expr.name)
        is Expr.Assign -> throw runtimeError(
            "type error: reassignment is not supported",
            Token(TokenType.IDENTIFIER, expr.name, null, 0, 0)
        )
        is Expr.IfExpr -> {
            val condType = infer(expr.condition, env, null)
            if (condType != BoolType) {
                throw typeError(BoolType, condType, token = null)
            }
            val thenType = infer(expr.thenBranch, env, null)
            val elseType = infer(expr.elseBranch, env, null)
            if (thenType != elseType) {
                throw typeError(thenType, elseType, token = null)
            }
            thenType
        }
        is Expr.LetInExpr -> {
            val boundType = infer(expr.boundExpr, env, null)
            val scoped = env.extend(expr.name, boundType)
            infer(expr.bodyExpr, scoped, null)
        }
        is Expr.Unary -> {
            val right = infer(expr.right, env, null)
            if (expr.operator.type != TokenType.MINUS) {
                throw runtimeError("type error: unknown unary operator", expr.operator)
            }
            if (right != NumberType) {
                throw typeError(NumberType, right, expr.operator)
            }
            NumberType
        }
        is Expr.Binary -> inferBinary(expr, env)
        is Expr.LambdaExpr -> inferLambda(expr, env, expectedLambdaParams)
        is Expr.CallExpr -> inferCall(expr, env)
        is Expr.MethodCallExpr -> inferMethodCall(expr, env)
        is Expr.PropertyAccess -> inferProperty(expr, env)
        is Expr.Function -> {
            val params = expr.params.map { NumberType }
            FunctionType(params, UnitType)
        }
        else -> UnitType
    }
}

private fun inferBinary(expr: Expr.Binary, env: TypeEnv): Type {
    val left = infer(expr.left, env, null)
    val right = infer(expr.right, env, null)

    return when (expr.operator.type) {
        TokenType.PLUS -> when {
            left == NumberType && right == NumberType -> NumberType
            left == MatrixType && right == MatrixType -> MatrixType
            left == MatrixType && right == NumberType -> MatrixType
            left == NumberType && right == MatrixType -> MatrixType
            else -> throw typeError(
                expected = "Number or Matrix",
                actual = right,
                token = expr.operator
            )
        }
        TokenType.MINUS -> when {
            left == NumberType && right == NumberType -> NumberType
            left == MatrixType && right == MatrixType -> MatrixType
            else -> throw typeError(
                expected = "Number or Matrix",
                actual = right,
                token = expr.operator
            )
        }
        TokenType.MULTIPLY -> when {
            left == NumberType && right == NumberType -> NumberType
            left == MatrixType && right == MatrixType -> MatrixType
            left == MatrixType && right == NumberType -> MatrixType
            left == NumberType && right == MatrixType -> MatrixType
            else -> throw typeError(
                expected = "Number or Matrix",
                actual = right,
                token = expr.operator
            )
        }
        TokenType.DIVIDE -> {
            if (left != NumberType || right != NumberType) {
                throw typeError(NumberType, if (left != NumberType) left else right, expr.operator)
            }
            NumberType
        }
        TokenType.EQ, TokenType.NEQ -> {
            if (left != right) {
                throw typeError(left, right, expr.operator)
            }
            BoolType
        }
        TokenType.LT, TokenType.GT, TokenType.LTE, TokenType.GTE -> {
            if (left != NumberType || right != NumberType) {
                throw typeError(NumberType, if (left != NumberType) left else right, expr.operator)
            }
            BoolType
        }
        else -> throw runtimeError("type error: unknown operator", expr.operator)
    }
}

private fun inferLambda(expr: Expr.LambdaExpr, env: TypeEnv, expectedParams: List<Type>?): Type {
    val params = expr.params
    val paramTypes = expectedParams ?: params.map { NumberType }
    if (paramTypes.size != params.size) {
        throw runtimeError("type error: lambda parameter count mismatch", token = null)
    }
    var scoped = env
    for ((name, type) in params.zip(paramTypes)) {
        scoped = scoped.extend(name, type)
    }
    val bodyType = infer(expr.body, scoped, null)
    return FunctionType(paramTypes, bodyType)
}

private fun inferCall(expr: Expr.CallExpr, env: TypeEnv): Type {
    val callee = expr.callee
    if (callee is Expr.Variable) {
        return when (callee.name) {
            "map" -> inferMap(expr.args, env)
            "reduce" -> inferReduce(expr.args, env)
            "zip" -> inferZip(expr.args, env)
            "unzip" -> inferUnzip(expr.args, env)
            else -> inferCallByType(expr, env)
        }
    }
    return inferCallByType(expr, env)
}

private fun inferCallByType(expr: Expr.CallExpr, env: TypeEnv): Type {
    val calleeType = infer(expr.callee, env, null)
    val fnType = calleeType as? FunctionType
        ?: throw typeError("Function", calleeType, token = dummyToken("call"))
    if (fnType.paramTypes.size != expr.args.size) {
        throw runtimeError("type error: expected ${fnType.paramTypes.size} arguments, got ${expr.args.size}")
    }
    for ((argExpr, paramType) in expr.args.zip(fnType.paramTypes)) {
        val argType = infer(argExpr, env, null)
        if (argType != paramType) {
            throw typeError(paramType, argType, token = null)
        }
    }
    return fnType.returnType
}

private fun inferMethodCall(expr: Expr.MethodCallExpr, env: TypeEnv): Type {
    return when (expr.method) {
        "map" -> inferMap(listOf(expr.receiver) + expr.args, env)
        "reduce" -> inferReduce(listOf(expr.receiver) + expr.args, env)
        "zip" -> inferZip(listOf(expr.receiver) + expr.args, env)
        "unzip" -> inferUnzip(listOf(expr.receiver) + expr.args, env)
        else -> throw runtimeError("type error: unknown method '${expr.method}'", token = null)
    }
}

private fun collectionElementType(type: Type, opName: String): Type = when (type) {
    is ListType -> type.elementType
    MatrixType -> NumberType
    VectorType -> NumberType
    else -> throw typeError("List, Vector or Matrix", type, dummyToken(opName))
}

private fun inferMap(args: List<Expr>, env: TypeEnv): Type {
    val target = args.getOrNull(0) ?: throw runtimeError("type error: map expects target")
    val fnExpr = args.getOrNull(1) ?: throw runtimeError("type error: map expects lambda")
    val targetType = infer(target, env, null)

    val elemType = collectionElementType(targetType, "map")
    val fnType = inferFunctionForMap(fnExpr, env, elemType)
    return when (targetType) {
        is ListType -> ListType(fnType.returnType)
        VectorType -> {
            if (fnType.returnType != NumberType) throw typeError(NumberType, fnType.returnType, token = null)
            VectorType
        }
        MatrixType -> {
            if (fnType.returnType != NumberType) throw typeError(NumberType, fnType.returnType, token = null)
            MatrixType
        }
        else -> throw typeError("List, Vector or Matrix", targetType, dummyToken("map"))
    }
}

private fun inferReduce(args: List<Expr>, env: TypeEnv): Type {
    val target = args.getOrNull(0) ?: throw runtimeError("type error: reduce expects target")
    val initExpr = args.getOrNull(1) ?: throw runtimeError("type error: reduce expects initial value")
    val fnExpr = args.getOrNull(2) ?: throw runtimeError("type error: reduce expects lambda")

    val targetType = infer(target, env, null)
    val elementType = collectionElementType(targetType, "reduce")

    val accType = infer(initExpr, env, null)
    return when (targetType) {
        is ListType -> {
            val fnType = inferFunctionForReduce(fnExpr, env, accType, elementType)
            if (fnType.returnType != accType) {
                throw typeError(accType, fnType.returnType, token = null)
            }
            accType
        }
        VectorType, MatrixType -> {
            if (accType != NumberType) throw typeError(NumberType, accType, token = null)
            val fnType = inferFunctionForReduce(fnExpr, env, NumberType, elementType)
            if (fnType.returnType != NumberType) {
                throw typeError(NumberType, fnType.returnType, token = null)
            }
            NumberType
        }
        else -> throw typeError("List, Vector or Matrix", targetType, dummyToken("reduce"))
    }
}

private fun inferZip(args: List<Expr>, env: TypeEnv): Type {
    val leftExpr = args.getOrNull(0) ?: throw runtimeError("type error: zip expects left collection")
    val rightExpr = args.getOrNull(1) ?: throw runtimeError("type error: zip expects right collection")
    val leftType = infer(leftExpr, env, null)
    val rightType = infer(rightExpr, env, null)

    val leftElem = collectionElementType(leftType, "zip")
    val rightElem = collectionElementType(rightType, "zip")

    return ListType(PairType(leftElem, rightElem))
}

private fun inferUnzip(args: List<Expr>, env: TypeEnv): Type {
    val inputExpr = args.getOrNull(0) ?: throw runtimeError("type error: unzip expects input")
    val inputType = infer(inputExpr, env, null)
    if (inputType is ListType && inputType.elementType is PairType) {
        val pairType = inputType.elementType
        return if (pairType.first == NumberType && pairType.second == NumberType) {
            PairType(VectorType, VectorType)
        } else {
            PairType(ListType(pairType.first), ListType(pairType.second))
        }
    }
    throw typeError("List<Pair<..>>", inputType, dummyToken("unzip"))
}

private fun inferFunctionForMap(expr: Expr, env: TypeEnv, paramType: Type): FunctionType {
    return when (expr) {
        is Expr.LambdaExpr -> inferLambda(expr, env, listOf(paramType)) as FunctionType
        else -> {
            val type = infer(expr, env, null)
            val fn = type as? FunctionType ?: throw typeError("Function", type, token = null)
            if (fn.paramTypes.size != 1) {
                throw runtimeError(
                    "type error: expected function with 1 parameter of type ${typeName(paramType)}, got ${typeName(type)}",
                    token = null
                )
            }
            if (fn.paramTypes.first() != paramType) {
                throw typeError(paramType, fn.paramTypes.first(), token = null)
            }
            fn
        }
    }
}

private fun inferFunctionForReduce(expr: Expr, env: TypeEnv, accType: Type, elemType: Type): FunctionType {
    return when (expr) {
        is Expr.LambdaExpr -> inferLambda(expr, env, listOf(accType, elemType)) as FunctionType
        else -> {
            val type = infer(expr, env, null)
            val fn = type as? FunctionType ?: throw typeError("Function", type, token = null)
            if (fn.paramTypes.size != 2) {
                throw runtimeError(
                    "type error: expected function with 2 parameters (${typeName(accType)}, ${typeName(elemType)}), got ${typeName(type)}",
                    token = null
                )
            }
            if (fn.paramTypes[0] != accType || fn.paramTypes[1] != elemType) {
                throw runtimeError(
                    "type error: expected function (${typeName(accType)}, ${typeName(elemType)}), got ${typeName(type)}",
                    token = null
                )
            }
            fn
        }
    }
}

private fun inferProperty(expr: Expr.PropertyAccess, env: TypeEnv): Type {
    val receiverType = infer(expr.receiver, env, null)
    return when (receiverType) {
        MatrixType -> when (expr.name) {
            "rows", "cols" -> NumberType
            "transpose" -> MatrixType
            else -> throw typeError("Matrix property", receiverType, token = null)
        }
        is PairType -> when (expr.name) {
            "first" -> receiverType.first
            "second" -> receiverType.second
            else -> throw typeError("Pair property", receiverType, token = null)
        }
        is ListType -> when (expr.name) {
            "size" -> NumberType
            else -> throw typeError("List property", receiverType, token = null)
        }
        VectorType -> when (expr.name) {
            "size" -> NumberType
            else -> throw typeError("Vector property", receiverType, token = null)
        }
        else -> throw typeError("Property access", receiverType, token = null)
    }
}

private fun typeError(expected: Type, actual: Type, token: Token?): MatrixLangRuntimeException =
    runtimeError("type error: expected ${typeName(expected)}, got ${typeName(actual)}.", token)

private fun typeError(expected: String, actual: Type, token: Token?): MatrixLangRuntimeException =
    runtimeError("type error: expected $expected, got ${typeName(actual)}.", token)

private fun typeName(type: Type): String = when (type) {
    NumberType -> "Number"
    BoolType -> "Bool"
    StringType -> "String"
    MatrixType -> "Matrix"
    VectorType -> "Vector"
    is ListType -> "List<${typeName(type.elementType)}>"
    is PairType -> "Pair<${typeName(type.first)}, ${typeName(type.second)}>"
    is FunctionType -> "(${type.paramTypes.joinToString(", ") { typeName(it) }}) -> ${typeName(type.returnType)}"
    UnitType -> "Unit"
    else -> "Unknown"
}

private fun dummyToken(name: String): Token =
    Token(TokenType.IDENTIFIER, name, null, 0, 0)
