package org.example.core

/**
 * Вычисление матричного полинома P(A) по схеме Хорнера.
 *
 * mult — стратегия умножения (например, { a, b -> multiply(a, b, MatMulDefaults.default()) }).
 */
fun evaluatePolynomial(
    coeffs: List<Double>,
    A: Matrix,
    mult: (Matrix, Matrix) -> Matrix
): Matrix {
    require(coeffs.isNotEmpty()) { "Coefficients must not be empty" }
    require(A.numRows == A.numCols) { "Polynomial is defined for square matrices" }

    // Начинаем с последнего коэффициента * I
    var result = Matrix.identity(A.numRows).scale(coeffs.last())

    for (i in coeffs.size - 2 downTo 0) {
        // Horner: result = result * A + coeff_i * I
        result = mult(result, A).plus(Matrix.identity(A.numRows).scale(coeffs[i]))
    }

    return result
}

/**
 * Линейная форма суммы coeff_i * matrices_i.
 */
fun linearForm(coeffs: List<Double>, matrices: List<Matrix>): Matrix {
    require(coeffs.size == matrices.size) { "coeffs and matrices must have the same length" }
    require(matrices.isNotEmpty()) { "matrices must not be empty" }

    val rows = matrices.first().numRows
    val cols = matrices.first().numCols
    require(matrices.all { it.numRows == rows && it.numCols == cols }) {
        "All matrices must have the same dimensions"
    }

    var acc = Matrix.zeros(rows, cols)
    for ((coef, mat) in coeffs.zip(matrices)) {
        acc = acc + mat.scale(coef)
    }
    return acc
}
