//package org.example
//
//import org.example.core.matrixOf
//import kotlin.math.max
//import kotlin.math.min
//
//// Matrix class и остальной код остается таким же как в предыдущем исправлении...
//
//// Тестирующие функции
//fun runBasicTests() {
//    println("=== Базовые тесты матричных операций ===")
//
//    // Тест 1: Создание матриц
//    println("\n1. Тест создания матриц:")
//    val A = matrixOf(listOf(1.0, 2.0), listOf(3.0, 4.0))
//    val B = matrixOf(listOf(5.0, 6.0), listOf(7.0, 8.0))
//    println("A = \n$A")
//    println("B = \n$B")
//
//    // Тест 2: Сложение
//    println("\n2. Тест сложения:")
//    val C = A + B
//    println("A + B = \n$C")
//    assert(C.rows[0][0] == 6.0 && C.rows[0][1] == 8.0)
//    assert(C.rows[1][0] == 10.0 && C.rows[1][1] == 12.0)
//
//    // Тест 3: Вычитание
//    println("\n3. Тест вычитания:")
//    val D = A - B
//    println("A - B = \n$D")
//    assert(D.rows[0][0] == -4.0 && D.rows[0][1] == -4.0)
//    assert(D.rows[1][0] == -4.0 && D.rows[1][1] == -4.0)
//
//    // Тест 4: Умножение на скаляр
//    println("\n4. Тест умножения на скаляр:")
//    val E = A * 2.0
//    println("A * 2 = \n$E")
//    assert(E.rows[0][0] == 2.0 && E.rows[0][1] == 4.0)
//    assert(E.rows[1][0] == 6.0 && E.rows[1][1] == 8.0)
//
//    // Тест 5: Умножение матриц
//    println("\n5. Тест умножения матриц:")
//    val F = A * B
//    println("A * B = \n$F")
//    // Проверка: [1*5+2*7, 1*6+2*8] = [19, 22]
//    //          [3*5+4*7, 3*6+4*8] = [43, 50]
//    assert(F.rows[0][0] == 19.0 && F.rows[0][1] == 22.0)
//    assert(F.rows[1][0] == 43.0 && F.rows[1][1] == 50.0)
//
//    // Тест 6: Транспонирование
//    println("\n6. Тест транспонирования:")
//    val G = A.transpose()
//    println("A.transpose() = \n$G")
//    assert(G.numRows == 2 && G.numCols == 2)
//    assert(G.rows[0][0] == 1.0 && G.rows[0][1] == 3.0)
//    assert(G.rows[1][0] == 2.0 && G.rows[1][1] == 4.0)
//
//    // Тест 7: Единичная матрица
//    println("\n7. Тест единичной матрицы:")
//    val I = identity(3)
//    println("identity(3) = \n$I")
//    assert(I.rows[0][0] == 1.0 && I.rows[0][1] == 0.0 && I.rows[0][2] == 0.0)
//    assert(I.rows[1][0] == 0.0 && I.rows[1][1] == 1.0 && I.rows[1][2] == 0.0)
//    assert(I.rows[2][0] == 0.0 && I.rows[2][1] == 0.0 && I.rows[2][2] == 1.0)
//
//    // Тест 8: Умножение на единичную матрицу
//    println("\n8. Тест умножения на единичную матрицу:")
//    val I2 = identity(2)
//    val H = A * I2
//    println("A * I = \n$H")
//    assert(H.rows[0][0] == A.rows[0][0] && H.rows[0][1] == A.rows[0][1])
//    assert(H.rows[1][0] == A.rows[1][0] && H.rows[1][1] == A.rows[1][1])
//
//    // Тест 9: Неквадратные матрицы
//    println("\n9. Тест с неквадратными матрицами:")
//    val M = matrixOf(listOf(1.0, 2.0, 3.0), listOf(4.0, 5.0, 6.0))
//    val N = matrixOf(listOf(7.0, 8.0), listOf(9.0, 10.0), listOf(11.0, 12.0))
//    println("M (2x3) = \n$M")
//    println("N (3x2) = \n$N")
//    val O = M * N
//    println("M * N = \n$O")
//    assert(O.numRows == 2 && O.numCols == 2)
//    // Проверка: [1*7+2*9+3*11, 1*8+2*10+3*12] = [58, 64]
//    //          [4*7+5*9+6*11, 4*8+5*10+6*12] = [139, 154]
//    assert(O.rows[0][0] == 58.0 && O.rows[0][1] == 64.0)
//    assert(O.rows[1][0] == 139.0 && O.rows[1][1] == 154.0)
//
//    // Тест 10: Вектор-столбец и вектор-строка
//    println("\n10. Тест векторов:")
//    val rowVector = matrixOf(listOf(1.0, 2.0, 3.0))
//    val colVector = matrixOf(listOf(4.0), listOf(5.0), listOf(6.0))
//    println("rowVector (1x3) = $rowVector")
//    println("colVector (3x1) = \n$colVector")
//    val dotProduct = rowVector * colVector
//    println("rowVector * colVector = \n$dotProduct")
//    assert(dotProduct.numRows == 1 && dotProduct.numCols == 1)
//    assert(dotProduct.rows[0][0] == 32.0) // 1*4 + 2*5 + 3*6 = 32
//
//    // Тест 11: Проверка ошибок
//    println("\n11. Тест обработки ошибок:")
//    try {
//        val badAdd = A + matrixOf(listOf(1.0, 2.0, 3.0))
//        println("ОШИБКА: Сложение должно было вызвать исключение!")
//    } catch (e: IllegalArgumentException) {
//        println("✓ Правильно выброшено исключение при сложении разных размеров: ${e.message}")
//    }
//
//    try {
//        val badMult = A * matrixOf(listOf(1.0, 2.0, 3.0), listOf(4.0, 5.0, 6.0))
//        println("ОШИБКА: Умножение должно было вызвать исключение!")
//    } catch (e: IllegalArgumentException) {
//        println("✓ Правильно выброшено исключение при несовместимом умножении: ${e.message}")
//    }
//
//    println("\n=== Все базовые тесты пройдены! ===")
//}
//
//fun runDSLTests() {
//    println("\n\n=== Тесты DSL контекста ===")
//
//    // Тест 1: Простой DSL
//    println("\n1. Простой DSL:")
//    val result1 = matrixComputation {
//        declare("X", matrixOf(listOf(1.0, 0.0), listOf(0.0, 1.0)))
//        declare("Y", matrixOf(listOf(2.0, 3.0), listOf(4.0, 5.0)))
//        this["X"] * this["Y"]
//    }
//    println("Результат: \n$result1")
//
//    // Тест 2: DSL с условием
//    println("\n2. DSL с условием:")
//    val result2 = matrixComputation {
//        declare("A", matrixOf(listOf(1.0, 2.0), listOf(3.0, 4.0)))
//        declare("B", matrixOf(listOf(5.0, 6.0), listOf(7.0, 8.0)))
//
//        val res = ifThen(
//            { this["A"].rows[0][0] == 1.0 },
//            { this["A"] + this["B"] },
//            { this["A"] * this["B"] }
//        )
//        res
//    }
//    println("Результат: \n$result2")
//
//    // Тест 3: DSL с fold
//    println("\n3. DSL с fold:")
//    val result3 = matrixComputation {
//        val vectorData = foldRange(0, 4, emptyList<Double>()) { acc, i ->
//            acc + listOf(i * 2.0 + 1.0)
//        }
//        Matrix(listOf(vectorData))
//    }
//    println("Вектор: $result3")
//
//    // Тест 4: DSL с mapRange
//    println("\n4. DSL с mapRange:")
//    val result4 = matrixComputation {
//        val data = mapRange(0, 3) { it * 3.0 }
//        Matrix(listOf(data))
//    }
//    println("Вектор: $result4")
//
//    println("\n=== Все DSL тесты пройдены! ===")
//}
//
//fun runAdvancedTests() {
//    println("\n\n=== Продвинутые тесты ===")
//
//    // Тест: Свойство транспонирования
//    println("\n1. Свойство (Aᵀ)ᵀ = A:")
//    val A = matrixOf(listOf(1.0, 2.0, 3.0), listOf(4.0, 5.0, 6.0))
//    val AT = A.transpose()
//    val ATransposeTranspose = AT.transpose()
//    println("A = \n$A")
//    println("Aᵀ = \n$AT")
//    println("(Aᵀ)ᵀ = \n$ATransposeTranspose")
//    assert(ATransposeTranspose.rows == A.rows)
//
//    // Тест: Свойство (A + B)ᵀ = Aᵀ + Bᵀ
//    println("\n2. Свойство (A + B)ᵀ = Aᵀ + Bᵀ:")
//    val B = matrixOf(listOf(7.0, 8.0, 9.0), listOf(10.0, 11.0, 12.0))
//    val sumTranspose = (A + B).transpose()
//    val sumOfTransposes = A.transpose() + B.transpose()
//    println("(A + B)ᵀ = \n$sumTranspose")
//    println("Aᵀ + Bᵀ = \n$sumOfTransposes")
//    assert(sumTranspose.rows == sumOfTransposes.rows)
//
//    // Тест: Ассоциативность умножения (для квадратных матриц)
//    println("\n3. Ассоциативность умножения (A*B)*C = A*(B*C):")
//    val C1 = matrixOf(listOf(1.0, 2.0), listOf(3.0, 4.0))
//    val C2 = matrixOf(listOf(5.0, 6.0), listOf(7.0, 8.0))
//    val C3 = matrixOf(listOf(9.0, 10.0), listOf(11.0, 12.0))
//
//    val left = (C1 * C2) * C3
//    val right = C1 * (C2 * C3)
//    println("(C1*C2)*C3 = \n$left")
//    println("C1*(C2*C3) = \n$right")
//    assert(left.rows == right.rows)
//
//    println("\n=== Все продвинутые тесты пройдены! ===")
//}
//
//// Обновленная main функция
//fun main() {
//    println("Запуск тестов матричной библиотеки")
//    println("=" * 50)
//
//    try {
//        runBasicTests()
//        runDSLTests()
//        runAdvancedTests()
//
//        println("\n" + "=" * 50)
//        println("Все тесты успешно пройдены! ✓")
//
//        // Демонстрация исправленного примера из вопроса
//        println("\n\n=== Демонстрация исправленного примера ===")
//        val result = matrixComputation {
//            declare("A", matrixOf(listOf(1.0, 2.0), listOf(3.0, 4.0)))
//            declare("B", matrixOf(listOf(5.0, 6.0), listOf(7.0, 8.0)))
//
//            // Conditional: if numRows > 1, add A + B, else A * B
//            val conditionalResult = ifThen(
//                { this["A"].numRows > 1 },
//                { this["A"] + this["B"] },
//                { this["A"] * this["B"] }
//            )
//
//            // Create compatible vector for multiplication
//            val vectorList = foldRange(0, 2, emptyList<Double>()) { acc, i ->
//                acc + listOf(i * 1.0)
//            }
//            val vector = Matrix(listOf(vectorList))
//
//            // This will work: (2x2) * (2x1) = (2x1)
//            conditionalResult * vector.transpose()
//        }
//
//        println("Результат примера:\n$result")
//
//    } catch (e: Exception) {
//        println("Тест провален с ошибкой: ${e.message}")
//        e.printStackTrace()
//    }
//}
//
//// Вспомогательная функция для повторения строки
//operator fun String.times(n: Int): String {
//    return repeat(n)
//}