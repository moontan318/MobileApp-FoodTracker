package com.mealmacros.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class OpenFoodFactsTest {

    // Shape of an Open Food Facts search response (values are test data).
    private val search = """
        {"count":2,"page":1,"products":[
          {"code":"5000328000001","product_name":"Oven Baked Cheese & Onion","brands":"Walkers,PepsiCo",
           "quantity":"6 x 22g","serving_size":"22 g","serving_quantity":22,
           "nutriments":{"energy-kcal_100g":432,"energy_100g":1820,"fat_100g":12,"saturated-fat_100g":1.1,
             "carbohydrates_100g":72,"sugars_100g":6.2,"fiber_100g":4.9,"proteins_100g":6.4,"salt_100g":1.6,
             "vitamin-c_100g":0.0105,"calcium_100g":"0.04"}},
          {"code":"123","product_name":"Mystery product","nutriments":{}}
        ]}
    """.trimIndent()

    @Test
    fun parsesSearchResults() {
        val foods = OpenFoodFacts.parseSearch(search)
        assertEquals(1, foods.size) // the product without nutrition is dropped
        val crisps = foods[0]
        assertEquals("off:5000328000001", crisps.id)
        assertEquals("Walkers Oven Baked Cheese & Onion", crisps.name)
        assertEquals("Walkers", crisps.source)
        assertEquals(432.0, crisps.per100g.amount(Nutrient.ENERGY))
        assertEquals(640.0, crisps.per100g.amount(Nutrient.SODIUM), 1e-9) // 1.6 g salt
        assertEquals(10.5, crisps.per100g.amount(Nutrient.VITAMIN_C), 1e-9) // grams -> mg
        assertEquals(40.0, crisps.per100g.amount(Nutrient.CALCIUM), 1e-9)
        assertEquals("serving (22 g)", crisps.defaultPortion()?.description)
        assertEquals(22.0, crisps.defaultPortion()?.grams)
    }

    @Test
    fun packPortionAndKilojoules() {
        val body = """{"status":1,"product":{"code":"0001","product_name":"Cornish Pasty","brands":"Ginsters",
            "quantity":"227 g","nutriments":{"energy_100g":1180,"fat_100g":17,"carbohydrates_100g":27,"proteins_100g":6.5}}}"""
        val pasty = assertNotNull(OpenFoodFacts.parseProductResponse(body))
        assertEquals("Ginsters Cornish Pasty", pasty.name)
        assertEquals(1180 / 4.184, pasty.per100g.amount(Nutrient.ENERGY), 1e-9)
        assertEquals(listOf(Portion("pack", 227.0)), pasty.portions)
        assertNull(OpenFoodFacts.parseProductResponse("""{"status":0,"status_verbose":"product not found"}"""))
    }

    @Test
    fun urls() {
        val url = OpenFoodFacts.searchUrl("ginsters: pasty")
        assertTrue(url.startsWith("https://search.openfoodfacts.org/search?"))
        assertTrue(url.endsWith("q=ginsters%20pasty%20countries_tags%3A%22en%3Aunited-kingdom%22"), url)
        assertTrue(OpenFoodFacts.productUrl("5 000328 000001").contains("/product/5000328000001.json"))
    }

    @Test
    fun tidiesCase() {
        assertEquals("Walkers", OpenFoodFacts.tidyCase("WALKERS"))
        assertEquals("Sainsbury's", OpenFoodFacts.tidyCase("SAINSBURY'S"))
        assertEquals("M&S", OpenFoodFacts.tidyCase("M&S"))
        assertEquals("Cornish pasty", OpenFoodFacts.tidyCase("cornish pasty"))
        assertEquals("Oven Baked Cheese & Onion", OpenFoodFacts.tidyCase("Oven Baked Cheese & Onion"))
    }

    @Test
    fun parsesSearchServiceHits() {
        // Shape of a search.openfoodfacts.org response: brands is a list, and some products repeat.
        val body = """
            {"hits":[
              {"code":"3038550","product_name":"smoker ham and Cheddar sandwich","brands":["Tesco Smoked Ham and Cheddar sandwich"],
               "nutriments":{"energy-kcal_100g":235,"proteins_100g":13.4,"carbohydrates_100g":21,"fat_100g":10.2,"salt_100g":0.88}},
              {"code":"03038550","product_name":"smoker ham and Cheddar sandwich","brands":["Tesco Smoked Ham and Cheddar sandwich"],
               "nutriments":{"energy-kcal_100g":235,"proteins_100g":13.4,"carbohydrates_100g":21,"fat_100g":10.2,"salt_100g":0.88}},
              {"code":"5000328210438","product_name":"Walkers oven baked - prawn cocktail","brands":["Walkers"],
               "nutriments":{"energy-kcal_100g":432,"proteins_100g":6.05,"carbohydrates_100g":72,"fat_100g":12.5}}
            ],"count":3,"page":1}
        """.trimIndent()
        val foods = OpenFoodFacts.parseSearch(body)
        assertEquals(listOf("Tesco Smoked Ham and Cheddar sandwich", "Walkers oven baked - prawn cocktail"), foods.map { it.name })
        assertEquals("Tesco", foods[0].source)
        assertEquals(352.0, foods[0].per100g.amount(Nutrient.SODIUM), 1e-9)
    }
}
