package com.mealmacros.core

import java.io.BufferedReader
import java.io.Reader

/**
 * The bundled food composition table (USDA SR28) plus search.
 *
 * File format: tab separated, first row is a header of
 * `id, name, group, common, <nutrient keys...>`; values are per 100 g, empty = unknown.
 */
class FoodDatabase(val foods: List<Food>) {

    private val searchText: List<String> = foods.map { normalize("${it.name} ${it.commonName}") }
    private val byId: Map<String, Food> = foods.associateBy { it.id }

    fun get(id: String): Food? = byId[id]

    /** Foods whose name contains every word of [query], best matches first. */
    fun search(query: String, limit: Int = 60): List<Food> =
        rank(foods, searchText, query, limit)

    companion object {
        const val USDA_PREFIX = "usda:"

        fun parse(reader: Reader): FoodDatabase {
            val lines = BufferedReader(reader).lineSequence().iterator()
            if (!lines.hasNext()) return FoodDatabase(emptyList())
            val header = lines.next().split('\t')
            val columns = header.drop(4).map { Nutrient.fromKey(it) }
            val foods = ArrayList<Food>(9000)
            for (line in lines) {
                if (line.isBlank()) continue
                val f = line.split('\t')
                val values = HashMap<Nutrient, Double>()
                columns.forEachIndexed { i, nutrient ->
                    val raw = f.getOrNull(i + 4)
                    val v = raw?.toDoubleOrNull()
                    if (nutrient != null && v != null) values[nutrient] = v
                }
                foods += Food(
                    id = USDA_PREFIX + f[0],
                    name = f[1],
                    group = f.getOrElse(2) { "" },
                    commonName = f.getOrElse(3) { "" },
                    per100g = NutrientProfile(values),
                )
            }
            return FoodDatabase(foods)
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
                scored += foods[i] to score
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
                if (variants.any { text.startsWith(it, pos) && wordEndsAt(text, pos + it.length) }) score += 2.0
                if (pos == 0) score += if (index == 0) 10.0 else 2.0
                score -= pos / 40.0
            }
            // Prefer short, plain descriptions and raw ingredients.
            score -= text.length / 25.0
            if (" raw" in text) score += 1.5
            return score
        }

        private fun wordEndsAt(text: String, end: Int) = end >= text.length || text[end] == ' '

        private fun variants(token: String): List<String> {
            val list = mutableListOf(token)
            if (token.length > 4 && token.endsWith("es")) list += token.dropLast(2)
            if (token.length > 3 && token.endsWith("s")) list += token.dropLast(1)
            SYNONYMS[token]?.let { list += it }
            return list
        }

        /** British/common names mapped to the (American) wording used by USDA. */
        private val SYNONYMS = mapOf(
            "mince" to "ground", "minced" to "ground",
            "courgette" to "zucchini", "courgettes" to "zucchini",
            "aubergine" to "eggplant", "aubergines" to "eggplant",
            "coriander" to "cilantro", "rocket" to "arugula",
            "prawn" to "shrimp", "prawns" to "shrimp",
            "swede" to "rutabaga", "mangetout" to "snow",
            "chickpea" to "chickpeas", "garbanzo" to "chickpeas",
            "yoghurt" to "yogurt", "porridge" to "oats",
            "crisps" to "chips", "gammon" to "ham",
            "fibre" to "fiber", "flavour" to "flavor",
            "beetroot" to "beets", "capsicum" to "peppers",
        )
    }
}
