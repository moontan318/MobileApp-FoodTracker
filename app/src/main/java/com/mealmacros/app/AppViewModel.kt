package com.mealmacros.app

import android.app.Application
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mealmacros.app.data.JsonStore
import com.mealmacros.app.data.UserData
import com.mealmacros.app.data.WebPage
import com.mealmacros.core.DiaryEntry
import com.mealmacros.core.Food
import com.mealmacros.core.FoodDatabase
import com.mealmacros.core.Ingredient
import com.mealmacros.core.NutritionCalculator
import com.mealmacros.core.Nutrient
import com.mealmacros.core.ImportedRecipe
import com.mealmacros.core.NutrientProfile
import com.mealmacros.core.Recipe
import com.mealmacros.core.RecipeImport
import com.mealmacros.core.StatsRange
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.UUID

enum class HomeTab { RECIPES, DIARY, DASHBOARD, FOODS }

/** Where a food picked in the search screen goes. */
enum class SearchTarget { RECIPE, DIARY }

sealed interface Screen {
    data object Home : Screen
    data class RecipeDetail(val recipeId: String) : Screen
    data object RecipeEditor : Screen
    data class FoodSearch(val target: SearchTarget) : Screen
    data class CustomFoodEditor(val foodId: String?) : Screen
    data class RecipeImport(val url: String) : Screen
}

/** Progress of importing a recipe from a web page. */
data class ImportState(val loading: Boolean = false, val error: String? = null)

/**
 * One ingredient row being edited; [key] keeps row state stable when rows are removed.
 * [needsReview] marks imported ingredients whose match or weight should be checked.
 */
data class DraftItem(val key: Long, val ingredient: Ingredient, val needsReview: Boolean = false)

data class RecipeDraft(
    val recipeId: String?,
    val name: String = "",
    val items: List<DraftItem> = emptyList(),
    val cookedWeightText: String = "",
    val notes: String = "",
    val servingsText: String = "",
    val sourceUrl: String? = null,
    /** Shown above an imported recipe, e.g. the site's own calories per serving. */
    val importSummary: String? = null,
) {
    val cookedWeight: Double? get() = parseNumber(cookedWeightText)?.takeIf { it > 0 }
    val servings: Int? get() = servingsText.trim().toIntOrNull()?.takeIf { it > 0 }

    fun toRecipe(id: String, updatedAt: Long) = Recipe(
        id = id,
        name = name.trim(),
        ingredients = items.map { it.ingredient },
        cookedWeight = cookedWeight,
        notes = notes.trim(),
        updatedAt = updatedAt,
        servings = servings,
        sourceUrl = sourceUrl,
    )
}

