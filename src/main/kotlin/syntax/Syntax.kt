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
 * 4. Выражения:
 *    let x = if rows(A) > 1 then A + B else A * B
 *    let y = let t = 10 in t * 2
 */
object Syntax {
    // Ключевые слова
    val KEYWORDS = setOf(
        "let", "if", "then", "else", "in", "function",
        "return", "print", "zeros", "ones", "identity",
        "transpose", "rows", "cols"
    )

    // Операторы
    val OPERATORS = setOf("+", "-", "*", "/", "=", "==", "!=", "<", ">", "<=", ">=")

    // Разделители
    val DELIMITERS = setOf(",", ";", "(", ")", "{", "}", "[", "]")
}
