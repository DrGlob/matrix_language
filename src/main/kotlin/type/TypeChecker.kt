package org.example.type

import org.example.parser.Expr
import org.example.parser.MatrixLangRuntimeException
import org.example.parser.Stmt
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
            val paramTypes = expr.params.map { NumberType }
            var bodyEnv: TypeEnv = TypeEnv(parent = env)
            for ((name, type) in expr.params.zip(paramTypes)) {
                bodyEnv = bodyEnv.extend(name, type)
            }
            val bodyBlock = when (val body = expr.body) {
                is Stmt.Block -> body
                else -> Stmt.Block(listOf(body))
            }
            checkStatements(bodyBlock.statements, bodyEnv)
            val returnType = inferFunctionReturnType(bodyBlock, bodyEnv)
            FunctionType(paramTypes, returnType)
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
            "filter" -> inferFilter(expr.args, env)
            "compose" -> inferCompose(expr.args, env)
            "zeros" -> inferZeros(expr.args, env)
            "ones" -> inferOnes(expr.args, env)
            "identity" -> inferIdentity(expr.args, env)
            "transpose" -> inferTranspose(expr.args, env)
            "rows" -> inferRows(expr.args, env)
            "cols" -> inferCols(expr.args, env)
            "norm" -> inferNorm(expr.args, env)
            "poly" -> inferPoly(expr.args, env)
            "polyWith" -> inferPolyWith(expr.args, env)
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
        "filter" -> inferFilter(listOf(expr.receiver) + expr.args, env)
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

private fun inferFilter(args: List<Expr>, env: TypeEnv): Type {
    val target = args.getOrNull(0) ?: throw runtimeError("type error: filter expects target")
    val fnExpr = args.getOrNull(1) ?: throw runtimeError("type error: filter expects predicate")
    val targetType = infer(target, env, null)

    val elemType = collectionElementType(targetType, "filter")
    val fnType = inferFunctionForMap(fnExpr, env, elemType)
    if (fnType.returnType != BoolType) {
        throw typeError(BoolType, fnType.returnType, token = null)
    }

    return when (targetType) {
        is ListType -> targetType
        VectorType -> VectorType
        MatrixType -> MatrixType
        else -> throw typeError("List, Vector or Matrix", targetType, dummyToken("filter"))
    }
}

private fun inferCompose(args: List<Expr>, env: TypeEnv): Type {
    val fExpr = args.getOrNull(0) ?: throw runtimeError("type error: compose expects f")
    val gExpr = args.getOrNull(1) ?: throw runtimeError("type error: compose expects g")

    val fType = infer(fExpr, env, null) as? FunctionType
        ?: throw typeError("Function", infer(fExpr, env, null), dummyToken("compose"))
    val gType = infer(gExpr, env, null) as? FunctionType
        ?: throw typeError("Function", infer(gExpr, env, null), dummyToken("compose"))

    if (fType.paramTypes.size != 1 || gType.paramTypes.size != 1) {
        throw runtimeError("type error: compose expects unary functions")
    }
    if (fType.paramTypes.first() != gType.returnType) {
        throw runtimeError(
            "type error: compose expects f(${typeName(gType.returnType)}) but got f(${typeName(fType.paramTypes.first())})"
        )
    }
    return FunctionType(listOf(gType.paramTypes.first()), fType.returnType)
}

private fun inferZeros(args: List<Expr>, env: TypeEnv): Type =
    inferMatrixFactory("zeros", args, env)

private fun inferOnes(args: List<Expr>, env: TypeEnv): Type =
    inferMatrixFactory("ones", args, env)

private fun inferMatrixFactory(name: String, args: List<Expr>, env: TypeEnv): Type {
    if (args.size != 2) {
        throw runtimeError("type error: $name expects 2 number arguments", dummyToken(name))
    }
    val rowsType = infer(args[0], env, null)
    val colsType = infer(args[1], env, null)
    if (rowsType != NumberType || colsType != NumberType) {
        throw runtimeError("type error: $name expects (Number, Number)", dummyToken(name))
    }
    return MatrixType
}

private fun inferIdentity(args: List<Expr>, env: TypeEnv): Type {
    if (args.size != 1) {
        throw runtimeError("type error: identity expects 1 number argument", dummyToken("identity"))
    }
    val sizeType = infer(args[0], env, null)
    if (sizeType != NumberType) {
        throw typeError(NumberType, sizeType, dummyToken("identity"))
    }
    return MatrixType
}

private fun inferTranspose(args: List<Expr>, env: TypeEnv): Type {
    if (args.size != 1) {
        throw runtimeError("type error: transpose expects 1 matrix argument", dummyToken("transpose"))
    }
    val argType = infer(args[0], env, null)
    if (argType != MatrixType) {
        throw typeError(MatrixType, argType, dummyToken("transpose"))
    }
    return MatrixType
}

private fun inferRows(args: List<Expr>, env: TypeEnv): Type =
    inferMatrixPropertyCall("rows", args, env)

private fun inferCols(args: List<Expr>, env: TypeEnv): Type =
    inferMatrixPropertyCall("cols", args, env)

private fun inferMatrixPropertyCall(name: String, args: List<Expr>, env: TypeEnv): Type {
    if (args.size != 1) {
        throw runtimeError("type error: $name expects 1 matrix argument", dummyToken(name))
    }
    val argType = infer(args[0], env, null)
    if (argType != MatrixType) {
        throw typeError(MatrixType, argType, dummyToken(name))
    }
    return NumberType
}

