package org.example.core

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlin.system.measureTimeMillis
import org.example.utils.MatrixValidator

/**
 * Алгоритмы умножения матриц и их свойства.
 *
 * SEQUENTIAL — блочное умножение O(n^3) с хорошей локальностью кэша.
 * PARALLEL — то же блочное умножение, но с распараллеливанием по блокам;
 *            асимптотика такая же, эффективность зависит от размеров и parallelism.
 * STRASSEN — алгоритм Штрассена с асимптотикой ~O(n^log2(7));
 *            работает только для квадратных матриц одинакового размера,
 *            внутри использует порог strassenThreshold для перехода на SEQUENTIAL.
 */
enum class MultiplicationAlgorithm {
    /** Блочное последовательное умножение (классическая O(n^3)). */
    SEQUENTIAL,
    /** Параллельное блочное умножение (те же операции, меньше wall-time при больших матрицах). */
    PARALLEL,
    /** Strassen для квадратных матриц; паддинг до степени 2 и пороговый переход на SEQUENTIAL. */
    STRASSEN
}

interface MatrixOperations {
    operator fun plus(other: Matrix): Matrix
    operator fun minus(other: Matrix): Matrix
    operator fun times(scalar: Double): Matrix

    /**
     * Умножение матриц.
     *
     * Иммутабельность: входные матрицы не изменяются.
     *
     * Исключения:
     * - [IllegalArgumentException] при несовместимых размерах.
     * - [IllegalArgumentException] для алгоритмов с доп. ограничениями (например, STRASSEN).
     */
    operator fun times(other: Matrix): Matrix
    fun transpose(): Matrix
}

