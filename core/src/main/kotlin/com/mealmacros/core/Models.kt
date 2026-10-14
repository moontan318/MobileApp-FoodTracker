package com.mealmacros.core

/** Nominal weight of one item for foods whose real weight isn't known (see [Food.weightKnown]). */
const val PER_ITEM_GRAMS = 100.0

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
    /** Where the data comes from: "UK" (CoFID), "US" (USDA) or a restaurant name. */
    val source: String = "",
    /**
     * False for restaurant items whose weight isn't published: their nutrients are per item,
     * stored as if one item weighed [PER_ITEM_GRAMS], and they can only be logged as items.
     */
    val weightKnown: Boolean = true,
) {
    fun nutrientsFor(grams: Double): NutrientProfile = per100g * (grams / 100.0)

    /** The portion to suggest first when logging a whole item (e.g. "medium"), if any. */
    fun defaultPortion(): Portion? =
        portions.firstOrNull { it.description == "item" }
            ?: portions.firstOrNull { it.description.startsWith("medium", ignoreCase = true) }
            ?: portions.firstOrNull().takeIf { isRestaurant } // e.g. a 6-inch sub
            ?: portions.firstOrNull { it.description.contains("medium", ignoreCase = true) }

    /** Menu items from a restaurant chain (as opposed to UK/US food tables or the user's own foods). */
    val isRestaurant: Boolean get() = !isCustom && source.isNotEmpty() && source != "UK" && source != "US"
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
    /** For imported recipes: the ingredient as the recipe wrote it, e.g. "1 small onion, chopped". */
    val sourceText: String = "",
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
    /** How many servings the recipe makes, if known. */
    val servings: Int? = null,
    /** Web page the recipe was imported from. */
    val sourceUrl: String? = null,
) {
    /** Weight of one serving of the finished recipe. */
    val servingWeight: Double? get() = servings?.takeIf { it > 0 && totalWeight > 0 }?.let { totalWeight / it }

    val rawWeight: Double get() = ingredients.sumOf { it.grams }

    val totalWeight: Double get() = cookedWeight?.takeIf { it > 0 } ?: rawWeight
}

/**
 * A logged recipe portion or single food. Nutrients are snapshotted so later edits
 * don't rewrite history.
 *
 * [grams] is 0 when the weight isn't known (restaurant items logged per item). When the
 * entry was logged as a household measure, [quantity] × [portion] describes it (e.g.
 * 2 × "medium"); [amountLabel] is the display text for that measure.
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
    val quantity: Double? = null,
    val portion: String? = null,
) {
    val weightKnown: Boolean get() = grams > 0

    /** The measure the entry was logged with, from its fields or (older entries) its label. */
    val measure: Pair<Double, String>?
        get() {
            if (quantity != null && portion != null) return quantity to portion
            val m = amountLabel?.let { LABEL.matchEntire(it) } ?: return null
            val q = m.groupValues[1].toDoubleOrNull() ?: return null
            return q to m.groupValues[2]
        }

    /** The same entry with everything (weight, quantity, nutrients) multiplied by [factor]. */
    fun scaled(factor: Double): DiaryEntry {
        val m = measure
        val q = m?.first?.times(factor)
        return copy(
            grams = grams * factor,
            nutrients = nutrients * factor,
            quantity = q,
            portion = m?.second,
            amountLabel = if (m != null) measureLabel(q!!, m.second) else amountLabel,
        )
    }

    companion object {
        private val LABEL = Regex("""^([0-9]+(?:\.[0-9]+)?) × (.+)$""")

        /** "2 × medium", with whole numbers shown without decimals and others to 2 places. */
        fun measureLabel(quantity: Double, portion: String): String = "${formatQuantity(quantity)} × $portion"

        fun formatQuantity(q: Double): String {
            val rounded = Math.round(q * 100) / 100.0
            return if (rounded == Math.floor(rounded)) rounded.toLong().toString() else rounded.toString()
        }
    }
}
