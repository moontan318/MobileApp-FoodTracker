package com.mealmacros.core

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RecipeImportTest {

    private val db: FoodDatabase by lazy {
        File("../app/src/main/assets/foods.tsv").reader().use { foods ->
            File("../app/src/main/assets/portions.tsv").reader().use { portions -> FoodDatabase.parse(foods, portions) }
        }
    }

    // Ingredient list of https://www.bbc.co.uk/food/recipes/cheesy_green_pasta_bake_92655
    private val bbcLines = listOf(
        "320g/11½oz short pasta, such as rigatoni, penne or conchiglie", "150g/5½oz broccoli florets",
        "1 tbsp olive oil", "1 small onion, finely chopped", "3 garlic cloves, crushed", "100g/3½oz baby spinach",
        "10g fresh basil, leaves picked", "10g flat leaf parsley, leaves picked",
        "400g tin cannellini beans, drained and rinsed",
        "50g/1¾oz fresh Parmesan, finely grated, plus extra for the topping ",
        "75ml/2½fl oz milk, such as full-fat, semi-skimmed or soya", "1 tsp lemon zest, plus extra to garnish",
        "1 tsp lemon juice", "½ tsp Dijon mustard", "75g/2¾oz mozzarella, torn into pieces",
        "50g/1¾oz mature cheddar or gruyère, grated", "salt and freshly ground black pepper",
        "dried red chilli flakes, to garnish (optional)",
    )

    private val page = """
        <html><head>
        <script type="application/ld+json">{"@context":"https://schema.org","@type":"BreadcrumbList"}</script>
        <script type="application/ld+json">
        {"@context":"https://schema.org","@graph":[{"@type":"WebPage"},{"@type":["Recipe"],
         "name":"Cheesy green pasta bake ","recipeYield":"Serves 4",
         "recipeIngredient":[${bbcLines.joinToString(",") { "\"" + it.replace("è", "&#232;") + "\"" }}],
         "nutrition":{"@type":"NutritionInformation","calories":"420kcal","proteinContent":"24.4g"}}]}
        </script></head><body>...</body></html>
    """.trimIndent()

    @Test
    fun extractsSchemaOrgRecipe() {
        val r = assertNotNull(RecipeImport.extractRecipe(page))
        assertEquals("Cheesy green pasta bake", r.name)
        assertEquals(4, r.servings)
        assertEquals(18, r.ingredientLines.size)
        assertEquals("50g/1¾oz mature cheddar or gruyère, grated", r.ingredientLines[15]) // entity decoded
        assertEquals("420kcal", r.nutrition["calories"])
        assertNull(RecipeImport.extractRecipe("<html>no recipe here</html>"))
    }

    @Test
    fun parsesIngredientLines() {
        fun p(s: String) = RecipeImport.parseLine(s)
        p("320g/11½oz short pasta, such as rigatoni").let {
            assertEquals(320.0, it.quantity); assertEquals(AmountUnit.GRAM, it.unit); assertEquals("short pasta", it.foodName)
        }
        p("75ml/2½fl oz milk, such as full-fat").let { assertEquals(75.0, it.quantity); assertEquals(AmountUnit.MILLILITRE, it.unit) }
        p("½ tsp Dijon mustard").let { assertEquals(0.5, it.quantity); assertEquals(AmountUnit.TEASPOON, it.unit) }
        p("1½ tbsp honey").let { assertEquals(1.5, it.quantity) }
        p("1 1/2 cups flour").let { assertEquals(1.5, it.quantity); assertEquals(AmountUnit.CUP, it.unit) }
        p("2-3 carrots").let { assertEquals(2.5, it.quantity); assertEquals(AmountUnit.COUNT, it.unit) }
        p("2 x 400g cans chickpeas").let { assertEquals(800.0, it.quantity); assertEquals("chickpeas", it.foodName) }
        p("400g tin cannellini beans, drained").let { assertEquals("cannellini beans", it.foodName) }
        p("3 garlic cloves, crushed").let { assertEquals("garlic", it.foodName); assertEquals("clove", it.sizeWord) }
        p("1 small onion, finely chopped").let { assertEquals("onion", it.foodName); assertEquals("small", it.sizeWord) }
        p("200ml orange juice").let { assertEquals("orange juice", it.foodName) } // "or" inside a word survives
        p("a pinch of salt").let { assertEquals(1.0, it.quantity); assertEquals(AmountUnit.PINCH, it.unit) }
        assertTrue(RecipeImport.isSkippable(p("salt and freshly ground black pepper")))
        assertTrue(RecipeImport.isSkippable(p("naan breads or cooked basmati rice, to serve")))
        assertFalse(RecipeImport.isSkippable(p("1 tbsp olive oil")))
    }

    @Test
    fun everyStapleExistsInTheDatabase() {
        val missing = RecipeImport.stapleFoodNames.filter { db.byName(it) == null }
        assertTrue(missing.isEmpty(), "missing: $missing")
    }

    @Test
    fun importsTheBbcRecipe() {
        val r = RecipeImport.import(assertNotNull(RecipeImport.extractRecipe(page)), db)
        assertEquals(2, r.skipped.size) // salt & pepper, chilli flakes to garnish
        assertEquals(16, r.ingredients.size)
        fun ing(start: String) = r.ingredients.first { it.parsed.original.startsWith(start) }
        assertEquals("Pasta, white, dried, raw", ing("320g").food?.name)
        assertEquals(320.0, ing("320g").grams)
        assertEquals("Onions, raw", ing("1 small onion").food?.name)
        assertTrue(ing("1 small onion").grams in 40.0..110.0)
        assertEquals("Garlic, raw", ing("3 garlic").food?.name)
        assertTrue(ing("3 garlic").grams in 6.0..20.0)
        assertEquals("Milk, semi-skimmed, pasteurised, average", ing("75ml").food?.name)
        assertEquals("Cheese, Parmesan, fresh", ing("50g/1¾oz fresh Parmesan").food?.name)
        assertEquals("Beans, cannellini, canned, re-heated, drained", ing("400g tin").food?.name)
        assertEquals(240.0, ing("400g tin").grams, 1e-9) // drained weight of a 400 g tin
        assertTrue(ing("400g tin").needsReview)
        assertTrue(ing("1 tbsp olive oil").grams in 12.0..16.0)
        assertTrue(r.ingredients.all { it.food != null })
        // Sanity check against the BBC's own estimate of 420 kcal x 4 servings.
        val kcal = r.ingredients.sumOf { it.food!!.nutrientsFor(it.grams).amount(Nutrient.ENERGY) }
        assertTrue(kcal in 1400.0..2600.0, "recipe kcal $kcal")
    }

    @Test
    fun keyWordsFromTheOriginalLineDecideTheMatch() {
        val r = RecipeImport.import(WebRecipe("Curry", 4, listOf("6 chicken thighs boneless and skinless", "400g can chopped tomatoes")), db)
        assertTrue(r.ingredients[0].food!!.name.contains("thigh"))
        assertEquals("Tomatoes, canned, whole contents", r.ingredients[1].food?.name)
    }
}
