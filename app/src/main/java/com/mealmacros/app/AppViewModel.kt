package com.mealmacros.app

import android.app.Application
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mealmacros.app.data.JsonStore
import com.mealmacros.app.data.UserData
import com.mealmacros.core.DiaryEntry
import com.mealmacros.core.Food
import com.mealmacros.core.FoodDatabase
import com.mealmacros.core.Ingredient
import com.mealmacros.core.NutritionCalculator
import com.mealmacros.core.Recipe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

enum class HomeTab { RECIPES, DIARY, FOODS }

sealed interface Screen {
    data object Home : Screen
    data class RecipeDetail(val recipeId: String) : Screen
    data object RecipeEditor : Screen
    data object FoodSearch : Screen
    data class CustomFoodEditor(val foodId: String?) : Screen
}

/** One ingredient row being edited; [key] keeps row state stable when rows are removed. */
data class DraftItem(val key: Long, val ingredient: Ingredient)

data class RecipeDraft(
    val recipeId: String?,
    val name: String = "",
    val items: List<DraftItem> = emptyList(),
    val cookedWeightText: String = "",
    val notes: String = "",
) {
    val cookedWeight: Double? get() = parseNumber(cookedWeightText)?.takeIf { it > 0 }

    fun toRecipe(id: String, updatedAt: Long) = Recipe(
        id = id,
        name = name.trim(),
        ingredients = items.map { it.ingredient },
        cookedWeight = cookedWeight,
        notes = notes.trim(),
        updatedAt = updatedAt,
    )
}

fun parseNumber(text: String): Double? = text.trim().replace(',', '.').toDoubleOrNull()

class AppViewModel(application: Application) : AndroidViewModel(application) {

    private val store = JsonStore(File(application.filesDir, "meal_macros.json"))

    var foodDatabase by mutableStateOf<FoodDatabase?>(null)
        private set
    var loaded by mutableStateOf(false)
        private set
    var recipes by mutableStateOf(emptyList<Recipe>())
        private set
    var customFoods by mutableStateOf(emptyList<Food>())
        private set
    var diary by mutableStateOf(emptyList<DiaryEntry>())
        private set

    var tab by mutableStateOf(HomeTab.RECIPES)
    val backStack = mutableStateListOf<Screen>(Screen.Home)
    val screen: Screen get() = backStack.last()

    var draft by mutableStateOf<RecipeDraft?>(null)
    private var draftOriginal: RecipeDraft? = null
    private var nextDraftKey = 0L

    // Saves are conflated and written one at a time, so the newest data always wins.
    private val pendingSave = MutableStateFlow<UserData?>(null)

    init {
        viewModelScope.launch(Dispatchers.IO) {
            pendingSave.filterNotNull().collect { data ->
                try {
                    store.save(data)
                } catch (e: Exception) {
                    Log.e(TAG, "Could not save data", e)
                }
            }
        }
        viewModelScope.launch {
            val data = withContext(Dispatchers.IO) {
                try {
                    store.load()
                } catch (e: Exception) {
                    Log.e(TAG, "Could not read saved data", e)
                    UserData()
                }
            }
            recipes = data.recipes
            customFoods = data.customFoods
            diary = data.diary
            loaded = true
        }
        viewModelScope.launch {
            foodDatabase = withContext(Dispatchers.IO) {
                application.assets.open("usda_sr28.tsv").reader().use { FoodDatabase.parse(it) }
            }
        }
    }

    // --- Navigation -------------------------------------------------------------------------

    fun navigate(screen: Screen) {
        backStack.add(screen)
    }

    fun back(): Boolean {
        if (backStack.size <= 1) return false
        backStack.removeAt(backStack.lastIndex)
        return true
    }

    // --- Recipes ----------------------------------------------------------------------------

    fun recipe(id: String): Recipe? = recipes.firstOrNull { it.id == id }

    fun startNewRecipe() {
        openDraft(RecipeDraft(recipeId = null))
    }

    fun startEditRecipe(recipe: Recipe) {
        openDraft(
            RecipeDraft(
                recipeId = recipe.id,
                name = recipe.name,
                items = recipe.ingredients.map { DraftItem(nextDraftKey++, it) },
                cookedWeightText = recipe.cookedWeight?.let(::formatPlain) ?: "",
                notes = recipe.notes,
            )
        )
    }

    private fun openDraft(d: RecipeDraft) {
        draft = d
        draftOriginal = d
        navigate(Screen.RecipeEditor)
    }

    val draftIsDirty: Boolean get() = draft != draftOriginal

    fun updateDraft(transform: (RecipeDraft) -> RecipeDraft) {
        draft = draft?.let(transform)
    }

    fun addIngredient(food: Food, grams: Double) {
        updateDraft { it.copy(items = it.items + DraftItem(nextDraftKey++, Ingredient.of(food, grams))) }
    }

    /** Saves the draft and returns the saved recipe id. */
    fun saveDraft(): String? {
        val d = draft ?: return null
        val id = d.recipeId ?: UUID.randomUUID().toString()
        val recipe = d.toRecipe(id, System.currentTimeMillis())
        recipes = if (recipes.any { it.id == id }) {
            recipes.map { if (it.id == id) recipe else it }
        } else {
            recipes + recipe
        }
        persist()
        draft = null
        draftOriginal = null
        return id
    }

    fun discardDraft() {
        draft = null
        draftOriginal = null
    }

    fun deleteRecipe(id: String) {
        recipes = recipes.filterNot { it.id == id }
        persist()
    }

    fun duplicateRecipe(recipe: Recipe): String {
        val copy = recipe.copy(
            id = UUID.randomUUID().toString(),
            name = "${recipe.name} (copy)",
            updatedAt = System.currentTimeMillis(),
        )
        recipes = recipes + copy
        persist()
        return copy.id
    }

    // --- Custom foods -----------------------------------------------------------------------

    fun customFood(id: String): Food? = customFoods.firstOrNull { it.id == id }

    fun saveCustomFood(food: Food): Food {
        val saved = if (food.id.isEmpty()) food.copy(id = "custom:" + UUID.randomUUID()) else food
        customFoods = if (customFoods.any { it.id == saved.id }) {
            customFoods.map { if (it.id == saved.id) saved else it }
        } else {
            customFoods + saved
        }
        persist()
        return saved
    }

    fun deleteCustomFood(id: String) {
        customFoods = customFoods.filterNot { it.id == id }
        persist()
    }

    // --- Diary ------------------------------------------------------------------------------

    fun logPortion(recipe: Recipe, grams: Double, timestamp: Long = System.currentTimeMillis()) {
        val portion = NutritionCalculator.portion(recipe, grams)
        diary = diary + DiaryEntry(
            id = UUID.randomUUID().toString(),
            name = recipe.name,
            grams = grams,
            timestamp = timestamp,
            nutrients = portion.nutrients,
            recipeId = recipe.id,
        )
        persist()
    }

    fun deleteDiaryEntry(id: String) {
        diary = diary.filterNot { it.id == id }
        persist()
    }

    private fun persist() {
        pendingSave.value = UserData(recipes, customFoods, diary)
    }

    companion object {
        private const val TAG = "MealMacros"
    }
}

/** Formats a number without a trailing ".0" (for pre-filling text fields). */
fun formatPlain(value: Double): String =
    if (value == Math.floor(value) && !value.isInfinite()) value.toLong().toString() else value.toString()
