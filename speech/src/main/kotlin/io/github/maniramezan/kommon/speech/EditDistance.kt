package io.github.maniramezan.kommon.speech

/** Word-level minimum edit distance with a deterministic backtrace. */
internal object EditDistance {
    /** One step turning the reference sequence into the heard sequence. */
    sealed interface Operation {
        /** Reference index [reference] equals heard index [heard]. */
        data class Keep(
            val reference: Int,
            val heard: Int,
        ) : Operation

        /** Reference index [reference] was replaced by heard index [heard]. */
        data class Substitute(
            val reference: Int,
            val heard: Int,
        ) : Operation

        /** Reference index [reference] is missing from the heard sequence. */
        data class Delete(
            val reference: Int,
        ) : Operation

        /** Heard index [heard] has no counterpart in the reference. */
        data class Insert(
            val heard: Int,
        ) : Operation
    }

    /**
     * The cheapest operations, in order, that turn [reference] into [heard].
     *
     * Substitutions, deletions, and insertions each cost one. On ties the backtrace prefers a keep
     * or substitution, then a deletion, then an insertion, so results are stable.
     */
    fun operations(
        reference: List<String>,
        heard: List<String>,
    ): List<Operation> {
        val cost = costMatrix(reference, heard)
        val result = mutableListOf<Operation>()
        var r = reference.size
        var h = heard.size
        while (r > 0 || h > 0) {
            val same = r > 0 && h > 0 && reference[r - 1] == heard[h - 1]
            when {
                r > 0 && h > 0 && cost[r][h] == cost[r - 1][h - 1] + substitutionCost(same) -> {
                    result += if (same) Operation.Keep(r - 1, h - 1) else Operation.Substitute(r - 1, h - 1)
                    r--
                    h--
                }
                r > 0 && cost[r][h] == cost[r - 1][h] + 1 -> {
                    result += Operation.Delete(r - 1)
                    r--
                }
                else -> {
                    result += Operation.Insert(h - 1)
                    h--
                }
            }
        }
        return result.reversed()
    }

    private fun substitutionCost(same: Boolean): Int = if (same) 0 else 1

    private fun costMatrix(
        reference: List<String>,
        heard: List<String>,
    ): Array<IntArray> {
        val cost = Array(reference.size + 1) { IntArray(heard.size + 1) }
        for (r in 0..reference.size) cost[r][0] = r
        for (h in 0..heard.size) cost[0][h] = h
        for (r in 1..reference.size) {
            for (h in 1..heard.size) {
                val diagonal = cost[r - 1][h - 1] + substitutionCost(reference[r - 1] == heard[h - 1])
                cost[r][h] = minOf(diagonal, cost[r - 1][h] + 1, cost[r][h - 1] + 1)
            }
        }
        return cost
    }
}
