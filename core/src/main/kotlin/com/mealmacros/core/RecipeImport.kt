package com.mealmacros.core

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/** A recipe as published on a web page (schema.org Recipe). */
data class WebRecipe(
    val name: String,
    val servings: Int?,
    val ingredientLines: List<String>,
    /** The site's own nutrition per serving, e.g. "calories" -> "420kcal", if published. */
    val nutrition: Map<String, String> = emptyMap(),
)

enum class AmountUnit(val grams: Double?, val isVolume: Boolean = false) {
    GRAM(1.0), KILOGRAM(1000.0), MILLIGRAM(0.001), OUNCE(28.35), POUND(453.6),
    MILLILITRE(1.0, true), LITRE(1000.0, true), TABLESPOON(15.0, true), TEASPOON(5.0, true),
    CUP(240.0, true), FLUID_OUNCE(28.4, true),
    PINCH(0.4), HANDFUL(30.0), KNOB(10.0),
    /** Counted things: "3 garlic cloves", "1 small onion", "2 slices". Weight comes from the food. */
    COUNT(null),
}

/** One ingredient line split into amount and food, e.g. "1 small onion, finely chopped". */
data class ParsedIngredient(
    val original: String,
    val quantity: Double?,
    val unit: AmountUnit?,
    /** Words used to find the food, e.g. "onion". */
    val foodName: String,
    /** "small" / "medium" / "large", "clove", "slice"... used to pick a household measure. */
    val sizeWord: String? = null,
)

/** An ingredient matched to the food database, with its weight in grams. */
data class ImportedIngredient(
    val parsed: ParsedIngredient,
    val food: Food?,
    val grams: Double,
    /** True when the match or the weight is a guess the user should check. */
    val needsReview: Boolean,
)

data class ImportedRecipe(
    val recipe: WebRecipe,
    val ingredients: List<ImportedIngredient>,
    /** Lines left out, such as "salt and pepper" or "rice, to serve". */
    val skipped: List<String>,
)

object RecipeImport {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    // ---------------------------------------------------------------------------------------
    // Finding the recipe in a web page
    // ---------------------------------------------------------------------------------------