/** An amount of a food chosen by the user; [label] is set for household measures ("1 × medium"). */
data class Amount(
    val grams: Double,
    val label: String? = null,
    val quantity: Double? = null,
    val portion: String? = null,
    /** When the food was eaten (on the diary's day); null = now / midday. */
    val time: LocalTime? = null,
)

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

    /** Day shown in the diary; new diary entries are logged to this day. */
    var diaryDay by mutableStateOf(LocalDate.now().toEpochDay())

    // Dashboard selections
    var statsRange by mutableStateOf(StatsRange.WEEK)
    var statsAnchor by mutableStateOf(LocalDate.now().toEpochDay())
    var statsNutrients by mutableStateOf(listOf(Nutrient.ENERGY, Nutrient.PROTEIN))
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
                val assets = application.assets
                assets.open("foods.tsv").reader().use { foods ->
                    assets.open("portions.tsv").reader().use { portions -> FoodDatabase.parse(foods, portions) }
                }
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
                servingsText = recipe.servings?.toString() ?: "",
                sourceUrl = recipe.sourceUrl,
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

    /** Row whose food is being replaced from the search screen (null = adding a new row). */
    var replacingKey: Long? = null
        private set

    fun startReplaceIngredient(key: Long) {
        replacingKey = key
        navigate(Screen.FoodSearch(SearchTarget.RECIPE))
    }

    /** Current weight of the row being replaced, to pre-fill the amount. */
    fun replacingGrams(): Double? = replacingKey?.let { key -> draft?.items?.firstOrNull { it.key == key }?.ingredient?.grams }

    fun startAddIngredient() {
        replacingKey = null
        navigate(Screen.FoodSearch(SearchTarget.RECIPE))
    }

    fun addIngredient(food: Food, amount: Amount) {
        val key = replacingKey
        replacingKey = null
        updateDraft { d ->
            if (key != null && d.items.any { it.key == key }) {
                d.copy(items = d.items.map { item ->
                    if (item.key != key) item else DraftItem(
                        key, Ingredient.of(food, amount.grams).copy(sourceText = item.ingredient.sourceText), needsReview = false,
                    )
                })
            } else {
                d.copy(items = d.items + DraftItem(nextDraftKey++, Ingredient.of(food, amount.grams)))
            }
        }
    }

    // --- Importing recipes from the web --------------------------------------------------------

    var importState by mutableStateOf(ImportState())
        private set

    /** Downloads a recipe page, matches its ingredients and opens the result for review. */
    fun importRecipe(url: String) {
        val link = url.trim().let { if (it.startsWith("http")) it else "https://$it" }
        importState = ImportState(loading = true)
        viewModelScope.launch {
            try {
                val html = withContext(Dispatchers.IO) { WebPage.download(link) }
                val web = RecipeImport.extractRecipe(html) ?: run {
                    importState = ImportState(
                        error = "No recipe found on that page. It works with sites that publish recipe details for " +
                            "search engines, such as BBC Food and BBC Good Food.",
                    )
                    return@launch
                }
                val db = snapshotFlow { foodDatabase }.filterNotNull().first()
                val imported = withContext(Dispatchers.Default) { RecipeImport.import(web, db, customFoods) }
                importState = ImportState()
                back() // leave the import screen
                openDraft(importedDraft(imported, link))
            } catch (e: Exception) {
                Log.w(TAG, "Recipe import failed", e)
                importState = ImportState(error = "Couldn't download the page: ${e.message ?: e.javaClass.simpleName}")
            }
        }
    }

    fun clearImportError() {
        importState = ImportState()
    }

    private fun importedDraft(imported: ImportedRecipe, url: String): RecipeDraft {
        val items = imported.ingredients.map { ing ->
            val ingredient = ing.food?.let { Ingredient.of(it, ing.grams) }
                ?: Ingredient(UNMATCHED_FOOD_ID, ing.parsed.foodName.ifBlank { ing.parsed.original }, ing.grams, NutrientProfile.EMPTY)
            DraftItem(nextDraftKey++, ingredient.copy(sourceText = ing.parsed.original), ing.needsReview)
        }
        val web = imported.recipe
        val site = web.nutrition["calories"]?.let { kcal ->
            val servings = web.servings?.let { " per serving (serves $it)" } ?: " per serving"
            "The recipe says $kcal$servings."
        }
        val notes = buildList {
            add("Imported from $url")
            if (imported.skipped.isNotEmpty()) add("Not included: " + imported.skipped.joinToString("; "))
        }.joinToString("\n")
        return RecipeDraft(
            recipeId = null,
            name = web.name,
            items = items,
            notes = notes,
            servingsText = web.servings?.toString() ?: "",
            sourceUrl = url,
            importSummary = listOfNotNull(
                "Check each ingredient against what you actually used: tap a name to pick a different food " +
                    "and change any weight. Items marked ⚠ are best guesses.",
                site,
            ).joinToString(" "),
        )
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

    fun logPortion(recipe: Recipe, grams: Double, time: LocalTime? = null) {
        val portion = NutritionCalculator.portion(recipe, grams)
        diary = diary + DiaryEntry(
            id = UUID.randomUUID().toString(),
            name = recipe.name,
            grams = grams,
            timestamp = diaryTimestamp(time),
            nutrients = portion.nutrients,
            recipeId = recipe.id,
        )
        persist()
    }

    fun logFood(food: Food, amount: Amount) {
        diary = diary + DiaryEntry(
            id = UUID.randomUUID().toString(),
            name = food.name,
            grams = if (food.weightKnown) amount.grams else 0.0, // 0 = weight not published
            timestamp = diaryTimestamp(amount.time),
            nutrients = food.nutrientsFor(amount.grams),
            foodId = food.id,
            amountLabel = amount.label,
            quantity = amount.quantity,
            portion = amount.portion,
        )
        persist()
    }

    /** [time] on the diary's day; without one, now if that's today, otherwise midday. */
    private fun diaryTimestamp(time: LocalTime? = null): Long {
        val day = LocalDate.ofEpochDay(diaryDay)
        if (time == null && day == LocalDate.now()) return System.currentTimeMillis()
        return day.atTime(time ?: LocalTime.NOON).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    }

    /** Suggested time for a new entry: now when the diary shows today, otherwise midday. */
    fun defaultLogTime(): LocalTime =
        if (LocalDate.ofEpochDay(diaryDay) == LocalDate.now()) LocalTime.now().withSecond(0).withNano(0) else LocalTime.NOON

    /** Replaces entries (matched by id) with edited versions. */
    fun updateDiaryEntries(updated: List<DiaryEntry>) {
        val byId = updated.associateBy { it.id }
        diary = diary.map { byId[it.id] ?: it }
        persist()
    }

    fun deleteDiaryEntries(ids: Set<String>) {
        diary = diary.filterNot { it.id in ids }
        persist()
    }

    fun diaryDayLabel(): String {
        val day = LocalDate.ofEpochDay(diaryDay)
        return when (day) {
            LocalDate.now() -> "today"
            LocalDate.now().minusDays(1) -> "yesterday"
            else -> day.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))
        }
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
        const val UNMATCHED_FOOD_ID = "unmatched"
    }
}

/** Formats a number without a trailing ".0" (for pre-filling text fields). */
fun formatPlain(value: Double): String =
    if (value == Math.floor(value) && !value.isInfinite()) value.toLong().toString() else value.toString()
