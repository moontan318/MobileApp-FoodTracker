package com.mealmacros.core

import java.io.BufferedReader
import java.io.Reader

/**
 * The bundled food database plus search. Built by tools/build_food_db.py from UK CoFID,
 * USDA SR28 and restaurant menus.
 *
 * Food file: tab separated with a header row of `id, name, group, source, common, per`
 * followed by nutrient keys; values are per 100 g (or per item when `per` is "item", see
 * [Food.weightKnown]), empty = unknown.
 * Portion file: tab separated `id, description, grams` (grams for one unit).
 */
class FoodDatabase(val foods: List<Food>) {

    private val searchText: List<String> = foods.map { normalize("${it.name} ${it.commonName}") }
    private val byId: Map<String, Food> = foods.associateBy { it.id }

    fun get(id: String): Food? = byId[id]

    /** Foods whose name contains every word of [query], best matches first. */
    fun search(query: String, limit: Int = 60): List<Food> =
        rank(foods, searchText, query, limit)

    companion object {
        /** Source of the UK government's CoFID foods, ranked first in search. */
        const val SOURCE_UK = "UK"

        fun parse(reader: Reader, portionsReader: Reader? = null): FoodDatabase {
            val portions = portionsReader?.let(::parsePortions) ?: emptyMap()
            val lines = BufferedReader(reader).lineSequence().iterator()
            if (!lines.hasNext()) return FoodDatabase(emptyList())
            val header = lines.next().split('\t')
            fun column(name: String) = header.indexOf(name)
            val idCol = column("id")
            val nameCol = column("name")
            val groupCol = column("group")
            val sourceCol = column("source")
            val commonCol = column("common")
            val perCol = column("per")
            val nutrientCols = header.mapIndexedNotNull { i, key -> Nutrient.fromKey(key)?.let { i to it } }
            val foods = ArrayList<Food>(10_500)
            for (line in lines) {
                if (line.isBlank()) continue
                val f = line.split('\t')
                val values = HashMap<Nutrient, Double>()
                for ((i, nutrient) in nutrientCols) {
                    f.getOrNull(i)?.toDoubleOrNull()?.let { values[nutrient] = it }
                }
                val id = f[idCol]
                foods += Food(
                    id = id,
                    name = f[nameCol],
                    group = f.getOrElse(groupCol) { "" },
                    source = if (sourceCol >= 0) f.getOrElse(sourceCol) { "" } else "",
                    commonName = if (commonCol >= 0) f.getOrElse(commonCol) { "" } else "",
                    per100g = NutrientProfile(values),
                    portions = portions[id] ?: emptyList(),
                    weightKnown = perCol < 0 || f.getOrNull(perCol) != "item",
                )
            }
            return FoodDatabase(foods)
        }

        private fun parsePortions(reader: Reader): Map<String, List<Portion>> {
            val result = HashMap<String, MutableList<Portion>>()
            BufferedReader(reader).lineSequence().drop(1).forEach { line ->
                val f = line.split('\t')
                val grams = f.getOrNull(2)?.toDoubleOrNull() ?: return@forEach
                if (grams > 0) result.getOrPut(f[0]) { ArrayList() } += Portion(f[1], grams)
            }
            return result
        }

        fun normalize(s: String): String = s.lowercase().replace(Regex("[^a-z0-9%]+"), " ").trim()

        /** Search helper shared with custom foods. */
        fun rank(foods: List<Food>, query: String, limit: Int = 60): List<Food> =
            rank(foods, foods.map { normalize("${it.name} ${it.commonName}") }, query, limit)

        private fun rank(foods: List<Food>, texts: List<String>, query: String, limit: Int): List<Food> {
            val tokens = normalize(query).split(' ').filter { it.isNotEmpty() }
            if (tokens.isEmpty()) return emptyList()
            val scored = ArrayList<Pair<Food, Double>>()
            for (i in foods.indices) {
                val score = score(texts[i], tokens) ?: continue
                scored += foods[i] to score + sourceBonus(foods[i])
            }
            return scored.sortedWith(compareByDescending<Pair<Food, Double>> { it.second }.thenBy { it.first.name })
                .take(limit)
                .map { it.first }
        }

        /** Returns null when some token doesn't match. */
        private fun score(text: String, tokens: List<String>): Double? {
            var score = 0.0
            for ((index, token) in tokens.withIndex()) {
                val variants = variants(token)
                val pos = variants.map { text.indexOf(it) }.filter { it >= 0 }.minOrNull() ?: return null
                val wordStart = pos == 0 || text[pos - 1] == ' '
                if (wordStart) score += 3.0
                val wholeWord = variants.any { v ->
                    listOf(v, v + "s", v + "es").any { text.startsWith(it, pos) && wordEndsAt(text, pos + it.length) }
                }
                if (wholeWord) score += 2.0
                if (pos == 0) score += if (index == 0) 10.0 else 2.0
                score -= pos / 40.0
            }
            // Prefer short, plain descriptions and raw ingredients.
            score -= text.length / 25.0
            if (" raw" in text) score += 1.5
            return score
        }

        private fun sourceBonus(food: Food): Double = when (food.source) {
            SOURCE_UK -> 2.5
            "US" -> 0.0
            else -> 1.0 // restaurants
        }

        private fun wordEndsAt(text: String, end: Int) = end >= text.length || text[end] == ' '

        private fun variants(token: String): List<String> {
            val list = mutableListOf(token)
            if (token.length > 4 && token.endsWith("es")) list += token.dropLast(2)
            if (token.length > 3 && token.endsWith("s")) list += token.dropLast(1)
            for (t in list.toList()) SYNONYMS[t]?.let { list += it }
            return list.distinct()
        }

        /**
         * Words that mean the same food. Names in the database are mostly British English;
         * these let American (or alternative) words find them and vice versa.
         */
        private val SYNONYM_GROUPS = listOf(
            listOf("aubergine", "eggplant"),
            listOf("courgette", "zucchini"),
            listOf("coriander", "cilantro"),
            listOf("rocket", "arugula"),
            listOf("prawn", "shrimp"),
            listOf("swede", "rutabaga"),
            listOf("mangetout", "mange tout", "snow pea"),
            listOf("chickpea", "garbanzo"),
            listOf("yoghurt", "yogurt"),
            listOf("mince", "minced", "ground"),
            listOf("biscuit", "cookie"),
            listOf("sweets", "candy", "candies"),
            listOf("chips", "fries"),
            listOf("scallion", "spring onion"),
            listOf("ketchup", "catsup"),
            listOf("fibre", "fiber"),
            listOf("flavour", "flavor"),
            listOf("beetroot", "beet"),
            listOf("pepper", "capsicum"),
            listOf("cornflour", "cornstarch"),
            listOf("porridge", "oatmeal"),
        )

        private val SYNONYMS: Map<String, List<String>> = buildMap {
            for (group in SYNONYM_GROUPS) for (word in group) put(word, group - word)
        }
    }
}