    private val LD_JSON = Regex(
        """<script[^>]*type\s*=\s*["']application/ld\+json["'][^>]*>(.*?)</script>""",
        setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE),
    )

    /** Extracts the schema.org Recipe that recipe sites embed for search engines, if any. */
    fun extractRecipe(html: String): WebRecipe? {
        for (match in LD_JSON.findAll(html)) {
            val element = try {
                json.parseToJsonElement(match.groupValues[1].trim())
            } catch (e: Exception) {
                continue
            }
            val recipe = findRecipe(element) ?: continue
            val lines = when (val ing = recipe["recipeIngredient"] ?: recipe["ingredients"]) {
                is JsonArray -> ing.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
                is JsonPrimitive -> listOfNotNull(ing.contentOrNull)
                else -> emptyList()
            }.map { cleanText(it) }.filter { it.isNotEmpty() }
            if (lines.isEmpty()) continue
            val nutrition = (recipe["nutrition"] as? JsonObject)
                ?.mapNotNull { (k, v) -> (v as? JsonPrimitive)?.contentOrNull?.let { k to it } }
                ?.filter { !it.first.startsWith("@") }
                ?.toMap() ?: emptyMap()
            return WebRecipe(
                name = cleanText(text(recipe["name"]) ?: "Imported recipe"),
                servings = parseServings(recipe["recipeYield"]),
                ingredientLines = lines,
                nutrition = nutrition,
            )
        }
        return null
    }

    private fun findRecipe(e: JsonElement): JsonObject? = when (e) {
        is JsonArray -> e.firstNotNullOfOrNull { findRecipe(it) }
        is JsonObject -> {
            val type = e["@type"]
            val isRecipe = when (type) {
                is JsonPrimitive -> type.contentOrNull == "Recipe"
                is JsonArray -> type.any { (it as? JsonPrimitive)?.contentOrNull == "Recipe" }
                else -> false
            }
            if (isRecipe) e else listOf("@graph", "mainEntity", "mainEntityOfPage").firstNotNullOfOrNull { k ->
                e[k]?.let { findRecipe(it) }
            }
        }
        else -> null
    }

    private fun text(e: JsonElement?): String? = when (e) {
        is JsonPrimitive -> e.contentOrNull
        is JsonArray -> e.firstNotNullOfOrNull { text(it) }
        else -> null
    }

    /** "Serves 4", "4", 4, ["4", "4 servings"], "Makes 12" -> the first number. */
    fun parseServings(e: JsonElement?): Int? {
        val t = when (e) {
            is JsonPrimitive -> e.contentOrNull
            is JsonArray -> e.firstNotNullOfOrNull { (it as? JsonPrimitive)?.contentOrNull }
            else -> null
        } ?: return null
        return Regex("""\d+""").find(t)?.value?.toIntOrNull()?.takeIf { it in 1..100 }
    }

    private val ENTITIES = mapOf(
        "&amp;" to "&", "&quot;" to "\"", "&#39;" to "'", "&apos;" to "'", "&lt;" to "<", "&gt;" to ">",
        "&nbsp;" to " ", "&frac12;" to "½", "&frac14;" to "¼", "&frac34;" to "¾", "&deg;" to "°",
    )

    fun cleanText(s: String): String {
        var t = s.replace(Regex("<[^>]+>"), " ")
        ENTITIES.forEach { (k, v) -> t = t.replace(k, v) }
        t = Regex("&#(\\d+);").replace(t) { it.groupValues[1].toIntOrNull()?.toChar()?.toString() ?: "" }
        return t.replace(Regex("\\s+"), " ").trim()
    }

    // ---------------------------------------------------------------------------------------
    // Parsing ingredient lines
    // ---------------------------------------------------------------------------------------

    private val FRACTIONS = mapOf(
        '½' to 0.5, '¼' to 0.25, '¾' to 0.75, '⅓' to 1.0 / 3, '⅔' to 2.0 / 3, '⅛' to 0.125,
        '⅜' to 0.375, '⅝' to 0.625, '⅞' to 0.875, '⅕' to 0.2,
    )

    private val UNITS: List<Pair<Regex, AmountUnit>> = listOf(
        "kg|kilos?|kilograms?" to AmountUnit.KILOGRAM,
        "mg" to AmountUnit.MILLIGRAM,
        "g|gr|grams?|grammes?" to AmountUnit.GRAM,
        "ml|millilitres?|milliliters?" to AmountUnit.MILLILITRE,
        "l|litres?|liters?|ltr" to AmountUnit.LITRE,
        "fl\\.? ?oz|fluid ounces?" to AmountUnit.FLUID_OUNCE,
        "oz|ounces?" to AmountUnit.OUNCE,
        "lbs?|pounds?" to AmountUnit.POUND,
        "tbsps?|tbs|tablespoons?|tbl" to AmountUnit.TABLESPOON,
        "tsps?|teaspoons?" to AmountUnit.TEASPOON,
        "cups?" to AmountUnit.CUP,
        "pinch(?:es)?" to AmountUnit.PINCH,
        "handfuls?" to AmountUnit.HANDFUL,
        "knobs?" to AmountUnit.KNOB,
    ).map { (p, u) -> Regex("^(?:$p)\\b\\.?\\s*", RegexOption.IGNORE_CASE) to u }

    /** Counted units kept as a size hint: "2 slices bread", "3 cloves garlic". */
    private val COUNT_WORDS = Regex(
        "^(cloves?|slices?|rashers?|sprigs?|sticks?|stalks?|fillets?|breasts?|thighs?|pieces?|bunch(?:es)?)\\b\\s*(?:of\\s+)?",
        RegexOption.IGNORE_CASE,
    )
    private val SIZE = Regex("\\b(small|medium|large|big|extra[- ]large)\\b", RegexOption.IGNORE_CASE)
    private val CONTAINER = Regex("^(?:x\\s+)?(tins?|cans?|packs?|packets?|jars?|bags?|pots?|cartons?|tubs?|bottles?)\\b\\s*(?:of\\s+)?", RegexOption.IGNORE_CASE)
    private val NUMBER = """(\d+(?:[.,]\d+)?)"""
    private val LEADING_QTY = Regex("""^(?:about|approx\.?|around)?\s*$NUMBER(?:\s*(?:-|–|to)\s*$NUMBER)?\s*""", RegexOption.IGNORE_CASE)
    private val MULTIPLIER = Regex("""^x\s*$NUMBER\s*""", RegexOption.IGNORE_CASE)
    /** "320g/11½oz", "75ml/2½fl oz": the imperial alternative after the slash is dropped. */
    private val IMPERIAL_ALT = Regex("""^/\s*\d+(?:\.\d+)?\s*(?:fl\.? ?oz|oz|lb|lbs|pints?|cups?)\b\s*""", RegexOption.IGNORE_CASE)

    private val SKIP_ONLY = Regex(
        "^(?:sea )?salt(?: and (?:freshly )?(?:ground )?(?:black )?pepper)?$|^(?:freshly )?(?:ground )?(?:black )?pepper$|" +
            "^water$|^salt & pepper$|^salt and pepper$",
        RegexOption.IGNORE_CASE,
    )
    private val SERVE_ONLY = Regex("\\b(to serve|to garnish|for serving|for garnish|optional|to taste)\\b", RegexOption.IGNORE_CASE)

    private fun replaceFractions(s: String): String {
        val out = StringBuilder()
        for ((i, c) in s.withIndex()) {
            val f = FRACTIONS[c]
            if (f == null) {
                out.append(c)
                continue
            }
            // "1½" -> "1.5"; a lone "½" -> "0.5"
            val digits = out.takeLastWhile { it.isDigit() }
            if (digits.isNotEmpty()) {
                repeat(digits.length) { out.deleteCharAt(out.length - 1) }
                out.append(digits.toString().toInt() + f)
            } else {
                out.append(f)
            }
            if (i + 1 < s.length && s[i + 1].isLetter()) out.append(' ')
        }
        // "1 1/2" and "1/2"
        return Regex("""(\d+)\s+(\d+)/(\d+)""").replace(out.toString()) {
            (it.groupValues[1].toDouble() + it.groupValues[2].toDouble() / it.groupValues[3].toDouble()).toString()
        }.let { t ->
            Regex("""(?<![\d.])(\d+)/(\d+)(?![\d])""").replace(t) {
                (it.groupValues[1].toDouble() / it.groupValues[2].toDouble()).toString()
            }
        }
    }

    fun parseLine(line: String): ParsedIngredient {
        var rest = replaceFractions(cleanText(line))
        var quantity: Double? = null
        var unit: AmountUnit? = null
        var sizeWord: String? = null

        // "a pinch of salt", "an onion", "thumb-sized piece of ginger"
        Regex("^(?:a|an|one)\\s+", RegexOption.IGNORE_CASE).find(rest)?.let { rest = "1 " + rest.substring(it.range.last + 1) }
        Regex("^thumb[- ]sized\\s+(?:piece\\s+(?:of\\s+)?)?", RegexOption.IGNORE_CASE).find(rest)?.let {
            rest = "1 " + rest.substring(it.range.last + 1)
            sizeWord = "thumb"
        }
        LEADING_QTY.find(rest)?.takeIf { it.value.isNotBlank() }?.let { m ->
            val a = m.groupValues[1].replace(',', '.').toDouble()
            val b = m.groupValues[2].takeIf { it.isNotEmpty() }?.replace(',', '.')?.toDouble()
            quantity = if (b != null) (a + b) / 2 else a // "2-3" -> 2.5
            rest = rest.substring(m.range.last + 1)
        }
        if (quantity != null) {
            // "2 x 400g tins": multiply.
            MULTIPLIER.find(rest)?.let { m ->
                val inner = m.groupValues[1].replace(',', '.').toDouble()
                quantity = quantity!! * inner
                rest = rest.substring(m.range.last + 1)
            }
            for ((regex, u) in UNITS) {
                val m = regex.find(rest) ?: continue
                unit = u
                rest = rest.substring(m.range.last + 1)
                break
            }
            IMPERIAL_ALT.find(rest)?.let { rest = rest.substring(it.range.last + 1) }
            if (unit == null) {
                unit = AmountUnit.COUNT
                SIZE.find(rest)?.takeIf { it.range.first == 0 }?.let { m ->
                    if (sizeWord == null) sizeWord = m.groupValues[1].lowercase()
                    rest = rest.substring(m.range.last + 1).trimStart()
                }
                COUNT_WORDS.find(rest)?.let { m ->
                    sizeWord = m.groupValues[1].lowercase().removeSuffix("es").removeSuffix("s")
                    rest = rest.substring(m.range.last + 1)
                }
            }
            CONTAINER.find(rest)?.let { rest = rest.substring(it.range.last + 1) } // "400g tin cannellini beans"
        }

        var name = rest
        name = name.replace(Regex("\\([^)]*\\)"), " ")             // "(about 520g)"
        name = name.substringBefore(",").substringBefore(";")       // ", finely chopped"
        name = name.replace(Regex("\\b(such as|plus|or|for|to serve|to garnish)\\b.*$", RegexOption.IGNORE_CASE), " ")
        SIZE.find(name)?.let { m ->
            if (sizeWord == null) sizeWord = m.groupValues[1].lowercase().replace("big", "large")
            name = name.removeRange(m.range)
        }
        // "3 garlic cloves" -> size hint "clove"
        Regex("\\b(cloves?|slices?|rashers?|fillets?|breasts?|thighs?|sticks?|stalks?)\\b", RegexOption.IGNORE_CASE)
            .find(name)?.let { m ->
                if (sizeWord == null && unit == AmountUnit.COUNT) {
                    sizeWord = m.groupValues[1].lowercase().removeSuffix("s")
                }
                name = name.removeRange(m.range) // "garlic cloves" -> "garlic", not the spice
            }
        name = name.replace(Regex("^(of|a|an)\\s+", RegexOption.IGNORE_CASE), "")
        name = name.replace(Regex("\\b(freshly|finely|roughly|thinly|fresh|chopped|grated|sliced|crushed|diced|peeled|minced|ready-made|good quality|good-quality|heaped|level|rounded)\\b", RegexOption.IGNORE_CASE), " ")
        name = name.replace(Regex("\\s+"), " ").trim().trim('-', '–', ' ')
        return ParsedIngredient(line, quantity, unit, name, sizeWord)
    }

    /** True for lines that don't need logging, e.g. "salt and pepper" or "rice, to serve". */
    fun isSkippable(p: ParsedIngredient): Boolean {
        if (p.foodName.isBlank()) return true
        if (p.quantity == null && SKIP_ONLY.matches(p.foodName)) return true
        if (p.quantity == null && SERVE_ONLY.containsMatchIn(p.original)) return true
        return SKIP_ONLY.matches(p.foodName) && (p.unit == AmountUnit.PINCH || p.unit == null)
    }

    // ---------------------------------------------------------------------------------------
    // Weights and matching
    // ---------------------------------------------------------------------------------------

    /** Grams per ml for common ingredients measured by volume (default 1). */
    private val DENSITY = listOf(
        "flour" to 0.55, "icing sugar" to 0.55, "sugar" to 0.85, "oats" to 0.4, "rice" to 0.8,
        "oil" to 0.92, "honey" to 1.4, "syrup" to 1.35, "treacle" to 1.4, "butter" to 0.95,
        "salt" to 1.2, "cocoa" to 0.45, "breadcrumbs" to 0.25, "parmesan" to 0.4, "cheese" to 0.45,
        "spice" to 0.5, "paste" to 1.1, "powder" to 0.5, "zest" to 0.4, "chilli flakes" to 0.35,
    )

    private fun density(name: String): Double {
        val n = name.lowercase()
        return DENSITY.firstOrNull { n.contains(it.first) }?.second ?: 1.0
    }

    /** A household measure of [food] matching a spoon/cup unit, e.g. "tbsp" for TABLESPOON. */
    private fun spoonPortion(food: Food, unit: AmountUnit): Portion? {
        val words = when (unit) {
            AmountUnit.TABLESPOON -> listOf("tbsp", "tablespoon")
            AmountUnit.TEASPOON -> listOf("tsp", "teaspoon")
            AmountUnit.CUP -> listOf("cup")
            else -> return null
        }
        return food.portions.firstOrNull { p -> words.any { p.description.lowercase().startsWith(it) } }
    }

    /** A household measure for a counted ingredient: "small onion", "garlic clove", "slice". */
    private fun countPortion(food: Food, sizeWord: String?): Portion? {
        val portions = food.portions
        if (sizeWord != null) {
            portions.firstOrNull { it.description.lowercase().startsWith(sizeWord) }?.let { return it }
            portions.firstOrNull { it.description.lowercase().contains(sizeWord) }?.let { return it }
        }
        return food.defaultPortion()
            ?: portions.firstOrNull { !it.description.lowercase().startsWith("cup") && !it.description.contains("oz") }
    }

    /** Weight in grams of a parsed ingredient, or null if it can't be worked out. */
    fun grams(p: ParsedIngredient, food: Food?): Double? {
        val q = p.quantity ?: return null
        val unit = p.unit ?: return null
        if (unit == AmountUnit.COUNT) {
            food?.let { countPortion(it, p.sizeWord) }?.let { return q * it.grams }
            val text = "${p.sizeWord ?: ""} ${p.foodName}".lowercase()
            val typical = COUNT_GRAMS.firstOrNull { text.contains(it.first) }?.second ?: return null
            val size = when (p.sizeWord) { "small" -> 0.7; "large" -> 1.3; else -> 1.0 }
            return q * typical * size
        }
        if (unit.isVolume) {
            food?.let { spoonPortion(it, unit) }?.let { return q * it.grams }
            return q * unit.grams!! * density(p.foodName)
        }
        return q * unit.grams!!
    }

    private val STOP_WORDS = setOf(
        "and", "with", "the", "into", "in", "of", "boneless", "skinless", "skin", "on", "off", "free", "range",
        "organic", "unwaxed", "large", "small", "medium", "pieces", "piece", "baby", "ripe", "tinned",
    )

    /**
     * Everyday UK recipe ingredients and the food that best represents them as bought (raw,
     * dried or canned), checked in order. Anything else falls back to a database search.
     */
    private val STAPLES: List<Pair<Regex, String>> = listOf(
        "chopped tomatoes|tinned tomatoes|canned tomatoes|plum tomatoes|tin of tomatoes" to "Tomatoes, canned, whole contents",
        "passata|tomato puree|tomato purée|tomato paste" to "Tomato puree",
        "cherry tomato" to "Tomatoes, cherry, raw",
        "tomato" to "Tomatoes, standard, raw",
        "chickpea|chick pea" to "Chickpeas, mature seeds, canned, drained, rinsed in tap water",
        "cannellini" to "Beans, cannellini, canned, re-heated, drained",
        "butter bean" to "Beans, butter, canned, re-heated, drained",
        "kidney bean" to "Beans, red kidney, canned in water, re-heated, drained",
        "black bean" to "Beans, black turtle, mature seeds, canned",
        "red lentil" to "Lentils, red, split, dried, raw",
        "lentil" to "Lentils, green and brown, whole, dried, raw",
        "greek yo" to "Yogurt, Greek style, plain",
        "natural yo|plain yo|yoghurt|yogurt" to "Yogurt, whole milk, plain",
        "double cream" to "Cream, fresh, double, including Jersey cream",
        "single cream" to "Cream, fresh, single",
        "soured cream|sour cream" to "Cream, sour, cultured",
        "coconut milk" to "Coconut milk",
        "whole milk|full fat milk|full-fat milk" to "Milk, whole, pasteurised, average",
        "skimmed milk" to "Milk, skimmed, pasteurised, average",
        "milk" to "Milk, semi-skimmed, pasteurised, average",
        "parmesan|parmigiano|grana padano" to "Cheese, Parmesan, fresh",
        "mozzarella" to "Cheese, Mozzarella, fresh",
        "cheddar" to "Cheese, Cheddar, English",
        "gruyere|gruyère" to "Cheese, Gruyere",
        "feta" to "Cheese, Feta",
        "unsalted butter" to "Butter, unsalted",
        "butter" to "Butter, salted",
        "olive oil" to "Oil, olive",
        "sunflower oil" to "Oil, sunflower",
        "rapeseed oil" to "Oil, rapeseed",
        "vegetable oil|cooking oil|groundnut oil" to "Oil, vegetable, average",
        "self raising|self-raising" to "Flour, wheat, white, self raising",
        "strong .*flour|bread flour" to "Flour, wheat, bread/strong, white",
        "wholemeal flour" to "Flour, wheat, wholemeal",
        "plain flour|flour" to "Flour, wheat, white, plain, soft",
        "icing sugar" to "Sugar, icing",
        "brown sugar|muscovado" to "Sugar, brown",
        "demerara" to "Sugar, Demerara",
        "caster sugar|granulated sugar|white sugar|sugar" to "Sugar, white",
        "honey" to "Honey",
        "egg yolk" to "Eggs, chicken, yolk, raw",
        "egg" to "Eggs, chicken, whole, raw",
        "arborio|risotto rice" to "Rice, white, Italian Arborio risotto, raw",
        "basmati|long grain rice|rice" to "Rice, white, basmati, raw",
        "spaghetti|penne|rigatoni|fusilli|conchiglie|tagliatelle|linguine|pasta|macaroni|farfalle" to "Pasta, white, dried, raw",
        "noodle" to "Noodles, egg, dried, raw",
        "chicken thigh" to "Chicken, dark meat, thigh, meat only, raw",
        "chicken breast|chicken" to "Chicken, breast, skinless, boneless, meat only, raw",
        "lamb mince|minced lamb" to "Lamb, mince, raw",
        "pork mince|minced pork" to "Pork, mince, raw",
        "beef mince|minced beef|mince" to "Beef, mince, raw",
        "streaky bacon" to "Bacon rashers, streaky, raw",
        "bacon|lardons|pancetta" to "Bacon rashers, back, raw",
        "salmon" to "Salmon, farmed, flesh only, raw",
        "prawn" to "Prawns, king, raw",
        "tuna" to "Tuna, canned in brine, drained",
        "spring onion" to "Spring onions, bulbs and tops, raw",
        "onion" to "Onions, raw",
        "garlic" to "Garlic, raw",
        "ginger" to "Ginger, fresh",
        "chilli flakes|chilli powder" to "Chilli powder",
        "chilli" to "Chillies, hot, red, raw",
        "black pepper|white pepper|peppercorn" to "Pepper, black",
        "pepper" to "Peppers, red, raw",
        "mushroom" to "Mushrooms, white, raw",
        "baby spinach" to "Spinach, baby, raw",
        "spinach" to "Spinach, mature, raw",
        "broccoli" to "Broccoli, green, raw",
        "carrot" to "Carrots, old, raw",
        "celery" to "Celery, raw",
        "courgette" to "Courgette, raw",
        "aubergine" to "Aubergine, raw",
        "leek" to "Leeks, raw",
        "sweet potato" to "Sweet potato, raw, flesh only",
        "new potato|salad potato" to "Potatoes, new and salad, flesh only, raw",
        "potato" to "Potatoes, old, raw, flesh only",
        "peas" to "Peas, frozen, raw",
        "sweetcorn" to "Sweetcorn kernels, canned in water, drained",
        "lemon zest|lemon peel" to "Lemon peel",
        "lemon juice" to "Lemon juice, fresh",
        "lime juice" to "Lime juice, fresh",
        "lemon" to "Lemons, whole, without pips",
        "parsley" to "Parsley, fresh",
        "coriander seed|ground coriander" to "Coriander seeds",
        "coriander" to "Coriander leaves, fresh",
        "basil" to "Basil, fresh",
        "thyme" to "Thyme, fresh",
        "wholegrain mustard" to "Mustard, wholegrain",
        "dijon|mustard" to "Mustard, smooth",
        "soy sauce" to "Soy sauce, light and dark varieties",
        "worcestershire" to "Worcestershire sauce",
        "vegetable stock cube" to "Stock cubes, vegetable",
        "beef stock cube" to "Stock cubes, beef",
        "stock cube|stock pot" to "Stock cubes, chicken",
        "stock" to "Stock, chicken, ready made, retail",
        "curry paste|spice paste|tikka paste|balti paste" to "Curry paste",
        "almond" to "Almonds, flaked and ground",
    ).map { (p, name) -> Regex("\\b(?:$p)", RegexOption.IGNORE_CASE) to name }

    /** Exposed for tests: every staple must name a food in the database. */
    internal val stapleFoodNames: List<String> get() = STAPLES.map { it.second }

    private val DRAINED_TIN = Regex("\\b(tins?|cans?)\\b.*\\bdrained\\b", RegexOption.IGNORE_CASE)

    /** Typical weights (g) for counted ingredients whose food has no household measure. */
    private val COUNT_GRAMS = listOf(
        "thumb" to 15.0, "clove" to 5.0, "thigh" to 90.0, "breast" to 150.0, "fillet" to 140.0,
        "rasher" to 25.0, "slice" to 35.0, "sausage" to 60.0, "egg" to 58.0, "bunch" to 25.0, "sprig" to 1.0,
        "stick" to 40.0, "stalk" to 40.0, "baking potato" to 250.0, "potato" to 200.0, "sweet potato" to 200.0,
        "onion" to 150.0, "shallot" to 30.0, "tomato" to 85.0, "pepper" to 160.0, "carrot" to 70.0,
        "courgette" to 200.0, "aubergine" to 250.0, "lemon" to 100.0, "lime" to 65.0, "chilli" to 15.0,
        "avocado" to 150.0, "naan" to 130.0, "tortilla" to 60.0, "leek" to 150.0, "stock cube" to 10.0,
    )

    /**
     * Finds the closest food: one of the user's own foods, then a known staple (checked against
     * [context], the ingredient as written, so "chopped tomatoes" and "chicken thighs" keep the
     * words that decide the match), then the name, then shorter versions of it.
     */
    fun match(name: String, db: FoodDatabase, customFoods: List<Food> = emptyList(), context: String = name): Food? {
        FoodDatabase.rank(customFoods, name).firstOrNull()?.let { return it }
        for (text in listOf(context, name)) {
            STAPLES.firstOrNull { it.first.containsMatchIn(text) }?.let { (_, food) -> db.byName(food)?.let { return it } }
        }
        val words = FoodDatabase.normalize(name).split(' ').filter { it.length > 1 && it !in STOP_WORDS }.take(5)
        if (words.isEmpty()) return null
        val candidates = buildList {
            add(words)
            for (n in words.size - 1 downTo 1) {
                add(words.takeLast(n)) // the noun is usually last: "cannellini beans" -> "beans"
                add(words.take(n))
            }
        }.distinct()
        for (query in candidates) {
            val q = query.joinToString(" ")
            val hit = (FoodDatabase.rank(customFoods, q) + db.search(q, 20))
                .firstOrNull { !it.isRestaurant && it.weightKnown }
            if (hit != null) return hit
        }
        return null
    }

    fun import(recipe: WebRecipe, db: FoodDatabase, customFoods: List<Food> = emptyList()): ImportedRecipe {
        val ingredients = ArrayList<ImportedIngredient>()
        val skipped = ArrayList<String>()
        for (line in recipe.ingredientLines) {
            val parsed = parseLine(line)
            if (isSkippable(parsed)) {
                skipped += line
                continue
            }
            // The ingredient as written, without preparation notes: "6 chicken thighs boneless and skinless"
            val context = line.substringBefore(",").replace(Regex("\\([^)]*\\)"), " ")
            val food = match(parsed.foodName, db, customFoods, context)
            var grams = grams(parsed, food)
            // "400g tin beans, drained": the tin weight includes the liquid; drained is about 60%.
            val drainedTin = DRAINED_TIN.containsMatchIn(line) && parsed.unit?.isVolume == false
            if (drainedTin && grams != null) grams *= 0.6
            val wordsMatched = food != null && FoodDatabase.normalize(parsed.foodName).split(' ')
                .filter { it.length > 2 }
                .all { w -> FoodDatabase.normalize(food.name + " " + food.commonName).contains(w.removeSuffix("s")) }
            ingredients += ImportedIngredient(
                parsed = parsed,
                food = food,
                grams = grams ?: 0.0,
                needsReview = food == null || grams == null || !wordsMatched || parsed.unit == AmountUnit.COUNT ||
                    drainedTin,
            )
        }
        return ImportedRecipe(recipe, ingredients, skipped)
    }
}
