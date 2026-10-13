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

    private val db: FoodDatabase by lazy {
        File("../app/src/main/assets/foods.tsv").reader().use { foods ->
            File("../app/src/main/assets/portions.tsv").reader().use { portions ->
                FoodDatabase.parse(foods, portions)
            }
        }
    }

    private fun top(query: String, n: Int = 10) = db.search(query).take(n)

    @Test
    fun bundledDatabaseParsesAndSearches() {
        assertTrue(db.foods.size > 10_000, "${db.foods.size} foods")
        assertTrue(db.foods.count { it.source == "UK" } > 2800)

        // USDA food, still found by the id stored in existing recipes.
        val potatoes = assertNotNull(db.get("usda:11674"))
        assertEquals("Potatoes, baked, flesh and skin, without salt", potatoes.name)
        assertEquals(93.0, potatoes.per100g.amount(Nutrient.ENERGY))
        assertEquals(535.0, potatoes.per100g.amount(Nutrient.POTASSIUM))

        assertTrue(top("potato raw").any { it.name.startsWith("Potatoes") }, top("potato raw").joinToString { it.name })
        assertTrue(top("hamburger").isNotEmpty())
    }

    @Test
    fun ukFoodsAndNames() {
        val aubergine = assertNotNull(db.foods.firstOrNull { it.name == "Aubergine, raw" })
        assertEquals("UK", aubergine.source)
        assertTrue(aubergine.per100g.isKnown(Nutrient.VITAMIN_C))
        // UK foods rank first, and US words still find them.
        assertEquals("UK", top("aubergine").first().source)
        assertTrue(top("eggplant").any { it.name.startsWith("Aubergine") }, top("eggplant").joinToString { it.name })
        assertTrue(top("zucchini").any { it.name.startsWith("Courgette") })
        // USDA names are translated to UK English.
        assertTrue(top("beef mince").all { it.name.startsWith("Beef") && "mince" in it.name })
        assertTrue(top("ground beef").any { it.name.startsWith("Beef") && "mince" in it.name })
        assertTrue(db.foods.any { it.source == "US" && it.name.startsWith("Beef mince") })
        assertTrue(db.foods.none { it.source == "US" && it.name.startsWith("Eggplant") })
        // US brands are not included.
        assertTrue(db.foods.none { "KELLOGG" in it.name })
    }

    @Test
    fun takeawayAndRestaurantFoods() {
        assertTrue(top("tikka masala").isNotEmpty())
        assertTrue(top("big mac").any { it.name.contains("Big Mac") })
        assertTrue(top("doner kebab").isNotEmpty() || top("kebab").isNotEmpty())

        val sausageRoll = assertNotNull(top("greggs sausage roll").firstOrNull { it.source == "Greggs" })
        val item = assertNotNull(sausageRoll.defaultPortion())
        assertEquals("item", item.description)
        val kcal = sausageRoll.nutrientsFor(item.grams).amount(Nutrient.ENERGY)
        assertTrue(kcal in 300.0..400.0, "sausage roll $kcal kcal")

        val latte = assertNotNull(top("costa latte").firstOrNull { it.source == "Costa" })
        assertTrue(latte.portions.any { it.description == "Medium" }, latte.portions.toString())
        assertEquals("Medium", latte.defaultPortion()?.description)
        assertTrue(db.foods.count { it.source == "LEON" } > 50)
    }

    @Test
    fun householdPortions() {
        val banana = assertNotNull(db.get("usda:09040"))
        val medium = assertNotNull(banana.defaultPortion())
        assertEquals("medium (7\" to 7-7/8\" long)", medium.description)
        assertEquals(118.0, medium.grams)
        // 1 medium banana = 118 g, banana is 89 kcal / 100 g.
        assertEquals(89.0 * 1.18, banana.nutrientsFor(medium.grams).amount(Nutrient.ENERGY), 1e-9)

        // UK bananas borrow the same household measures.
        val ukBanana = assertNotNull(db.foods.firstOrNull { it.name == "Bananas, flesh only" })
        assertEquals(118.0, ukBanana.defaultPortion()?.grams)
    }

    @Test
    fun fastFoodChains() {
        for (chain in listOf("McDonald's", "KFC", "Burger King", "Subway", "Nando's", "Popeyes")) {
            assertTrue(db.foods.count { it.source == chain } > 50, chain)
        }

        // Weights published: per 100 g with a 1-item portion.
        val bigMac = assertNotNull(top("big mac").firstOrNull { it.source == "McDonald's" })
        assertTrue(bigMac.weightKnown)
        val bigMacKcal = bigMac.nutrientsFor(assertNotNull(bigMac.defaultPortion()).grams).amount(Nutrient.ENERGY)
        assertTrue(bigMacKcal in 450.0..560.0, "Big Mac $bigMacKcal")

        // Subway subs default to a 6-inch and offer a footlong.
        val sub = assertNotNull(top("subway italian bmt sub").firstOrNull { it.source == "Subway" })
        assertEquals("6-inch", sub.defaultPortion()?.description)
        assertTrue(sub.portions.any { it.description == "footlong" })

        // KFC doesn't publish weights: values are per item.
        val zinger = assertNotNull(top("zinger burger").firstOrNull { it.source == "KFC" })
        assertTrue(!zinger.weightKnown)
        val item = assertNotNull(zinger.defaultPortion())
        assertEquals(PER_ITEM_GRAMS, item.grams)
        assertTrue(zinger.nutrientsFor(item.grams).amount(Nutrient.ENERGY) in 400.0..550.0)

        assertTrue(db.foods.none { it.name.startsWith("McDonald's McDonald's") })
        assertTrue(db.foods.none { it.source == "Burger King" && "TEST" in it.name })
    }
}
