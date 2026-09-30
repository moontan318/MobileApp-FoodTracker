package com.mealmacros.core

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class NutritionCalculatorTest {

    private val potato = Food(
        "t:potato", "Potatoes", "Vegetables",
        NutrientProfile(mapOf(Nutrient.ENERGY to 93.0, Nutrient.CARBS to 21.0, Nutrient.PROTEIN to 2.5, Nutrient.VITAMIN_C to 9.6)),
    )
    private val beef = Food(
        "t:beef", "Beef", "Beef",
        NutrientProfile(mapOf(Nutrient.ENERGY to 254.0, Nutrient.PROTEIN to 17.0, Nutrient.FAT to 20.0, Nutrient.CARBS to 0.0)),
    )
    private val recipe = Recipe("r1", "Beef & potatoes", listOf(Ingredient.of(potato, 200.0), Ingredient.of(beef, 200.0)))

    @Test
    fun halfPortionContainsHalfOfEachIngredient() {
        val portion = NutritionCalculator.portion(recipe, 200.0)
        assertEquals(0.5, portion.fraction, 1e-9)
        assertEquals(listOf(100.0, 100.0), portion.shares.map { it.grams })
        assertEquals(93.0 + 254.0, portion.nutrients.amount(Nutrient.ENERGY), 1e-9)
        assertEquals(2.5 + 17.0, portion.nutrients.amount(Nutrient.PROTEIN), 1e-9)
        assertEquals(21.0, portion.nutrients.amount(Nutrient.CARBS), 1e-9)
        assertEquals(20.0, portion.nutrients.amount(Nutrient.FAT), 1e-9)
    }

    @Test
    fun cookedWeightChangesPortionFraction() {
        val cooked = recipe.copy(cookedWeight = 300.0)
        val portion = NutritionCalculator.portion(cooked, 150.0)
        assertEquals(0.5, portion.fraction, 1e-9)
        assertEquals(347.0, portion.nutrients.amount(Nutrient.ENERGY), 1e-9)
    }

    @Test
    fun missingDataIsFlaggedIncomplete() {
        val total = NutritionCalculator.recipeTotal(recipe)
        // Beef has no vitamin C value, potatoes has no fat value.
        assertTrue(total.isIncomplete(Nutrient.VITAMIN_C))
        assertTrue(total.isIncomplete(Nutrient.FAT))
        assertTrue(!total.isIncomplete(Nutrient.PROTEIN))
        assertEquals(19.2, total.amount(Nutrient.VITAMIN_C), 1e-9)
        assertTrue(!total.isKnown(Nutrient.IRON))
    }

    @Test
    fun per100gOfRecipe() {
        val per100 = NutritionCalculator.per100g(recipe)
        assertEquals((93.0 * 2 + 254.0 * 2) / 4, per100.amount(Nutrient.ENERGY), 1e-9)
    }

    @Test
    fun emptyRecipeIsSafe() {
        val empty = Recipe("e", "Empty", emptyList())
        val portion = NutritionCalculator.portion(empty, 100.0)
        assertEquals(0.0, portion.fraction)
        assertEquals(0.0, portion.nutrients.amount(Nutrient.ENERGY))
    }

    @Test
    fun bundledDatabaseParsesAndSearches() {
        val db = File("../app/src/main/assets/usda_sr28.tsv").reader().use { foods ->
            File("../app/src/main/assets/usda_portions.tsv").reader().use { portions ->
                FoodDatabase.parse(foods, portions)
            }
        }
        assertEquals(8789, db.foods.size)

        val potatoes = assertNotNull(db.get("usda:11674"))
        assertEquals("Potatoes, baked, flesh and skin, without salt", potatoes.name)
        assertEquals(93.0, potatoes.per100g.amount(Nutrient.ENERGY))
        assertEquals(535.0, potatoes.per100g.amount(Nutrient.POTASSIUM))

        val results = db.search("potato raw")
        assertTrue(results.isNotEmpty())
        assertTrue(results.take(10).any { it.name.startsWith("Potatoes") }, results.take(10).joinToString { it.name })
        assertTrue(db.search("ground beef").take(10).any { it.name.startsWith("Beef, ground") })
        assertTrue(db.search("hamburger").isNotEmpty())
    }

    @Test
    fun householdPortions() {
        val db = File("../app/src/main/assets/usda_sr28.tsv").reader().use { foods ->
            File("../app/src/main/assets/usda_portions.tsv").reader().use { portions ->
                FoodDatabase.parse(foods, portions)
            }
        }
        val banana = assertNotNull(db.get("usda:09040"))
        val medium = assertNotNull(banana.defaultPortion())
        assertEquals("medium (7\" to 7-7/8\" long)", medium.description)
        assertEquals(118.0, medium.grams)
        // 1 medium banana = 118 g, banana is 89 kcal / 100 g.
        assertEquals(89.0 * 1.18, banana.nutrientsFor(medium.grams).amount(Nutrient.ENERGY), 1e-9)
        assertTrue(db.foods.count { it.portions.isNotEmpty() } > 8000)
    }
}