private fun inferNorm(args: List<Expr>, env: TypeEnv): Type {
    if (args.size != 1) {
        throw runtimeError("type error: norm expects 1 matrix argument", dummyToken("norm"))
    }
    val argType = infer(args[0], env, null)
    if (argType != MatrixType) {
        throw typeError(MatrixType, argType, dummyToken("norm"))
    }
    return NumberType
}

private fun inferPoly(args: List<Expr>, env: TypeEnv): Type {
    if (args.size != 2) {
        throw runtimeError("type error: poly expects 2 arguments", dummyToken("poly"))
    }
    val matrixType = infer(args[0], env, null)
    if (matrixType != MatrixType) {
        throw typeError(MatrixType, matrixType, dummyToken("poly"))
    }
    val coeffType = infer(args[1], env, null)
    val ok = when (coeffType) {
        is ListType -> coeffType.elementType == NumberType
        MatrixType, VectorType -> true
        else -> false
    }
    if (!ok) {
        throw runtimeError("type error: poly expects coefficients as List<Number> or Matrix", dummyToken("poly"))
    }
    return MatrixType
}

private fun inferPolyWith(args: List<Expr>, env: TypeEnv): Type {
    if (args.size != 3) {
        throw runtimeError("type error: polyWith expects 3 arguments", dummyToken("polyWith"))
    }
    val matrixType = infer(args[0], env, null)
    if (matrixType != MatrixType) {
        throw typeError(MatrixType, matrixType, dummyToken("polyWith"))
    }
    val coeffType = infer(args[1], env, null)
    val ok = when (coeffType) {
        is ListType -> coeffType.elementType == NumberType
        MatrixType, VectorType -> true
        else -> false
    }
    if (!ok) {
        throw runtimeError("type error: polyWith expects coefficients as List<Number> or Matrix", dummyToken("polyWith"))
    }
    val algoType = infer(args[2], env, null)
    if (algoType != StringType) {
        throw typeError(StringType, algoType, dummyToken("polyWith"))
    }
    return MatrixType
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

private fun typeName(type: Type): String = formatType(type)

private fun dummyToken(name: String): Token =
    Token(TokenType.IDENTIFIER, name, null, 0, 0)

/**
 * Проверка списка statement-ов с накоплением окружения типов.
 */
fun checkStatements(statements: List<Stmt>, env: TypeEnv): TypeEnv {
    var current = env
    for (stmt in statements) {
        current = checkStatement(stmt, current)
    }
    return current
}

private fun checkStatement(stmt: Stmt, env: TypeEnv): TypeEnv {
    return when (stmt) {
        is Stmt.Expression -> {
            inferType(stmt.expression, env)
            env
        }
        is Stmt.Print -> {
            inferType(stmt.expression, env)
            env
        }
        is Stmt.Var -> {
            val initializerType = stmt.initializer?.let { inferType(it, env) } ?: UnitType
            env.extend(stmt.name, initializerType)
        }
        is Stmt.Block -> {
            checkStatements(stmt.statements, TypeEnv(parent = env))
            env
        }
        is Stmt.If -> {
            val condType = inferType(stmt.condition, env)
            if (condType != BoolType) {
                throw typeError(BoolType, condType, token = null)
            }
            checkStatement(stmt.thenBranch, TypeEnv(parent = env))
            stmt.elseBranch?.let { checkStatement(it, TypeEnv(parent = env)) }
            env
        }
        is Stmt.Function -> {
            val paramTypes = stmt.params.map { NumberType }
            val placeholder = FunctionType(paramTypes, UnitType)
            val withFn = env.extend(stmt.name, placeholder)
            var bodyEnv: TypeEnv = TypeEnv(parent = withFn)
            for ((name, type) in stmt.params.zip(paramTypes)) {
                bodyEnv = bodyEnv.extend(name, type)
            }
            checkStatements(stmt.body.statements, bodyEnv)
            val returnType = inferFunctionReturnType(stmt.body, bodyEnv)
            env.extend(stmt.name, FunctionType(paramTypes, returnType))
        }
        is Stmt.Return -> {
            stmt.value?.let { inferType(it, env) }
            env
        }
    }
}

private fun inferFunctionReturnType(body: Stmt.Block, env: TypeEnv): Type {
    val returns = mutableListOf<Type>()
    var currentEnv = env
    for (statement in body.statements) {
        currentEnv = collectReturnTypes(statement, currentEnv, returns)
    }
    if (returns.isEmpty()) return UnitType
    val first = returns.first()
    for (type in returns) {
        if (type != first) {
            throw runtimeError("type error: inconsistent return types in function body")
        }
    }
    return first
}

private fun collectReturnTypes(stmt: Stmt, env: TypeEnv, out: MutableList<Type>): TypeEnv {
    return when (stmt) {
        is Stmt.Expression -> {
            inferType(stmt.expression, env)
            env
        }
        is Stmt.Print -> {
            inferType(stmt.expression, env)
            env
        }
        is Stmt.Var -> {
            val initializerType = stmt.initializer?.let { inferType(it, env) } ?: UnitType
            env.extend(stmt.name, initializerType)
        }
        is Stmt.Block -> {
            var scoped = TypeEnv(parent = env)
            for (statement in stmt.statements) {
                scoped = collectReturnTypes(statement, scoped, out)
            }
            env
        }
        is Stmt.If -> {
            collectReturnTypes(stmt.thenBranch, TypeEnv(parent = env), out)
            stmt.elseBranch?.let { collectReturnTypes(it, TypeEnv(parent = env), out) }
            env
        }
        is Stmt.Function -> env
        is Stmt.Return -> {
            val type = stmt.value?.let { inferType(it, env) } ?: UnitType
            out.add(type)
            env
        }
    }
}
