package com.mealmacros.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.mealmacros.app.AppViewModel
import com.mealmacros.app.formatPlain
import com.mealmacros.app.parseNumber
import com.mealmacros.core.Food
import com.mealmacros.core.Nutrient
import com.mealmacros.core.NutrientCategory
import com.mealmacros.core.NutrientProfile
import com.mealmacros.core.Portion

/** Nutrients shown by default - the ones found on a typical nutrition label. */
private val LABEL_NUTRIENTS = listOf(
    Nutrient.ENERGY, Nutrient.FAT, Nutrient.SATURATED_FAT, Nutrient.CARBS, Nutrient.SUGARS,
    Nutrient.FIBER, Nutrient.PROTEIN, Nutrient.SODIUM,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomFoodScreen(vm: AppViewModel, foodId: String?) {
    val existing = foodId?.let { vm.customFood(it) }
    var name by rememberSaveable { mutableStateOf(existing?.name ?: "") }
    var basisText by rememberSaveable { mutableStateOf("100") }
    val existingPortion = existing?.portions?.firstOrNull()
    var portionName by rememberSaveable { mutableStateOf(existingPortion?.description ?: "") }
    var portionGramsText by rememberSaveable { mutableStateOf(existingPortion?.grams?.let(::formatPlain) ?: "") }
    val values = remember {
        mutableStateMapOf<Nutrient, String>().apply {
            existing?.per100g?.values?.forEach { (n, v) -> put(n, formatPlain(v)) }
        }
    }
    var showAll by rememberSaveable { mutableStateOf(existing?.per100g?.values?.keys?.any { it !in LABEL_NUTRIENTS } == true) }
    val basis = parseNumber(basisText)?.takeIf { it > 0 }
    val invalid = values.values.any { it.isNotBlank() && parseNumber(it) == null }
    val portionGrams = parseNumber(portionGramsText)?.takeIf { it > 0 }
    val portionInvalid = portionGramsText.isNotBlank() && portionGrams == null
    val canSave = name.isNotBlank() && basis != null && !invalid && !portionInvalid &&
        parseNumber(values[Nutrient.ENERGY] ?: "") != null

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (existing == null) "New food" else "Edit food") },
                navigationIcon = {
                    IconButton(onClick = { vm.back() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                },
                actions = {
                    TextButton(enabled = canSave, onClick = {
                        val scale = 100.0 / (basis ?: return@TextButton)
                        val per100 = values.mapNotNull { (n, text) -> parseNumber(text)?.let { n to it * scale } }.toMap()
                        vm.saveCustomFood(
                            Food(
                                id = existing?.id ?: "",
                                name = name.trim(),
                                group = "My foods",
                                per100g = NutrientProfile(per100),
                                isCustom = true,
                                portions = listOfNotNull(
                                    portionGrams?.let { Portion(portionName.trim().ifEmpty { "serving" }, it) }
                                ),
                            )
                        )
                        vm.back()
                    }) { Text("Save") }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Food name") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth(),
            )
            NumberField(
                label = "Values below are per",
                text = basisText,
                unit = "g",
                onChange = { basisText = it },
                supporting = "Copy the numbers from the label's “per 100 g” or “per serving” column and enter that weight here.",
            )
            Text("Portion size (optional)", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
            Text(
                "Lets you log this food as e.g. “1 bar” or “2 slices” instead of weighing it.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = portionName,
                onValueChange = { portionName = it },
                label = { Text("Portion name") },
                placeholder = { Text("e.g. bar, slice, serving") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            NumberField(
                label = "Weight of one portion",
                text = portionGramsText,
                unit = "g",
                onChange = { portionGramsText = it },
            )
            Text("Nutrition", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
            LABEL_NUTRIENTS.forEach { n ->
                NutrientField(n, values[n] ?: "") { values[n] = it }
            }
            TextButton(onClick = { showAll = !showAll }) {
                Icon(if (showAll) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown, null)
                Text(if (showAll) "Hide other nutrients" else "More nutrients (vitamins, minerals…)")
            }
            if (showAll) {
                NutrientCategory.entries.forEach { category ->
                    val rest = Nutrient.inCategory(category).filter { it !in LABEL_NUTRIENTS }
                    if (rest.isNotEmpty()) {
                        Text(category.title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp))
                        rest.forEach { n -> NutrientField(n, values[n] ?: "") { values[n] = it } }
                    }
                }
            }
            Text(
                "Leave a field blank if the value is unknown. Energy is required.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun NutrientField(nutrient: Nutrient, text: String, onChange: (String) -> Unit) {
    NumberField(nutrient.label, text, nutrient.unit.symbol, onChange)
}

@Composable
private fun NumberField(label: String, text: String, unit: String, onChange: (String) -> Unit, supporting: String? = null) {
    OutlinedTextField(
        value = text,
        onValueChange = { v -> onChange(v.filter { it.isDigit() || it == '.' || it == ',' }) },
        label = { Text(label) },
        suffix = { Text(unit) },
        singleLine = true,
        isError = text.isNotBlank() && parseNumber(text) == null,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        supportingText = if (supporting != null) {
            { Text(supporting) }
        } else {
            null
        },
        modifier = Modifier.fillMaxWidth(),
    )
}
