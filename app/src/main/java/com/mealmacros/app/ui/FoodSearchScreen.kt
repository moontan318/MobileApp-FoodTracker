package com.mealmacros.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import com.mealmacros.app.AppViewModel
import com.mealmacros.app.Screen
import com.mealmacros.app.SearchTarget
import com.mealmacros.core.Food
import com.mealmacros.core.FoodDatabase
import com.mealmacros.core.NutritionCalculator
import com.mealmacros.core.Recipe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FoodSearchScreen(vm: AppViewModel, target: SearchTarget) {
    val forDiary = target == SearchTarget.DIARY
    var query by rememberSaveable { mutableStateOf("") }
    var results by remember { mutableStateOf(emptyList<Food>()) }
    var selected by remember { mutableStateOf<Food?>(null) }
    val db = vm.foodDatabase
    val customFoods = vm.customFoods
    val focusRequester = remember { FocusRequester() }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(query, db, customFoods) {
        delay(150) // debounce typing
        results = withContext(Dispatchers.Default) {
            FoodDatabase.rank(customFoods, query) + (db?.search(query) ?: emptyList())
        }
    }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    val matchingRecipes = if (forDiary) matchRecipes(vm.recipes, query) else emptyList()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (forDiary) "Add to diary · ${vm.diaryDayLabel()}" else "Add ingredient") },
                navigationIcon = {
                    IconButton(onClick = { vm.back() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(Modifier.padding(padding)) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text(if (forDiary) "Search foods, e.g. banana" else "Search foods, e.g. potatoes raw") },
                leadingIcon = { Icon(Icons.Filled.Search, null) },
                trailingIcon = {
                    if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Filled.Clear, "Clear") }
                },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .focusRequester(focusRequester),
            )
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item {
                    OutlinedButton(
                        onClick = { vm.navigate(Screen.CustomFoodEditor(null)) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Filled.Add, null)
                        Text("Create your own food (from a label)", Modifier.padding(start = 8.dp))
                    }
                }
                if (matchingRecipes.isNotEmpty()) {
                    item { Text("My recipes", style = MaterialTheme.typography.titleSmall) }
                    items(matchingRecipes, key = { "recipe:" + it.id }) { recipe ->
                        RecipeResultCard(recipe) { vm.navigate(Screen.RecipeDetail(recipe.id)) }
                    }
                }
                if (db == null) {
                    item {
                        Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    }
                }
                if (query.isBlank()) {
                    if (customFoods.isNotEmpty()) {
                        item { Text("My foods", style = MaterialTheme.typography.titleSmall) }
                        items(customFoods.sortedBy { it.name.lowercase() }, key = { it.id }) { food ->
                            FoodCard(food, onClick = { selected = food })
                        }
                    }
                    item {
                        Text(
                            if (forDiary) {
                                "Search for a food, then enter a weight or pick a size such as “1 medium”."
                            } else {
                                "Tip: include the preparation in your search (e.g. “rice raw” or “chicken breast roasted”) " +
                                    "and weigh ingredients in the same state."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else if (results.isEmpty() && matchingRecipes.isEmpty() && db != null) {
                    item {
                        Text(
                            "No foods match “$query”. Try fewer or different words, or create your own food.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    if (matchingRecipes.isNotEmpty() && results.isNotEmpty()) {
                        item { Text("Foods", style = MaterialTheme.typography.titleSmall) }
                    }
                    items(results, key = { it.id }) { food ->
                        FoodCard(food, onClick = { selected = food })
                    }
                }
            }
        }
    }

    selected?.let { food ->
        AmountDialog(
            food = food,
            confirmLabel = if (forDiary) "Log" else "Add",
            preferPortion = forDiary,
            onDismiss = { selected = null },
            onConfirm = { amount ->
                selected = null
                if (forDiary) {
                    vm.logFood(food, amount)
                    val what = amount.label ?: formatGrams(amount.grams)
                    scope.launch { snackbar.showSnackbar("Logged $what of ${food.name}") }
                } else {
                    vm.addIngredient(food, amount)
                    vm.back()
                }
            },
        )
    }
}

private fun matchRecipes(recipes: List<Recipe>, query: String): List<Recipe> {
    val tokens = FoodDatabase.normalize(query).split(' ').filter { it.isNotEmpty() }
    val all = recipes.sortedBy { it.name.lowercase() }
    if (tokens.isEmpty()) return all
    return all.filter { r -> FoodDatabase.normalize(r.name).let { name -> tokens.all { it in name } } }
}

@Composable
private fun RecipeResultCard(recipe: Recipe, onClick: () -> Unit) {
    Card(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
            Text(recipe.name, style = MaterialTheme.typography.titleSmall)
            Text(
                "Recipe · ${formatGrams(recipe.totalWeight)} · per 100 g",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(macroSummary(NutritionCalculator.per100g(recipe)), style = MaterialTheme.typography.bodySmall)
        }
    }
}
