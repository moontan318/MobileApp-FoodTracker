package com.mealmacros.core

/** How much of one ingredient ends up in a portion. */
data class IngredientShare(
    val ingredient: Ingredient,
    /** Raw weight of this ingredient contained in the portion. */
    val grams: Double,
    val nutrients: NutrientProfile,
)

data class PortionNutrition(
    val portionGrams: Double,
    /** Portion as a fraction of the whole recipe (0.5 = half the recipe). */
    val fraction: Double,
    val nutrients: NutrientProfile,
    val shares: List<IngredientShare>,
)

object NutritionCalculator {

    /** Nutrients in the whole recipe. */
    fun recipeTotal(recipe: Recipe): NutrientProfile =
        NutrientProfile.sum(recipe.ingredients.map { it.nutrients })

    /** Nutrients in 100 g of the finished recipe. */
    fun per100g(recipe: Recipe): NutrientProfile {
        val total = recipe.totalWeight
        if (total <= 0.0) return NutrientProfile.EMPTY
        return recipeTotal(recipe) * (100.0 / total)
    }

    /**
     * Nutrients in a portion of [portionGrams] of the finished recipe.
     *
     * Example: potatoes 200 g + beef 200 g (400 g total). A 200 g portion is half
     * the recipe, so it holds 100 g of potatoes and 100 g of beef.
     */
    fun portion(recipe: Recipe, portionGrams: Double): PortionNutrition {
        val total = recipe.totalWeight
        val fraction = if (total > 0.0) portionGrams.coerceAtLeast(0.0) / total else 0.0
        val shares = recipe.ingredients.map {
            IngredientShare(it, it.grams * fraction, it.nutrients * fraction)
        }
        return PortionNutrition(
            portionGrams = portionGrams,
            fraction = fraction,
            nutrients = NutrientProfile.sum(shares.map { it.nutrients }),
            shares = shares,
        )
    }

    /** Percentage of the daily value, or null if the nutrient has no DV. */
    fun percentDailyValue(nutrient: Nutrient, amount: Double): Double? =
        nutrient.dailyValue?.let { amount / it * 100.0 }

    fun kcalToKj(kcal: Double): Double = kcal * 4.184
}
