package com.mealmacros.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.mealmacros.app.AppViewModel
import com.mealmacros.app.Screen
import com.mealmacros.app.parseNumber
import com.mealmacros.core.Food
import com.mealmacros.core.FoodDatabase
import com.mealmacros.core.Nutrient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FoodSearchScreen(vm: AppViewModel) {
    var query by rememberSaveable { mutableStateOf("") }
    var results by remember { mutableStateOf(emptyList<Food>()) }
    var selected by remember { mutableStateOf<Food?>(null) }
    val db = vm.foodDatabase
    val customFoods = vm.customFoods
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(query, db, customFoods) {
        delay(150) // debounce typing
        results = withContext(Dispatchers.Default) {
            FoodDatabase.rank(customFoods, query) + (db?.search(query) ?: emptyList())
        }
    }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Add ingredient") },
                navigationIcon = {
                    IconButton(onClick = { vm.back() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding)) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("Search foods, e.g. potatoes raw") },
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
                            "Tip: include the preparation in your search (e.g. “rice raw” or “chicken breast roasted”) " +
                                "and weigh ingredients in the same state.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else if (results.isEmpty() && db != null) {
                    item {
                        Text(
                            "No foods match “$query”. Try fewer or different words, or create your own food.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    items(results, key = { it.id }) { food ->
                        FoodCard(food, onClick = { selected = food })
                    }
                }
            }
        }
    }

    selected?.let { food ->
        AddIngredientDialog(
            food = food,
            onDismiss = { selected = null },
            onAdd = { grams ->
                selected = null
                vm.addIngredient(food, grams)
                vm.back()
            },
        )
    }
}

@Composable
private fun AddIngredientDialog(food: Food, onDismiss: () -> Unit, onAdd: (Double) -> Unit) {
    var text by remember { mutableStateOf("") }
    val grams = parseNumber(text)?.takeIf { it > 0 }
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(food.name) },
        text = {
            Column(Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { v -> text = v.filter { it.isDigit() || it == '.' || it == ',' } },
                    label = { Text("Weight in recipe") },
                    suffix = { Text("g") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester),
                )
                val preview = if (grams != null) food.per100g * (grams / 100.0) else food.per100g
                Text(
                    (if (grams != null) "${formatGrams(grams)}: " else "Per 100 g: ") + macroSummary(preview),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 8.dp),
                )
                if (!food.per100g.isKnown(Nutrient.ENERGY)) {
                    Text(
                        "This food has no energy data.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(enabled = grams != null, onClick = { grams?.let(onAdd) }) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
