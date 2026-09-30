package com.mealmacros.app.data

import android.util.AtomicFile
import com.mealmacros.core.DiaryEntry
import com.mealmacros.core.Food
import com.mealmacros.core.Ingredient
import com.mealmacros.core.Nutrient
import com.mealmacros.core.NutrientProfile
import com.mealmacros.core.Recipe
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Everything the user has created. */
data class UserData(
    val recipes: List<Recipe> = emptyList(),
    val customFoods: List<Food> = emptyList(),
    val diary: List<DiaryEntry> = emptyList(),
)

/** Persists [UserData] as a single JSON file in app-private storage. */
class JsonStore(file: File) {

    private val atomicFile = AtomicFile(file)

    fun load(): UserData {
        val text = try {
            atomicFile.readFully().toString(Charsets.UTF_8)
        } catch (e: java.io.FileNotFoundException) {
            return UserData()
        }
        return decode(JSONObject(text))
    }

    @Synchronized
    fun save(data: UserData) {
        val stream = atomicFile.startWrite()
        try {
            stream.write(encode(data).toString().toByteArray(Charsets.UTF_8))
            atomicFile.finishWrite(stream)
        } catch (e: Exception) {
            atomicFile.failWrite(stream)
            throw e
        }
    }

    companion object {
        private const val VERSION = 1

        fun encode(data: UserData): JSONObject = JSONObject()
            .put("version", VERSION)
            .put("recipes", JSONArray(data.recipes.map(::encodeRecipe)))
            .put("customFoods", JSONArray(data.customFoods.map(::encodeFood)))
            .put("diary", JSONArray(data.diary.map(::encodeEntry)))

        fun decode(json: JSONObject): UserData = UserData(
            recipes = json.optJSONArray("recipes").objects().map(::decodeRecipe),
            customFoods = json.optJSONArray("customFoods").objects().map(::decodeFood),
            diary = json.optJSONArray("diary").objects().map(::decodeEntry),
        )

        private fun encodeRecipe(r: Recipe) = JSONObject()
            .put("id", r.id)
            .put("name", r.name)
            .put("cookedWeight", r.cookedWeight ?: JSONObject.NULL)
            .put("notes", r.notes)
            .put("updatedAt", r.updatedAt)
            .put("ingredients", JSONArray(r.ingredients.map {
                JSONObject()
                    .put("foodId", it.foodId)
                    .put("name", it.name)
                    .put("grams", it.grams)
                    .put("per100g", encodeProfile(it.per100g))
            }))

        private fun decodeRecipe(o: JSONObject) = Recipe(
            id = o.getString("id"),
            name = o.getString("name"),
            cookedWeight = if (o.isNull("cookedWeight")) null else o.getDouble("cookedWeight"),
            notes = o.optString("notes"),
            updatedAt = o.optLong("updatedAt"),
            ingredients = o.optJSONArray("ingredients").objects().map {
                Ingredient(
                    foodId = it.getString("foodId"),
                    name = it.getString("name"),
                    grams = it.getDouble("grams"),
                    per100g = decodeProfile(it.optJSONObject("per100g")),
                )
            },
        )

        private fun encodeFood(f: Food) = JSONObject()
            .put("id", f.id)
            .put("name", f.name)
            .put("group", f.group)
            .put("per100g", encodeProfile(f.per100g))

        private fun decodeFood(o: JSONObject) = Food(
            id = o.getString("id"),
            name = o.getString("name"),
            group = o.optString("group"),
            per100g = decodeProfile(o.optJSONObject("per100g")),
            isCustom = true,
        )

        private fun encodeEntry(e: DiaryEntry) = JSONObject()
            .put("id", e.id)
            .put("name", e.name)
            .put("grams", e.grams)
            .put("timestamp", e.timestamp)
            .put("recipeId", e.recipeId ?: JSONObject.NULL)
            .put("nutrients", encodeProfile(e.nutrients))

        private fun decodeEntry(o: JSONObject) = DiaryEntry(
            id = o.getString("id"),
            name = o.getString("name"),
            grams = o.getDouble("grams"),
            timestamp = o.getLong("timestamp"),
            recipeId = if (o.isNull("recipeId")) null else o.getString("recipeId"),
            nutrients = decodeProfile(o.optJSONObject("nutrients")),
        )

        private fun encodeProfile(p: NutrientProfile): JSONObject {
            val values = JSONObject()
            p.values.forEach { (n, v) -> values.put(n.key, v) }
            return JSONObject()
                .put("values", values)
                .put("incomplete", JSONArray(p.incomplete.map { it.key }))
        }

        private fun decodeProfile(o: JSONObject?): NutrientProfile {
            if (o == null) return NutrientProfile.EMPTY
            val values = HashMap<Nutrient, Double>()
            o.optJSONObject("values")?.let { v ->
                v.keys().forEach { key -> Nutrient.fromKey(key)?.let { values[it] = v.getDouble(key) } }
            }
            val incomplete = o.optJSONArray("incomplete").strings().mapNotNull { Nutrient.fromKey(it) }.toSet()
            return NutrientProfile(values, incomplete)
        }

        private fun JSONArray?.objects(): List<JSONObject> =
            if (this == null) emptyList() else (0 until length()).map { getJSONObject(it) }

        private fun JSONArray?.strings(): List<String> =
            if (this == null) emptyList() else (0 until length()).map { getString(it) }
    }
}