// Иммутабельная матрица с плоским хранением (row-major)
class Matrix private constructor(
    internal val data: DoubleArray,
    val rows: Int,
    val cols: Int
) : MatrixOperations {

    val numRows: Int get() = rows
    val numCols: Int get() = cols

    companion object {
        const val STRASSEN_THRESHOLD = MatMulDefaults.DEFAULT_STRASSEN_THRESHOLD

        operator fun invoke(rows: List<List<Double>>): Matrix =
            fromFlat(rows.size, if (rows.isNotEmpty()) rows[0].size else 0, flatten(rows), copy = false)

        fun fromInitializer(rows: Int, cols: Int, init: (Int, Int) -> Double): Matrix {
            val data = DoubleArray(rows * cols)
            var idx = 0
            for (r in 0 until rows) {
                for (c in 0 until cols) {
                    data[idx++] = init(r, c)
                }
            }
            return Matrix(data, rows, cols)
        }

        fun zeros(rows: Int, cols: Int): Matrix =
            Matrix(DoubleArray(rows * cols), rows, cols)

        fun identity(size: Int): Matrix =
            fromInitializer(size, size) { r, c -> if (r == c) 1.0 else 0.0 }

        fun fromFlat(rows: Int, cols: Int, data: DoubleArray, copy: Boolean = true): Matrix {
            require(rows >= 0 && cols >= 0) { "Matrix dimensions must be non-negative" }
            require(data.size == rows * cols) {
                "Data size (${data.size}) does not match dimensions ${rows}x$cols"
            }
            return Matrix(if (copy) data.copyOf() else data, rows, cols)
        }

        private fun flatten(rows: List<List<Double>>): DoubleArray {
            val rowCount = rows.size
            val colCount = if (rowCount > 0) rows[0].size else 0
            require(rows.all { it.size == colCount }) { "All rows must have the same number of columns" }

            val flat = DoubleArray(rowCount * colCount)
            var idx = 0
            for (r in 0 until rowCount) {
                val row = rows[r]
                for (c in 0 until colCount) {
                    flat[idx++] = row[c]
                }
            }
            return flat
        }
    }

    private fun index(row: Int, col: Int): Int {
        require(row in 0 until rows && col in 0 until cols) {
            "Index out of bounds: ($row, $col) for matrix ${rows}x$cols"
        }
        return row * cols + col
    }

    operator fun get(row: Int, col: Int): Double = data[index(row, col)]

    operator fun set(row: Int, col: Int, value: Double): Matrix {
        val copy = data.copyOf()
        copy[index(row, col)] = value
        return Matrix(copy, rows, cols)
    }

    fun toList(): List<List<Double>> =
        (0 until rows).map { r ->
            (0 until cols).map { c -> data[r * cols + c] }
        }

    // Реализация операций
    override operator fun plus(other: Matrix): Matrix {
        MatrixValidator.validateDimensionsForAddition(this, other)
        val result = DoubleArray(rows * cols)
        for (i in result.indices) {
            result[i] = data[i] + other.data[i]
        }
        return Matrix(result, rows, cols)
    }

    override operator fun minus(other: Matrix): Matrix {
        MatrixValidator.validateDimensionsForSubtraction(this, other)
        val result = DoubleArray(rows * cols)
        for (i in result.indices) {
            result[i] = data[i] - other.data[i]
        }
        return Matrix(result, rows, cols)
    }

    override operator fun times(scalar: Double): Matrix {
        val result = DoubleArray(rows * cols)
        for (i in result.indices) {
            result[i] = data[i] * scalar
        }
        return Matrix(result, rows, cols)
    }

    /**
     * Умножение матриц через единую точку входа [multiply] и [MatMulDefaults.default].
     *
     * Иммутабельность: входные матрицы не изменяются.
     */
    override operator fun times(other: Matrix): Matrix = multiply(this, other)

    override fun transpose(): Matrix {
        val result = DoubleArray(cols * rows)
        for (r in 0 until rows) {
            for (c in 0 until cols) {
                result[c * rows + r] = data[r * cols + c]
            }
        }
        return Matrix(result, cols, rows)
    }


    /**
     * Масштабирование матрицы скаляром (alias для операторной версии).
     */
    fun scale(alpha: Double): Matrix = this * alpha

    /**
     * Вычисление матричного полинома P(A) по схеме Горнера.
     *
     * Коэффициенты задаются в порядке убывания степени: [a_n, a_{n-1}, ..., a_0],
     * полином считается как (((a_n I) * A + a_{n-1} I) * A + ... + a_0 I).
     *
     * По умолчанию умножения выполняются параллельным блочным алгоритмом.
     */
    fun polyEval(
        coefficients: List<Double>,
        config: MatMulConfig = MatMulDefaults.default().copy(algorithm = MultiplicationAlgorithm.PARALLEL),
        logMetrics: Boolean = false
    ): Matrix {
        require(coefficients.isNotEmpty()) { "Polynomial coefficients must not be empty" }
        require(rows == cols) { "Matrix polynomials require square matrices: got ${rows}x$cols" }

        val id = identity(rows)
        var acc = id * coefficients[0]
        var idx = 1
        while (idx < coefficients.size) {
            acc = multiplyWithMetrics(acc, this, config, logMetrics)
            val coeff = coefficients[idx]
            if (coeff != 0.0) {
                acc = acc + id * coeff
            }
            idx++
        }
        return acc
    }

    /**
     * Блочное умножение без параллелизма.
     */
    fun multiplySequential(other: Matrix, blockSize: Int = BLOCK_SIZE): Matrix {
        MatrixValidator.validateDimensionsForMultiplication(this, other)
        val aBlocks = this.toBlocks(blockSize)
        val bBlocks = other.toBlocks(blockSize)
        val resultData = DoubleArray(rows * other.cols)

        for (bi in 0 until aBlocks.blockRows) {
            val rowStart = bi * blockSize
            val blockHeight = aBlocks.blocks[bi][0].numRows
            for (bj in 0 until bBlocks.blockCols) {
                val colStart = bj * blockSize
                val blockWidth = bBlocks.blocks[0][bj].numCols

                var kIndex = 0
                while (kIndex < aBlocks.blockCols) {
                    val aBlock = aBlocks.blocks[bi][kIndex]
                    val bBlock = bBlocks.blocks[kIndex][bj]
                    require(aBlock.numCols == bBlock.numRows) { "Block dimensions mismatch" }

                    for (r in 0 until blockHeight) {
                        val destBase = (rowStart + r) * other.cols + colStart
                        for (c in 0 until blockWidth) {
                            var acc = 0.0
                            for (k in 0 until aBlock.numCols) {
                                acc += aBlock[r, k] * bBlock[k, c]
                            }
                            resultData[destBase + c] += acc
                        }
                    }
                    kIndex++
                }
            }
        }

        return Matrix.fromFlat(rows, other.cols, resultData, copy = false)
    }

    /**
     * Параллельное блочное умножение (async-задачи на блок C_ij).
     */
    suspend fun multiplyParallel(
        other: Matrix,
        blockSize: Int = BLOCK_SIZE,
        parallelism: Int = MatMulDefaults.default().parallelism
    ): Matrix = coroutineScope {
        MatrixValidator.validateDimensionsForMultiplication(this@Matrix, other)
        val aBlocks = this@Matrix.toBlocks(blockSize)
        val bBlocks = other.toBlocks(blockSize)
        val resultData = DoubleArray(rows * other.cols)
        val semaphore = Semaphore(parallelism)

        val jobs = mutableListOf<kotlinx.coroutines.Deferred<Unit>>()

        for (bi in 0 until aBlocks.blockRows) {
            val rowStart = bi * blockSize
            val blockHeight = aBlocks.blocks[bi][0].numRows
            for (bj in 0 until bBlocks.blockCols) {
                val colStart = bj * blockSize
                val blockWidth = bBlocks.blocks[0][bj].numCols

                val job = async(Dispatchers.Default) {
                    semaphore.withPermit {
                        val local = DoubleArray(blockHeight * blockWidth)
                        var kIndex = 0
                        while (kIndex < aBlocks.blockCols) {
                            val aBlock = aBlocks.blocks[bi][kIndex]
                            val bBlock = bBlocks.blocks[kIndex][bj]
                            require(aBlock.numCols == bBlock.numRows) { "Block dimensions mismatch" }

                            for (r in 0 until blockHeight) {
                                val localBase = r * blockWidth
                                for (c in 0 until blockWidth) {
                                    var acc = 0.0
                                    for (k in 0 until aBlock.numCols) {
                                        acc += aBlock[r, k] * bBlock[k, c]
                                    }
                                    local[localBase + c] += acc
                                }
                            }
                            kIndex++
                        }

                        for (r in 0 until blockHeight) {
                            val destBase = (rowStart + r) * other.cols + colStart
                            val localBase = r * blockWidth
                            for (c in 0 until blockWidth) {
                                resultData[destBase + c] = local[localBase + c]
                            }
                        }
                    }
                }
                jobs.add(job)
            }
        }

        jobs.forEach { it.await() }
        Matrix.fromFlat(rows, other.cols, resultData, copy = false)
    }

    /**
     * Реализация Штрассена для квадратных матриц.
     * Для некратных размеров выполняется паддинг нулями до ближайшей степени 2,
     * затем результат усекается обратно до исходных размеров.
     */
    fun multiplyStrassen(other: Matrix, threshold: Int = STRASSEN_THRESHOLD): Matrix {
        MatrixValidator.validateDimensionsForMultiplication(this, other)
        require(numRows == numCols && other.numRows == other.numCols && numRows == other.numRows) {
            "Strassen implementation currently supports only square matrices of equal size"
        }

        val originalSize = numRows
        val targetSize = nextPowerOfTwo(originalSize)

        val aPadded = if (targetSize == numRows) this else pad(targetSize, targetSize)
        val bPadded = if (targetSize == other.numRows) other else other.pad(targetSize, targetSize)

        val resultPadded = strassenRecursive(aPadded, bPadded, targetSize, threshold)

        return if (targetSize == originalSize) {
            resultPadded
        } else {
            resultPadded.trim(originalSize, other.numCols)
        }
    }

    private fun pad(targetRows: Int, targetCols: Int): Matrix =
        fromInitializer(targetRows, targetCols) { r, c ->
            if (r < rows && c < cols) this[r, c] else 0.0
        }

    private fun trim(targetRows: Int, targetCols: Int): Matrix =
        fromInitializer(targetRows, targetCols) { r, c -> this[r, c] }

    private fun strassenRecursive(a: Matrix, b: Matrix, size: Int, threshold: Int): Matrix {
        if (size <= threshold) return a.multiplySequential(b)

        val mid = size / 2

        val a11 = a.subMatrix(0, 0, mid, mid)
        val a12 = a.subMatrix(0, mid, mid, size - mid)
        val a21 = a.subMatrix(mid, 0, size - mid, mid)
        val a22 = a.subMatrix(mid, mid, size - mid, size - mid)

        val b11 = b.subMatrix(0, 0, mid, mid)
        val b12 = b.subMatrix(0, mid, mid, size - mid)
        val b21 = b.subMatrix(mid, 0, size - mid, mid)
        val b22 = b.subMatrix(mid, mid, size - mid, size - mid)

        val p1 = strassenRecursive(a11 + a22, b11 + b22, mid, threshold)
        val p2 = strassenRecursive(a21 + a22, b11, mid, threshold)
        val p3 = strassenRecursive(a11, b12 - b22, mid, threshold)
        val p4 = strassenRecursive(a22, b21 - b11, mid, threshold)
        val p5 = strassenRecursive(a11 + a12, b22, mid, threshold)
        val p6 = strassenRecursive(a21 - a11, b11 + b12, mid, threshold)
        val p7 = strassenRecursive(a12 - a22, b21 + b22, mid, threshold)

        val c11 = p1 + p4 - p5 + p7
        val c12 = p3 + p5
        val c21 = p2 + p4
        val c22 = p1 - p2 + p3 + p6

        return combineQuadrants(c11, c12, c21, c22, size, size)
    }

    private fun subMatrix(row: Int, col: Int, subRows: Int, subCols: Int): Matrix =
        fromInitializer(subRows, subCols) { r, c -> this[row + r, col + c] }

    private fun combineQuadrants(
        c11: Matrix,
        c12: Matrix,
        c21: Matrix,
        c22: Matrix,
        totalRows: Int,
        totalCols: Int
    ): Matrix {
        val data = DoubleArray(totalRows * totalCols)
        val midRow = c11.numRows
        val midCol = c11.numCols

        for (r in 0 until midRow) {
            val base = r * totalCols
            for (c in 0 until midCol) {
                data[base + c] = c11[r, c]
            }
            for (c in 0 until c12.numCols) {
                data[base + midCol + c] = c12[r, c]
            }
        }
        for (r in 0 until c21.numRows) {
            val base = (midRow + r) * totalCols
            for (c in 0 until c21.numCols) {
                data[base + c] = c21[r, c]
            }
            for (c in 0 until c22.numCols) {
                data[base + midCol + c] = c22[r, c]
            }
        }
        return Matrix.fromFlat(totalRows, totalCols, data, copy = false)
    }

    private fun nextPowerOfTwo(n: Int): Int {
        var v = 1
        while (v < n) v = v shl 1
        return v
    }

    override fun equals(other: Any?): Boolean =
        other is Matrix &&
                rows == other.rows &&
                cols == other.cols &&
                data.contentEquals(other.data)

    override fun hashCode(): Int = 31 * rows + 31 * cols + data.contentHashCode()

    override fun toString(): String {
        return (0 until rows).joinToString("\n") { r ->
            (0 until cols).joinToString(" ") { c -> data[r * cols + c].toString() }
        }
    }
}

private fun multiplyWithMetrics(
    left: Matrix,
    right: Matrix,
    config: MatMulConfig,
    logMetrics: Boolean
): Matrix {
    if (!logMetrics) {
        return multiply(left, right, config)
    }

    val rowsA = left.numRows
    val colsB = right.numCols
    val inner = left.numCols
    lateinit var produced: Matrix
    val duration = measureTimeMillis {
        produced = multiply(left, right, config)
    }
    println(
        "Matrix multiply: ${rowsA}x${inner} * ${right.numRows}x${colsB} " +
            "algo=${config.algorithm} time=${duration}ms cost≈${rowsA * colsB * inner}"
    )
    return produced
}
