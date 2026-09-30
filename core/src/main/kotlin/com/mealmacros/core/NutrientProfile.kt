package com.mealmacros.core

/**
 * An amount of each nutrient. Nutrients that are absent from [values] are unknown
 * (the source data had no value), which is different from a known value of zero.
 *
 * [incomplete] lists nutrients whose total is a lower bound because at least one
 * contributing food had no data for it.
 */
data class NutrientProfile(
    val values: Map<Nutrient, Double> = emptyMap(),
    val incomplete: Set<Nutrient> = emptySet(),
) {
    operator fun get(nutrient: Nutrient): Double? = values[nutrient]

    fun amount(nutrient: Nutrient): Double = values[nutrient] ?: 0.0

    fun isKnown(nutrient: Nutrient): Boolean = nutrient in values

    fun isIncomplete(nutrient: Nutrient): Boolean = nutrient in incomplete

    operator fun times(factor: Double): NutrientProfile =
        NutrientProfile(values.mapValues { it.value * factor }, incomplete)

    /**
     * Adds two profiles. If a nutrient is known on one side but not the other the
     * result keeps the known amount and is flagged as incomplete.
     */
    operator fun plus(other: NutrientProfile): NutrientProfile {
        val sum = HashMap<Nutrient, Double>()
        val missing = HashSet<Nutrient>(incomplete + other.incomplete)
        for (n in Nutrient.entries) {
            val a = values[n]
            val b = other.values[n]
            if (a != null || b != null) sum[n] = (a ?: 0.0) + (b ?: 0.0)
            if ((a == null) != (b == null)) missing += n
        }
        return NutrientProfile(sum, missing)
    }

    /** Share of energy coming from protein, carbohydrate and fat (0..1 each). */
    fun macroEnergySplit(): MacroSplit {
        val p = amount(Nutrient.PROTEIN) * 4
        val c = amount(Nutrient.CARBS) * 4
        val f = amount(Nutrient.FAT) * 9
        val total = p + c + f
        if (total <= 0.0) return MacroSplit(0.0, 0.0, 0.0)
        return MacroSplit(p / total, c / total, f / total)
    }

    companion object {
        val EMPTY = NutrientProfile()

        fun sum(profiles: Iterable<NutrientProfile>): NutrientProfile {
            var total: NutrientProfile? = null
            for (p in profiles) total = total?.plus(p) ?: p
            return total ?: EMPTY
        }
    }
}

data class MacroSplit(val protein: Double, val carbs: Double, val fat: Double)
