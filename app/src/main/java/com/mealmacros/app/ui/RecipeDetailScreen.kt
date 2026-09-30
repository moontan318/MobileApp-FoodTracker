package com.mealmacros.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.SuggestionChip
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
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mealmacros.app.AppViewModel
import com.mealmacros.app.Screen
import com.mealmacros.app.formatPlain
import com.mealmacros.app.parseNumber
import com.mealmacros.core.Nutrient
import com.mealmacros.core.NutritionCalculator
import com.mealmacros.core.PortionNutrition
import com.mealmacros.core.Recipe
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.roundToLong

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecipeDetailScreen(vm: AppViewModel, recipeId: String) {
    val recipe = vm.recipe(recipeId)
    if (recipe == null) {
        // Deleted (or never existed) - leave the screen.
        LaunchedEffect(recipeId) { vm.back() }
        return
    }
    var portionText by rememberSaveable(recipeId) { mutableStateOf("100") }
    val portionGrams = parseNumber(portionText)?.takeIf { it >= 0 }
    var menuOpen by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val focus = LocalFocusManager.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(recipe.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = { vm.back() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                },
                actions = {
                    IconButton(onClick = { vm.startEditRecipe(recipe) }) { Icon(Icons.Filled.Edit, "Edit") }
                    IconButton(onClick = { menuOpen = true }) { Icon(Icons.Filled.MoreVert, "More") }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(text = { Text("Duplicate") }, onClick = {
                            menuOpen = false
                            val id = vm.duplicateRecipe(recipe)
                            vm.back()
                            vm.navigate(Screen.RecipeDetail(id))
                        })
                        DropdownMenuItem(text = { Text("Delete") }, onClick = {
                            menuOpen = false
                            confirmDelete = true
                        })
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            Surface(tonalElevation = 3.dp) {
                Button(
                    onClick = {
                        val grams = portionGrams ?: return@Button
                        vm.logPortion(recipe, grams)
                        focus.clearFocus()
                        scope.launch { snackbar.showSnackbar("Logged ${formatGrams(grams)} of ${recipe.name} to ${vm.diaryDayLabel()}") }
                    },
                    enabled = portionGrams != null && portionGrams > 0 && recipe.totalWeight > 0,
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                ) {
                    Text("Log ${portionGrams?.let(::formatGrams) ?: ""} to diary (${vm.diaryDayLabel()})")
                }
            }
        },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            RecipeSummary(recipe)
            PortionInput(recipe, portionText, onChange = { portionText = it })
            if (portionGrams != null) {
                val portion = NutritionCalculator.portion(recipe, portionGrams)
                PortionContents(recipe, portion)
                NutritionDetails(portion.nutrients, title = "Nutrition in ${formatGrams(portionGrams)}")
            }
            Spacer(Modifier.height(8.dp))
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete “${recipe.name}”?") },
            text = { Text("Diary entries already logged are kept.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    vm.deleteRecipe(recipe.id)
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun RecipeSummary(recipe: Recipe) {
    val total = NutritionCalculator.recipeTotal(recipe)
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text("Whole recipe", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(formatGrams(recipe.totalWeight), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
            if (recipe.cookedWeight != null) {
                Text(
                    "Cooked weight (ingredients weighed ${formatGrams(recipe.rawWeight)} raw)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(macroSummary(total), style = MaterialTheme.typography.bodyMedium)
            if (recipe.notes.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(recipe.notes, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PortionInput(recipe: Recipe, text: String, onChange: (String) -> Unit) {
    val total = recipe.totalWeight
    val grams = parseNumber(text)
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text("How much are you eating?", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = text,
                onValueChange = { new -> onChange(new.filter { it.isDigit() || it == '.' || it == ',' }) },
                label = { Text("Portion weight") },
                suffix = { Text("g") },
                singleLine = true,
                isError = grams == null,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                supportingText = {
                    if (grams != null && total > 0) {
                        Text(
                            String.format(
                                Locale.getDefault(),
                                "%.1f%% of the recipe (%s total)",
                                grams / total * 100,
                                formatGrams(total),
                            )
                        )
                    } else if (grams == null) {
                        Text("Enter a weight in grams")
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )
            if (total > 0) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SuggestionChip(onClick = { onChange("100") }, label = { Text("100 g") })
                    listOf(4 to "¼", 3 to "⅓", 2 to "½").forEach { (div, label) ->
                        SuggestionChip(
                            onClick = { onChange(formatPlain((total / div).roundToLong().toDouble())) },
                            label = { Text("$label recipe") },
                        )
                    }
                    SuggestionChip(onClick = { onChange(formatPlain(total)) }, label = { Text("Whole") })
                }
            }
        }
    }
}

@Composable
private fun PortionContents(recipe: Recipe, portion: PortionNutrition) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(vertical = 12.dp)) {
            Text(
                "This portion contains",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            if (recipe.cookedWeight != null) {
                Text(
                    "Raw ingredient weights",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
            Spacer(Modifier.height(4.dp))
            portion.shares.forEachIndexed { i, share ->
                if (i > 0) HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                Row(
                    Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f).padding(end = 8.dp)) {
                        Text(share.ingredient.name, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(
                            "${formatGrams(share.grams)} of ${formatGrams(share.ingredient.grams)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(formatKcal(share.nutrients.amount(Nutrient.ENERGY)), style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "P ${formatAmount(share.nutrients.amount(Nutrient.PROTEIN))} · " +
                                "C ${formatAmount(share.nutrients.amount(Nutrient.CARBS))} · " +
                                "F ${formatAmount(share.nutrients.amount(Nutrient.FAT))}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}
