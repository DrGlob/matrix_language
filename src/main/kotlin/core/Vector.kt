package org.example.core

/**
 * Иммутабельный одномерный массив значений Double.
 *
 * Операции:
 * - [map]: поэлементное преобразование с сохранением длины,
 * - [reduce]: свёртка с числовым аккумулятором,
 * - [zip]: объединение с другим вектором до минимальной длины,
 * - [unzip]: раскладывание списка пар обратно в два вектора.
 */
class Vector private constructor(private val data: DoubleArray) {
    val size: Int get() = data.size

    operator fun get(index: Int): Double {
        require(index in 0 until size) { "Index $index is out of bounds for vector of size $size" }
        return data[index]
    }

    fun toList(): List<Double> = data.toList()

    fun map(transform: (Double) -> Double): Vector =
        Vector(DoubleArray(size) { idx -> transform(data[idx]) })

    fun reduce(initial: Double, op: (Double, Double) -> Double): Double {
        var acc = initial
        for (value in data) {
            acc = op(acc, value)
        }
        return acc
    }

    fun zip(other: Vector): List<Pair<Double, Double>> {
        val length = minOf(size, other.size)
        return List(length) { idx -> data[idx] to other.data[idx] }
    }

    fun toMatrix(asColumn: Boolean = true): Matrix =
        if (asColumn) {
            Matrix.fromFlat(size, 1, data, copy = true)
        } else {
            Matrix.fromFlat(1, size, data, copy = true)
        }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Vector) return false
        return data.contentEquals(other.data)
    }

    override fun hashCode(): Int = data.contentHashCode()

    override fun toString(): String =
        data.joinToString(prefix = "[", postfix = "]") { "%.4f".format(it) }

    companion object {
        fun fromList(values: List<Double>): Vector = Vector(values.toDoubleArray())

        fun fromMatrix(matrix: Matrix): Vector {
            require(matrix.numRows == 1 || matrix.numCols == 1) {
                "Matrix must have exactly one row or one column to convert to Vector"
            }
            val size = matrix.numRows * matrix.numCols
            val values = DoubleArray(size)
            var idx = 0
            for (r in 0 until matrix.numRows) {
                for (c in 0 until matrix.numCols) {
                    values[idx++] = matrix[r, c]
                }
            }
            return Vector(values)
        }

        fun fromArray(values: DoubleArray): Vector = Vector(values.copyOf())

        fun zip(a: Vector, b: Vector): List<Pair<Double, Double>> = a.zip(b)

        fun unzip(pairs: List<Pair<Double, Double>>): Pair<Vector, Vector> {
            val first = DoubleArray(pairs.size)
            val second = DoubleArray(pairs.size)
            for (i in pairs.indices) {
                val pair = pairs[i]
                first[i] = pair.first
                second[i] = pair.second
            }
            return Vector(first) to Vector(second)
        }
    }
}
