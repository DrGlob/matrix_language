package syntax

/**
 * Синтаксис языка матричных вычислений
 *
 * Примеры:
 *
 * 1. Объявление переменных:
 *    let A = [[1, 2], [3, 4]]
 *    let B = zeros(2, 2)
 *
 * 2. Операции:
 *    let C = A + B
 *    let D = A * B
 *    let E = transpose(A)
 *
 * 3. Условия:
 *    if rows(A) > 1 {
 *        let result = A + B
 *    } else {
 *        let result = A * B
 *    }
 *
 * 4. Циклы:
 *    for i in 0..5 {
 *        let mat = identity(i)
 *        print(mat)
 *    }
 *
 * 5. Функции:
 *    function addMatrices(a, b) {
 *        return a + b
 *    }
 */
object Syntax {
    // Ключевые слова
    val KEYWORDS = setOf(
        "let", "if", "else", "for", "in", "function",
        "return", "print", "zeros", "ones", "identity",
        "transpose", "rows", "cols"
    )

    // Операторы
    val OPERATORS = setOf("+", "-", "*", "/", "=", "==", "!=", "<", ">", "<=", ">=")

    // Разделители
    val DELIMITERS = setOf(",", ";", "(", ")", "{", "}", "[", "]")
}