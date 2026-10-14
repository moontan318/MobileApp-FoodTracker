package com.mealmacros.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.material3.CardDefaults
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mealmacros.app.AppViewModel
import com.mealmacros.app.DraftItem
import com.mealmacros.app.Screen
import com.mealmacros.app.formatPlain
import com.mealmacros.app.parseNumber
import com.mealmacros.core.Nutrient
import com.mealmacros.core.NutrientProfile

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecipeEditorScreen(vm: AppViewModel) {
    val draft = vm.draft ?: return
    var confirmDiscard by remember { mutableStateOf(false) }
    val canSave = draft.name.isNotBlank() && draft.items.isNotEmpty()

    fun leave() {
        if (vm.draftIsDirty) confirmDiscard = true else {
            vm.discardDraft()
            vm.back()
        }
    }
    BackHandler { leave() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (draft.recipeId == null) "New recipe" else "Edit recipe") },
                navigationIcon = {
                    IconButton(onClick = { leave() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                },
                actions = {
                    TextButton(
                        enabled = canSave,
                        onClick = {
                            val id = vm.saveDraft() ?: return@TextButton
                            vm.back()
                            if (draft.recipeId == null) vm.navigate(Screen.RecipeDetail(id))
                        },
                    ) { Text("Save") }
                },
            )
        },
    ) { padding ->
        val rawWeight = draft.items.sumOf { it.ingredient.grams }
        val total = NutrientProfile.sum(draft.items.map { it.ingredient.nutrients })
        LazyColumn(
            Modifier.padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                OutlinedTextField(
                    value = draft.name,
                    onValueChange = { v -> vm.updateDraft { it.copy(name = v) } },
                    label = { Text("Recipe name") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            draft.importSummary?.let { summary ->
                item {
                    Card(
                        Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                    ) {
                        Text(summary, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(12.dp))
                    }
                }
            }
            item {
                Text("Ingredients", style = MaterialTheme.typography.titleMedium)
                if (draft.items.isEmpty()) {
                    Text(
                        "Add each ingredient with the amount you use in the whole recipe, by weight or by " +
                            "size (e.g. 2 medium potatoes).",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            items(draft.items, key = { it.key }) { item ->
                IngredientRow(
                    item,
                    onGramsChange = { grams ->
                        vm.updateDraft { d ->
                            d.copy(items = d.items.map {
                                if (it.key == item.key) {
                                    it.copy(ingredient = it.ingredient.copy(grams = grams), needsReview = false)
                                } else {
                                    it
                                }
                            })
                        }
                    },
                    onReplace = { vm.startReplaceIngredient(item.key) },
                    onRemove = { vm.updateDraft { d -> d.copy(items = d.items.filterNot { it.key == item.key }) } },
                )
            }
            item {
                OutlinedButton(onClick = { vm.startAddIngredient() }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Filled.Add, null)
                    Text("Add ingredient", Modifier.padding(start = 8.dp))
                }
            }
            item {
                OutlinedTextField(
                    value = draft.servingsText,
                    onValueChange = { v -> vm.updateDraft { it.copy(servingsText = v.filter { c -> c.isDigit() }.take(3)) } },
                    label = { Text("Servings (optional)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    supportingText = { Text("Adds a “1 serving” option when logging this recipe.") },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item {
                OutlinedTextField(
                    value = draft.cookedWeightText,
                    onValueChange = { v ->
                        vm.updateDraft { it.copy(cookedWeightText = v.filter { c -> c.isDigit() || c == '.' || c == ',' }) }
                    },
                    label = { Text("Cooked weight (optional)") },
                    suffix = { Text("g") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    supportingText = {
                        Text(
                            "Cooking changes weight (e.g. water evaporates). Weigh the finished dish for accurate " +
                                "portions. Leave blank to use the ingredient total of ${formatGrams(rawWeight)}."
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item {
                OutlinedTextField(
                    value = draft.notes,
                    onValueChange = { v -> vm.updateDraft { it.copy(notes = v) } },
                    label = { Text("Notes (optional)") },
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                )
            }
            if (draft.items.isNotEmpty()) {
                item {
                    EnergyMacroCard(total, title = "Whole recipe · ${formatGrams(draft.cookedWeight ?: rawWeight)}")
                }
            }
        }
    }

    if (confirmDiscard) {
        AlertDialog(
            onDismissRequest = { confirmDiscard = false },
            title = { Text("Discard changes?") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDiscard = false
                    vm.discardDraft()
                    vm.back()
                }) { Text("Discard") }
            },
            dismissButton = { TextButton(onClick = { confirmDiscard = false }) { Text("Keep editing") } },
        )
    }
}

@Composable
private fun IngredientRow(item: DraftItem, onGramsChange: (Double) -> Unit, onReplace: () -> Unit, onRemove: () -> Unit) {
    val ingredient = item.ingredient
    val unmatched = ingredient.foodId == AppViewModel.UNMATCHED_FOOD_ID
    // Re-read the weight when the food is replaced.
    var text by remember(item.key, ingredient.foodId) { mutableStateOf(formatPlain(Math.round(ingredient.grams * 10) / 10.0)) }
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(start = 16.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(
                Modifier
                    .weight(1f)
                    .padding(end = 8.dp)
                    .clickable(onClick = onReplace)
            ) {
                if (ingredient.sourceText.isNotBlank()) {
                    Text(
                        "Recipe: ${ingredient.sourceText}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    if (unmatched) "⚠ No match – tap to choose a food" else (if (item.needsReview) "⚠ " else "") + ingredient.name,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (unmatched) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    formatKcal(ingredient.nutrients.amount(Nutrient.ENERGY)) +
                        if (ingredient.sourceText.isNotBlank()) " · tap to change food" else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            OutlinedTextField(
                value = text,
                onValueChange = { v ->
                    text = v.filter { it.isDigit() || it == '.' || it == ',' }
                    parseNumber(text)?.let(onGramsChange)
                },
                suffix = { Text("g") },
                singleLine = true,
                isError = parseNumber(text) == null || (ingredient.grams <= 0.0 && !unmatched),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.width(110.dp),
            )
            IconButton(onClick = onRemove) { Icon(Icons.Filled.Close, "Remove") }
        }
    }
}
