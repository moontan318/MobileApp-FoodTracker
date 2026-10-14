package com.mealmacros.core

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject
import java.net.URLEncoder

/**
 * Packaged foods from Open Food Facts (openfoodfacts.org, Open Database Licence): parsing
 * of its API responses and the URLs to call. Values on packs are per 100 g.
 */
object OpenFoodFacts {

    const val ID_PREFIX = "off:"
    const val GROUP = "Supermarket"

    /** Open Food Facts asks apps to identify themselves. */
    const val USER_AGENT = "MealMacros/1.0 (Android; github.com/moontan318/MobileApp-FoodTracker)"

    private const val FIELDS = "code,product_name,product_name_en,brands,quantity,serving_size,serving_quantity,nutriments"

    /**
     * Full-text search of UK products with Open Food Facts' search service (search-a-licious).
     * The older cgi/search.pl and api/v2/search endpoints often answer 503 when busy.
     */
    fun searchUrl(query: String, pageSize: Int = 30): String {
        // The query is Lucene syntax: keep the user's words only, so ":" or quotes can't break it.
        val words = query.replace(Regex("""[^\p{L}\p{N}'&\s-]"""), " ").trim().replace(Regex("""\s+"""), " ")
        val q = "$words countries_tags:\"en:united-kingdom\""
        return "https://search.openfoodfacts.org/search?langs=en&page_size=$pageSize&fields=$FIELDS&q=" +
            URLEncoder.encode(q, "UTF-8").replace("+", "%20")
    }

    fun productUrl(barcode: String): String =
        "https://world.openfoodfacts.org/api/v2/product/${barcode.filter { it.isDigit() }}.json?fields=$FIELDS"

    /**
     * Open Food Facts nutrient key -> (our nutrient, multiplier from the stored unit).
     * Open Food Facts stores all `_100g` values in grams, except energy-kcal.
     */
    val NUTRIENT_KEYS: List<Triple<String, Nutrient, Double>> = listOf(
        Triple("energy-kcal", Nutrient.ENERGY, 1.0),
        Triple("proteins", Nutrient.PROTEIN, 1.0),
        Triple("carbohydrates", Nutrient.CARBS, 1.0),
        Triple("sugars", Nutrient.SUGARS, 1.0),
        Triple("fiber", Nutrient.FIBER, 1.0),
        Triple("fat", Nutrient.FAT, 1.0),
        Triple("saturated-fat", Nutrient.SATURATED_FAT, 1.0),
        Triple("monounsaturated-fat", Nutrient.MONO_FAT, 1.0),
        Triple("polyunsaturated-fat", Nutrient.POLY_FAT, 1.0),
        Triple("cholesterol", Nutrient.CHOLESTEROL, 1000.0),
        Triple("vitamin-a", Nutrient.VITAMIN_A, 1e6),
        Triple("vitamin-c", Nutrient.VITAMIN_C, 1000.0),
        Triple("vitamin-d", Nutrient.VITAMIN_D, 1e6),
        Triple("vitamin-e", Nutrient.VITAMIN_E, 1000.0),
        Triple("vitamin-k", Nutrient.VITAMIN_K, 1e6),
        Triple("vitamin-b1", Nutrient.THIAMIN, 1000.0),
        Triple("vitamin-b2", Nutrient.RIBOFLAVIN, 1000.0),
        Triple("vitamin-pp", Nutrient.NIACIN, 1000.0),
        Triple("pantothenic-acid", Nutrient.PANTOTHENIC_ACID, 1000.0),
        Triple("vitamin-b6", Nutrient.VITAMIN_B6, 1000.0),
        Triple("vitamin-b9", Nutrient.FOLATE, 1e6),
        Triple("vitamin-b12", Nutrient.VITAMIN_B12, 1e6),
        Triple("calcium", Nutrient.CALCIUM, 1000.0),
        Triple("iron", Nutrient.IRON, 1000.0),
        Triple("magnesium", Nutrient.MAGNESIUM, 1000.0),
        Triple("phosphorus", Nutrient.PHOSPHORUS, 1000.0),
        Triple("potassium", Nutrient.POTASSIUM, 1000.0),
        Triple("zinc", Nutrient.ZINC, 1000.0),
        Triple("copper", Nutrient.COPPER, 1000.0),
        Triple("manganese", Nutrient.MANGANESE, 1000.0),
        Triple("selenium", Nutrient.SELENIUM, 1e6),
    )

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    /**
     * Products from a search response ("hits" from the search service, "products" from the
     * older API), best first. Products without basic nutrition, and repeats, are dropped.
     */
    fun parseSearch(body: String): List<Food> {
        val root = json.parseToJsonElement(body).jsonObject
        val products = (root["hits"] ?: root["products"]) as? JsonArray ?: return emptyList()
        return products.mapNotNull { (it as? JsonObject)?.let(::parseProduct) }
            .distinctBy { it.name.lowercase() to it.per100g.amount(Nutrient.ENERGY).toInt() }
    }

