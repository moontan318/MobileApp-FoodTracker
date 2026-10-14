package com.mealmacros.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
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
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
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
    val context = LocalContext.current
    var enteringBarcode by remember { mutableStateOf(false) }
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
                title = {
                    Text(
                        when {
                            forDiary -> "Add to diary · ${vm.diaryDayLabel()}"
                            vm.replacingKey != null -> "Replace ingredient"
                            else -> "Add ingredient"
                        }
                    )
                },
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
                placeholder = { Text(if (forDiary) "Search foods, e.g. banana, Big Mac, Zinger" else "Search foods, e.g. potatoes raw") },
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
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = {
                                vm.barcodeMessage = null
                                scanBarcode(
                                    context,
                                    onResult = { code -> vm.lookupBarcode(code) { selected = it } },
                                    onError = { enteringBarcode = true },
                                )
                            },
                            modifier = Modifier.weight(1f),
                        ) { Text("Scan barcode") }
                        OutlinedButton(
                            onClick = { vm.navigate(Screen.CustomFoodEditor(null)) },
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(Icons.Filled.Add, null)
                            Text("Own food", Modifier.padding(start = 4.dp))
                        }
                    }
                    vm.barcodeMessage?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
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
                // Packaged foods not in the app: search Open Food Facts.
                if (query.isNotBlank() && db != null) {
                    val online = vm.onlineSearch.takeIf { it.query == query }
                    if (online != null && online.results.isNotEmpty()) {
                        item { Text("Online · Open Food Facts", style = MaterialTheme.typography.titleSmall) }
                        items(online.results, key = { "online:" + it.id }) { food ->
                            FoodCard(food, onClick = { selected = food })
                        }
                    } else {
                        item {
                            if (online?.loading == true) {
                                Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                                    CircularProgressIndicator()
                                }
                            } else {
                                OutlinedButton(onClick = { vm.searchOnline(query) }, modifier = Modifier.fillMaxWidth()) {
                                    Icon(Icons.Filled.Search, null)
                                    Text("Search supermarket products online for “$query”", Modifier.padding(start = 8.dp))
                                }
                                online?.error?.let {
                                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (enteringBarcode) {
        BarcodeEntryDialog(
            onDismiss = { enteringBarcode = false },
            onConfirm = { code ->
                enteringBarcode = false
                vm.lookupBarcode(code) { selected = it }
            },
        )
    }

    selected?.let { food ->
        AmountDialog(
            food = food,
            confirmLabel = if (forDiary) "Log" else "Add",
            preferPortion = forDiary,
            allowUnknownWeight = forDiary,
            onDismiss = { selected = null },
            initialTime = if (forDiary) vm.defaultLogTime() else null,
            initialGrams = if (forDiary) null else vm.replacingGrams(),
            onConfirm = { amount ->
                selected = null
                vm.rememberFood(food) // keep online products for next time
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

/** Typing a barcode in, for phones where the scanner isn't available. */
@Composable
private fun BarcodeEntryDialog(onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var code by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Enter barcode") },
        text = {
            Column {
                Text(
                    "The camera scanner isn't available, so type the numbers under the barcode.",
                    style = MaterialTheme.typography.bodySmall,
                )
                OutlinedTextField(
                    value = code,
                    onValueChange = { v -> code = v.filter { it.isDigit() }.take(14) },
                    label = { Text("Barcode") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
            }
        },
        confirmButton = { TextButton(enabled = code.length >= 8, onClick = { onConfirm(code) }) { Text("Look up") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
