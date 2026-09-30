package com.mealmacros.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mealmacros.app.AppViewModel
import com.mealmacros.app.HomeTab
import com.mealmacros.app.Screen
import com.mealmacros.app.SearchTarget
import com.mealmacros.core.Food
import com.mealmacros.core.NutrientProfile
import com.mealmacros.core.NutritionCalculator
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(vm: AppViewModel) {
    Scaffold(
        topBar = {
            TopAppBar(title = {
                Text(
                    when (vm.tab) {
                        HomeTab.RECIPES -> "My recipes"
                        HomeTab.DIARY -> "Food diary"
                        HomeTab.FOODS -> "My foods"
                    }
                )
            })
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = vm.tab == HomeTab.RECIPES,
                    onClick = { vm.tab = HomeTab.RECIPES },
                    icon = { Icon(Icons.AutoMirrored.Filled.List, null) },
                    label = { Text("Recipes") },
                )
                NavigationBarItem(
                    selected = vm.tab == HomeTab.DIARY,
                    onClick = { vm.tab = HomeTab.DIARY },
                    icon = { Icon(Icons.Filled.DateRange, null) },
                    label = { Text("Diary") },
                )
                NavigationBarItem(
                    selected = vm.tab == HomeTab.FOODS,
                    onClick = { vm.tab = HomeTab.FOODS },
                    icon = { Icon(Icons.Filled.Star, null) },
                    label = { Text("My foods") },
                )
            }
        },
        floatingActionButton = {
            when (vm.tab) {
                HomeTab.RECIPES -> ExtendedFloatingActionButton(
                    onClick = { vm.startNewRecipe() },
                    icon = { Icon(Icons.Filled.Add, null) },
                    text = { Text("New recipe") },
                )
                HomeTab.FOODS -> ExtendedFloatingActionButton(
                    onClick = { vm.navigate(Screen.CustomFoodEditor(null)) },
                    icon = { Icon(Icons.Filled.Add, null) },
                    text = { Text("New food") },
                )
                HomeTab.DIARY -> ExtendedFloatingActionButton(
                    onClick = { vm.navigate(Screen.FoodSearch(SearchTarget.DIARY)) },
                    icon = { Icon(Icons.Filled.Add, null) },
                    text = { Text("Add food") },
                )
            }
        },
    ) { padding ->
        Box(Modifier.padding(padding)) {
            when (vm.tab) {
                HomeTab.RECIPES -> RecipeList(vm)
                HomeTab.DIARY -> DiaryTab(vm)
                HomeTab.FOODS -> CustomFoodList(vm)
            }
        }
    }
}

@Composable
private fun RecipeList(vm: AppViewModel) {
    val recipes = vm.recipes.sortedBy { it.name.lowercase() }
    if (recipes.isEmpty()) {
        EmptyState(
            "No recipes yet",
            "Tap “New recipe”, add your ingredients and their weights, and the app will work out the " +
                "calories, macros, vitamins and minerals for any portion you eat.",
        )
        return
    }
    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(recipes, key = { it.id }) { recipe ->
            val per100 = NutritionCalculator.per100g(recipe)
            Card(
                Modifier
                    .fillMaxWidth()
                    .clickable { vm.navigate(Screen.RecipeDetail(recipe.id)) }
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text(recipe.name, style = MaterialTheme.typography.titleMedium)
                    Text(
                        "${recipe.ingredients.size} ingredients · ${formatGrams(recipe.totalWeight)} total",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        "Per 100 g: ${macroSummary(per100)}",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
    }
}

@Composable
private fun DiaryTab(vm: AppViewModel) {
    val zone = ZoneId.systemDefault()
    val day = LocalDate.ofEpochDay(vm.diaryDay)
    val entries = vm.diary
        .filter { Instant.ofEpochMilli(it.timestamp).atZone(zone).toLocalDate() == day }
        .sortedBy { it.timestamp }
    val totals = NutrientProfile.sum(entries.map { it.nutrients })
    val timeFormat = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)
    var pendingDelete by rememberSaveable { mutableStateOf<String?>(null) }

    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 0.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { vm.diaryDay-- }) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Previous day")
                }
                Text(
                    when (day) {
                        LocalDate.now() -> "Today"
                        LocalDate.now().minusDays(1) -> "Yesterday"
                        else -> day.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))
                    },
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { vm.diaryDay++ }) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Next day")
                }
            }
        }
        if (entries.isEmpty()) {
            item {
                Text(
                    "Nothing logged for this day. Tap “Add food” to log a food (by weight or a size such as " +
                        "“1 medium banana”) or one of your recipes.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 24.dp),
                )
            }
        } else {
            items(entries, key = { it.id }) { entry ->
                Card(Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(start = 16.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(entry.name, style = MaterialTheme.typography.titleSmall)
                            Text(
                                listOfNotNull(
                                    Instant.ofEpochMilli(entry.timestamp).atZone(zone).format(timeFormat),
                                    entry.amountLabel,
                                    formatGrams(entry.grams),
                                ).joinToString(" · "),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(macroSummary(entry.nutrients), style = MaterialTheme.typography.bodyMedium)
                        }
                        IconButton(onClick = { pendingDelete = entry.id }) {
                            Icon(Icons.Filled.Delete, "Remove")
                        }
                    }
                }
            }
            item {
                NutritionDetails(totals, Modifier.padding(top = 8.dp), title = "Day total")
            }
        }
    }

    pendingDelete?.let { id ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Remove entry?") },
            confirmButton = {
                TextButton(onClick = { vm.deleteDiaryEntry(id); pendingDelete = null }) { Text("Remove") }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun CustomFoodList(vm: AppViewModel) {
    val foods = vm.customFoods.sortedBy { it.name.lowercase() }
    var pendingDelete by rememberSaveable { mutableStateOf<String?>(null) }
    if (foods.isEmpty()) {
        EmptyState(
            "No custom foods",
            "The built-in database has over 8,700 foods. For anything else (e.g. a branded product) tap " +
                "“New food” and copy the values from its nutrition label.",
        )
        return
    }
    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(foods, key = { it.id }) { food ->
            FoodCard(food, onClick = { vm.navigate(Screen.CustomFoodEditor(food.id)) }) {
                IconButton(onClick = { pendingDelete = food.id }) { Icon(Icons.Filled.Delete, "Delete") }
            }
        }
    }
    pendingDelete?.let { id ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Delete food?") },
            text = { Text("Recipes that already use it keep their values.") },
            confirmButton = {
                TextButton(onClick = { vm.deleteCustomFood(id); pendingDelete = null }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("Cancel") } },
        )
    }
}

@Composable
fun FoodCard(food: Food, onClick: () -> Unit, trailing: @Composable () -> Unit = {}) {
    Card(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(Modifier.padding(start = 16.dp, top = 10.dp, bottom = 10.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f).padding(end = 8.dp)) {
                Text(food.name, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                val group = if (food.isCustom) "My food" else food.group
                Text(
                    "$group · per 100 g",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (food.isCustom) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(macroSummary(food.per100g), style = MaterialTheme.typography.bodySmall)
            }
            trailing()
        }
    }
}

@Composable
fun EmptyState(title: String, message: String) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(title, style = MaterialTheme.typography.titleLarge)
        Text(
            message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}
