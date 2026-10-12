package dev.qtremors.acqua.domain

object CollectionSelection {
    /** One-based, inclusive ranges. Reject malformed or out-of-bounds selections. */
    fun parse(value: String, count: Int): Set<Int>? = runCatching {
        require(count > 0 && value.isNotBlank())
        buildSet {
            value.split(',').forEach { token ->
                val parts = token.trim().split('-')
                require(parts.size in 1..2)
                val start = parts[0].trim().toInt()
                val end = if (parts.size == 2) parts[1].trim().toInt() else start
                require(start in 1..count && end in start..count)
                addAll((start..end).map { it - 1 })
            }
        }
    }.getOrNull()
}
