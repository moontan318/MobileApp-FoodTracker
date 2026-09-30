package com.mealmacros.core

enum class NutrientUnit(val symbol: String) {
    KCAL("kcal"),
    GRAM("g"),
    MILLIGRAM("mg"),
    MICROGRAM("µg"),
}

enum class NutrientCategory(val title: String) {
    ENERGY("Energy"),
    MACRO("Macronutrients"),
    VITAMIN("Vitamins"),
    MINERAL("Minerals"),
}

/**
 * Nutrients tracked by the app. Values are always stored per 100 g of food.
 *
 * [dailyValue] is the adult Daily Value used on US nutrition labels (FDA, 2016
 * rule) and is used to show a "% of daily value" figure; null where no DV exists.
 * [indent] marks sub-components (e.g. sugars within carbohydrates) for display.
 */
enum class Nutrient(
    val key: String,
    val label: String,
    val unit: NutrientUnit,
    val category: NutrientCategory,
    val dailyValue: Double?,
    val indent: Boolean = false,
) {
    ENERGY("energy", "Energy", NutrientUnit.KCAL, NutrientCategory.ENERGY, 2000.0),

    PROTEIN("protein", "Protein", NutrientUnit.GRAM, NutrientCategory.MACRO, 50.0),
    CARBS("carbs", "Carbohydrates", NutrientUnit.GRAM, NutrientCategory.MACRO, 275.0),
    FIBER("fiber", "Fibre", NutrientUnit.GRAM, NutrientCategory.MACRO, 28.0, indent = true),
    SUGARS("sugars", "Sugars", NutrientUnit.GRAM, NutrientCategory.MACRO, null, indent = true),
    FAT("fat", "Fat", NutrientUnit.GRAM, NutrientCategory.MACRO, 78.0),
    SATURATED_FAT("saturated_fat", "Saturated fat", NutrientUnit.GRAM, NutrientCategory.MACRO, 20.0, indent = true),
    MONO_FAT("mono_fat", "Monounsaturated fat", NutrientUnit.GRAM, NutrientCategory.MACRO, null, indent = true),
    POLY_FAT("poly_fat", "Polyunsaturated fat", NutrientUnit.GRAM, NutrientCategory.MACRO, null, indent = true),
    CHOLESTEROL("cholesterol", "Cholesterol", NutrientUnit.MILLIGRAM, NutrientCategory.MACRO, 300.0),
    WATER("water", "Water", NutrientUnit.GRAM, NutrientCategory.MACRO, null),

    VITAMIN_A("vitamin_a", "Vitamin A (RAE)", NutrientUnit.MICROGRAM, NutrientCategory.VITAMIN, 900.0),
    VITAMIN_C("vitamin_c", "Vitamin C", NutrientUnit.MILLIGRAM, NutrientCategory.VITAMIN, 90.0),
    VITAMIN_D("vitamin_d", "Vitamin D", NutrientUnit.MICROGRAM, NutrientCategory.VITAMIN, 20.0),
    VITAMIN_E("vitamin_e", "Vitamin E", NutrientUnit.MILLIGRAM, NutrientCategory.VITAMIN, 15.0),
    VITAMIN_K("vitamin_k", "Vitamin K", NutrientUnit.MICROGRAM, NutrientCategory.VITAMIN, 120.0),
    THIAMIN("thiamin", "Thiamin (B1)", NutrientUnit.MILLIGRAM, NutrientCategory.VITAMIN, 1.2),
    RIBOFLAVIN("riboflavin", "Riboflavin (B2)", NutrientUnit.MILLIGRAM, NutrientCategory.VITAMIN, 1.3),
    NIACIN("niacin", "Niacin (B3)", NutrientUnit.MILLIGRAM, NutrientCategory.VITAMIN, 16.0),
    PANTOTHENIC_ACID("pantothenic_acid", "Pantothenic acid (B5)", NutrientUnit.MILLIGRAM, NutrientCategory.VITAMIN, 5.0),
    VITAMIN_B6("vitamin_b6", "Vitamin B6", NutrientUnit.MILLIGRAM, NutrientCategory.VITAMIN, 1.7),
    FOLATE("folate", "Folate (DFE)", NutrientUnit.MICROGRAM, NutrientCategory.VITAMIN, 400.0),
    VITAMIN_B12("vitamin_b12", "Vitamin B12", NutrientUnit.MICROGRAM, NutrientCategory.VITAMIN, 2.4),
    CHOLINE("choline", "Choline", NutrientUnit.MILLIGRAM, NutrientCategory.VITAMIN, 550.0),

    CALCIUM("calcium", "Calcium", NutrientUnit.MILLIGRAM, NutrientCategory.MINERAL, 1300.0),
    IRON("iron", "Iron", NutrientUnit.MILLIGRAM, NutrientCategory.MINERAL, 18.0),
    MAGNESIUM("magnesium", "Magnesium", NutrientUnit.MILLIGRAM, NutrientCategory.MINERAL, 420.0),
    PHOSPHORUS("phosphorus", "Phosphorus", NutrientUnit.MILLIGRAM, NutrientCategory.MINERAL, 1250.0),
    POTASSIUM("potassium", "Potassium", NutrientUnit.MILLIGRAM, NutrientCategory.MINERAL, 4700.0),
    SODIUM("sodium", "Sodium", NutrientUnit.MILLIGRAM, NutrientCategory.MINERAL, 2300.0),
    ZINC("zinc", "Zinc", NutrientUnit.MILLIGRAM, NutrientCategory.MINERAL, 11.0),
    COPPER("copper", "Copper", NutrientUnit.MILLIGRAM, NutrientCategory.MINERAL, 0.9),
    MANGANESE("manganese", "Manganese", NutrientUnit.MILLIGRAM, NutrientCategory.MINERAL, 2.3),
    SELENIUM("selenium", "Selenium", NutrientUnit.MICROGRAM, NutrientCategory.MINERAL, 55.0);

    companion object {
        private val byKey = entries.associateBy { it.key }

        fun fromKey(key: String): Nutrient? = byKey[key]

        fun inCategory(category: NutrientCategory): List<Nutrient> = entries.filter { it.category == category }
    }
}
