package org.example.utils

import org.example.core.Matrix

object MatrixValidator {
    fun validateDimensionsForAddition(a: Matrix, b: Matrix) {
        require(a.numRows == b.numRows && a.numCols == b.numCols) {
            "Matrices must have the same dimensions for addition. " +
                    "Got: ${a.numRows}x${a.numCols} and ${b.numRows}x${b.numCols}"
        }
    }

    fun validateDimensionsForSubtraction(a: Matrix, b: Matrix) {
        validateDimensionsForAddition(a, b)
    }

    fun validateDimensionsForMultiplication(a: Matrix, b: Matrix) {
        require(a.numCols == b.numRows) {
            "Number of columns in first matrix (${a.numCols}) " +
                    "must equal number of rows in second (${b.numRows})"
        }
    }

    fun validateDimensionsForElementWise(a: Matrix, b: Matrix) {
        validateDimensionsForAddition(a, b)
    }

    fun isSquare(matrix: Matrix): Boolean =
        matrix.numRows == matrix.numCols

    fun canMultiply(a: Matrix, b: Matrix): Boolean =
        a.numCols == b.numRows
}