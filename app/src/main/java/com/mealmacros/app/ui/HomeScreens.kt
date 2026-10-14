package com.mealmacros.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.OutlinedButton
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import com.mealmacros.core.DiaryEdits
import com.mealmacros.core.DiaryEntry
import com.mealmacros.core.Nutrient
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
                        HomeTab.DASHBOARD -> "Dashboard"
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
                    selected = vm.tab == HomeTab.DASHBOARD,
                    onClick = { vm.tab = HomeTab.DASHBOARD },
                    icon = { Icon(BarChartIcon, null) },
                    label = { Text("Dashboard") },
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
                HomeTab.DASHBOARD -> {}
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
                HomeTab.DASHBOARD -> DashboardTab(vm)
                HomeTab.FOODS -> CustomFoodList(vm)
            }
        }
    }
}

@Composable
private fun RecipeList(vm: AppViewModel) {
    val recipes = vm.recipes.sortedBy { it.name.lowercase() }
    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            OutlinedButton(onClick = { vm.navigate(Screen.RecipeImport("")) }, modifier = Modifier.fillMaxWidth()) {
                Text("Import a recipe from the web")
            }
        }
        if (recipes.isEmpty()) {
            item {
                Text(
                    "No recipes yet. Tap “New recipe”, add your ingredients and their weights, and the app will " +
                        "work out the calories, macros, vitamins and minerals for any portion you eat. Or import " +
                        "one from a recipe website and adjust it to what you cooked.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(24.dp),
                )
            }
        }
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

@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
private fun DiaryTab(vm: AppViewModel) {
    val zone = ZoneId.systemDefault()
    val day = LocalDate.ofEpochDay(vm.diaryDay)
    val entries = vm.diary
        .filter { Instant.ofEpochMilli(it.timestamp).atZone(zone).toLocalDate() == day }
        .sortedBy { it.timestamp }
    val totals = NutrientProfile.sum(entries.map { it.nutrients })
    val timeFormat = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)

    var selected by remember { mutableStateOf(emptySet<String>()) }
    var editing by remember { mutableStateOf<DiaryEntry?>(null) }
    var groupAmount by remember { mutableStateOf(false) }
    var groupTime by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    LaunchedEffect(vm.diaryDay) { selected = emptySet() }
    val selectedEntries = entries.filter { it.id in selected }
    val selecting = selectedEntries.isNotEmpty()

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
        if (selecting) {
            item {
                Card(
                    Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                ) {
                    Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "${selectedEntries.size} selected · " +
                                    formatKcal(selectedEntries.sumOf { it.nutrients.amount(Nutrient.ENERGY) }),
                                style = MaterialTheme.typography.titleSmall,
                                modifier = Modifier.weight(1f),
                            )
                            IconButton(onClick = { selected = emptySet() }) { Icon(Icons.Filled.Close, "Clear selection") }
                        }
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(onClick = { groupAmount = true }) { Text("Amount eaten") }
                            TextButton(onClick = { groupTime = true }) { Text("Time") }
                            TextButton(onClick = { confirmDelete = true }) { Text("Remove") }
                            TextButton(onClick = { selected = entries.map { it.id }.toSet() }) { Text("Select all") }
                        }
                    }
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
            if (!selecting) {
                item {
                    Text(
                        "Tap an entry to change the amount or time. Press and hold to select several, e.g. a " +
                            "plate you only ate part of.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            items(entries, key = { it.id }) { entry ->
                val isSelected = entry.id in selected
                fun toggle() {
                    selected = if (isSelected) selected - entry.id else selected + entry.id
                }
                Card(
                    Modifier
                        .fillMaxWidth()
                        .combinedClickable(
                            onClick = { if (selecting) toggle() else editing = entry },
                            onLongClick = { toggle() },
                        ),
                    colors = if (isSelected) {
                        CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                    } else {
                        CardDefaults.cardColors()
                    },
                ) {
                    Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (selecting) {
                            Checkbox(checked = isSelected, onCheckedChange = { toggle() })
                        }
                        Column(Modifier.weight(1f)) {
                            Text(entry.name, style = MaterialTheme.typography.titleSmall)
                            Text(
                                Instant.ofEpochMilli(entry.timestamp).atZone(zone).format(timeFormat) + " · " +
                                    entryAmount(entry),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(macroSummary(entry.nutrients), style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
            item {
                NutritionDetails(totals, Modifier.padding(top = 8.dp), title = "Day total")
            }
        }
    }

    editing?.let { entry ->
        EditEntryDialog(
            entry = entry,
            onDismiss = { editing = null },
            onSave = { vm.updateDiaryEntries(listOf(it)); editing = null },
            onDelete = { vm.deleteDiaryEntries(setOf(entry.id)); editing = null },
        )
    }
    if (groupAmount && selecting) {
        GroupAmountDialog(
            entries = selectedEntries,
            onDismiss = { groupAmount = false },
            onSave = { vm.updateDiaryEntries(it); groupAmount = false; selected = emptySet() },
        )
    }
    if (groupTime && selecting) {
        TimeDialog(
            title = "Time eaten (${selectedEntries.size} items)",
            initial = entryTime(selectedEntries.first()),
            onDismiss = { groupTime = false },
            onConfirm = { t ->
                vm.updateDiaryEntries(selectedEntries.map { DiaryEdits.atTime(it, t) })
                groupTime = false
                selected = emptySet()
            },
        )
    }
    if (confirmDelete && selecting) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Remove ${selectedEntries.size} entries?") },
            confirmButton = {
                TextButton(onClick = {
                    vm.deleteDiaryEntries(selected)
                    selected = emptySet()
                    confirmDelete = false
                }) { Text("Remove") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
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
            "The built-in database has over 11,000 foods, including UK foods, takeaways and the menus of " +
                "McDonald's, KFC, Burger King, Subway, Nando's, Popeyes, Greggs, Costa and LEON. For anything " +
                "else (e.g. a supermarket product) tap " +
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
                val group = when {
                    food.isCustom -> "My food"
                    food.source == "UK" || food.source == "US" -> "${food.source} · ${food.group}"
                    else -> food.source
                }
                // Restaurant items are shown per serving (item, 6-inch sub, medium drink...).
                val item = food.defaultPortion()?.takeIf { food.isRestaurant }
                Text(
                    when {
                        !food.weightKnown -> "$group · per item"
                        item != null -> "$group · per ${item.description} (${formatGrams(item.grams)})"
                        else -> "$group · per 100 g"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (food.isCustom) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    macroSummary(if (item != null) food.nutrientsFor(item.grams) else food.per100g),
                    style = MaterialTheme.typography.bodySmall,
                )
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
