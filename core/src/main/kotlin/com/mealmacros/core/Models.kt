package com.mealmacros.core

/** A household measure, e.g. "medium (7" to 7-7/8" long)" = 118 g for one banana. */
data class Portion(val description: String, val grams: Double)

/** A food with its nutrient content per 100 g and its household measures. */
data class Food(
    val id: String,
    val name: String,
    val group: String,
    val per100g: NutrientProfile,
    val commonName: String = "",
    val isCustom: Boolean = false,
    val portions: List<Portion> = emptyList(),
) {
    fun nutrientsFor(grams: Double): NutrientProfile = per100g * (grams / 100.0)

    /** The portion to suggest first when logging a whole item (e.g. "medium"), if any. */
    fun defaultPortion(): Portion? =
        portions.firstOrNull { it.description.startsWith("medium") }
            ?: portions.firstOrNull { it.description.contains("medium") }
}

/**
 * An ingredient in a recipe. A copy of the food's per-100 g nutrients is kept so a
 * recipe stays self-contained even if the food database changes.
 */
data class Ingredient(
    val foodId: String,
    val name: String,
    val grams: Double,
    val per100g: NutrientProfile,
) {
    val nutrients: NutrientProfile get() = per100g * (grams / 100.0)

    companion object {
        fun of(food: Food, grams: Double) = Ingredient(food.id, food.name, grams, food.per100g)
    }
}

/**
 * A recipe. [cookedWeight] is the optional weight of the finished dish; cooking
 * usually changes weight (water lost or absorbed), so when it is set portions are
 * calculated as a fraction of it instead of the sum of the raw ingredients.
 */
data class Recipe(
    val id: String,
    val name: String,
    val ingredients: List<Ingredient>,
    val cookedWeight: Double? = null,
    val notes: String = "",
    val updatedAt: Long = 0L,
) {
    val rawWeight: Double get() = ingredients.sumOf { it.grams }

    val totalWeight: Double get() = cookedWeight?.takeIf { it > 0 } ?: rawWeight
}

/**
 * A logged recipe portion or single food. Nutrients are snapshotted so later edits
 * don't rewrite history. [amountLabel] describes a household measure, e.g. "1 × medium".
 */
data class DiaryEntry(
    val id: String,
    val name: String,
    val grams: Double,
    val timestamp: Long,
    val nutrients: NutrientProfile,
    val recipeId: String? = null,
    val foodId: String? = null,
    val amountLabel: String? = null,
)