    /** The product in a barcode lookup response, or null when it isn't known. */
    fun parseProductResponse(body: String): Food? {
        val root = json.parseToJsonElement(body).jsonObject
        return (root["product"] as? JsonObject)?.let(::parseProduct)
    }

    fun parseProduct(p: JsonObject): Food? {
        val code = p.text("code") ?: return null
        val productName = (p.text("product_name_en") ?: p.text("product_name"))?.trim()?.takeIf { it.isNotEmpty() }
            ?.let(::tidyCase) ?: return null
        val n = p["nutriments"] as? JsonObject ?: return null
        fun value(key: String): Double? = n.number("${key}_100g")

        val values = HashMap<Nutrient, Double>()
        for ((key, nutrient, multiplier) in NUTRIENT_KEYS) {
            value(key)?.let { values[nutrient] = it * multiplier }
        }
        if (Nutrient.ENERGY !in values) value("energy")?.let { values[Nutrient.ENERGY] = it / 4.184 } // kJ
        value("sodium")?.let { values[Nutrient.SODIUM] = it * 1000.0 } ?: value("salt")?.let { values[Nutrient.SODIUM] = it * 400.0 }
        val required = listOf(Nutrient.ENERGY, Nutrient.PROTEIN, Nutrient.CARBS, Nutrient.FAT)
        if (required.any { it !in values }) return null
        if (values.getValue(Nutrient.ENERGY) !in 0.0..950.0) return null // obviously wrong data

        // "brands" is "Walkers,PepsiCo" from the product API and ["Walkers"] from the search service.
        val brands = (p["brands"] as? JsonArray)?.firstOrNull()?.let { (it as? JsonPrimitive)?.contentOrNull } ?: p.text("brands")
        var brand = brands?.split(',')?.firstOrNull()?.trim()?.takeIf { it.isNotEmpty() }?.let(::tidyCase)
        var name = productName
        if (brand != null && brand.split(' ').size >= 4) {
            // The full product name typed into the brand ("Tesco Smoked Ham and Cheddar sandwich").
            name = brand
            brand = brand.substringBefore(' ')
        }
        if (brand != null && !name.startsWith(brand, ignoreCase = true)) name = "$brand $name"
        return Food(
            id = ID_PREFIX + code,
            name = name,
            group = GROUP,
            per100g = NutrientProfile(values),
            portions = portions(p.text("serving_size"), p.number("serving_quantity"), p.text("quantity")),
            source = brand ?: "Product",
        )
    }

    /** "WALKERS" -> "Walkers", "cornish pasty" -> "Cornish pasty"; mixed case is left alone. */
    fun tidyCase(text: String): String = when {
        text.length > 4 && text == text.uppercase() && text != text.lowercase() ->
            text.lowercase().split(' ').joinToString(" ") { w -> w.replaceFirstChar { it.uppercase() } }
        text == text.lowercase() -> text.replaceFirstChar { it.uppercase() }
        else -> text
    }

    /** "serving (30 g)" from the serving size, and "pack" when the pack is a single weight. */
    fun portions(servingSize: String?, servingQuantity: Double?, quantity: String?): List<Portion> {
        val list = ArrayList<Portion>()
        servingQuantity?.takeIf { it in 1.0..3000.0 }?.let { g ->
            val label = servingSize?.trim()?.takeIf { it.isNotEmpty() && it.length <= 40 }
            list += Portion(if (label != null) "serving ($label)" else "serving", g)
        }
        quantity?.let { q ->
            // "180 g", "1 x 180g", "6 x 22 g" (multipack: one bag)
            val m = Regex("""^(?:(\d+)\s*[x×]\s*)?(\d+(?:[.,]\d+)?)\s*(g|ml)\b""", RegexOption.IGNORE_CASE).find(q.trim())
            m?.groupValues?.get(2)?.replace(',', '.')?.toDoubleOrNull()?.takeIf { it in 1.0..3000.0 }?.let { g ->
                val multipack = (m.groupValues[1].toIntOrNull() ?: 1) > 1
                if (list.none { kotlin.math.abs(it.grams - g) < 0.5 }) list += Portion(if (multipack) "pack (1 of a multipack)" else "pack", g)
            }
        }
        return list
    }

    private fun JsonObject.text(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull
    private fun JsonObject.number(key: String): Double? =
        (this[key] as? JsonPrimitive)?.let { it.doubleOrNull ?: it.contentOrNull?.replace(',', '.')?.toDoubleOrNull() }
}
