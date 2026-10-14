package com.mealmacros.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import java.time.LocalTime
import com.mealmacros.app.Amount
import com.mealmacros.app.parseNumber
import com.mealmacros.core.DiaryEntry
import com.mealmacros.core.Food
import com.mealmacros.core.Nutrient
import com.mealmacros.core.Portion

/**
 * Asks how much of [food] to add, either as a weight in grams or as a number of
 * household measures (e.g. 1 × "medium" banana = 118 g).
 *
 * [preferPortion] pre-selects a typical whole item (such as "medium") when the food has one.
 * Foods whose weight isn't published can only be added as items, and only when
 * [allowUnknownWeight] (recipes need real weights).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AmountDialog(
    food: Food,
    confirmLabel: String,
    preferPortion: Boolean,
    allowUnknownWeight: Boolean,
    onDismiss: () -> Unit,
    /** When set, the dialog also asks what time the food was eaten (diary logging). */
    initialTime: LocalTime? = null,
    onConfirm: (Amount) -> Unit,
) {
    // null = grams
    var portion by remember {
        mutableStateOf(if (preferPortion || !food.weightKnown) food.defaultPortion() else null)
    }
    val blocked = !food.weightKnown && !allowUnknownWeight
    var gramsText by remember { mutableStateOf("") }
    var quantityText by remember { mutableStateOf("1") }
    var time by remember { mutableStateOf(initialTime) }
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(portion == null) { if (portion == null) focusRequester.requestFocus() }

    val amount: Amount? = portion.let { p ->
        if (p == null) {
            parseNumber(gramsText)?.takeIf { it > 0 }?.let { Amount(it) }
        } else {
            parseNumber(quantityText)?.takeIf { it > 0 }?.let { q ->
                Amount(q * p.grams, DiaryEntry.measureLabel(q, p.description), q, p.description)
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(food.name, style = MaterialTheme.typography.titleMedium) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                if (food.portions.isNotEmpty()) {
                    Text("Measure", style = MaterialTheme.typography.labelLarge)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (food.weightKnown) {
                            FilterChip(
                                selected = portion == null,
                                onClick = { portion = null },
                                label = { Text("grams") },
                            )
                        }
                        food.portions.forEach { p ->
                            FilterChip(
                                selected = portion == p,
                                onClick = { portion = p },
                                label = { Text(portionChipLabel(p)) },
                            )
                        }
                    }
                }
                val p = portion
                if (p == null) {
                    OutlinedTextField(
                        value = gramsText,
                        onValueChange = { v -> gramsText = v.filter { it.isDigit() || it == '.' || it == ',' } },
                        label = { Text("Weight") },
                        suffix = { Text("g") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp)
                            .focusRequester(focusRequester),
                    )
                } else {
                    OutlinedTextField(
                        value = quantityText,
                        onValueChange = { v -> quantityText = v.filter { it.isDigit() || it == '.' || it == ',' } },
                        label = { Text("How many") },
                        suffix = { Text("× ${shortDescription(p)}") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                    )
                }
                if (blocked) {
                    Text(
                        "${food.source} doesn't publish the weight of this item, so it can be logged to " +
                            "your diary but not used as a recipe ingredient.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                } else if (amount != null) {
                    val nutrients = food.nutrientsFor(amount.grams)
                    if (food.weightKnown) {
                        Text(
                            "= ${formatGrams(amount.grams)}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                    Text(
                        macroSummary(nutrients),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                } else {
                    Text(
                        (if (food.weightKnown) "Per 100 g: " else "Per item: ") + macroSummary(food.per100g),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
                time?.let { t -> TimeRow(t, onChange = { time = it }, modifier = Modifier.padding(top = 4.dp)) }
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
            TextButton(enabled = amount != null && !blocked, onClick = { amount?.let { onConfirm(it.copy(time = time)) } }) { Text(confirmLabel) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** "medium (7" to 7-7/8" long)" -> "medium" */
private fun shortDescription(p: Portion): String = p.description.substringBefore(" (").ifBlank { p.description }

private fun portionChipLabel(p: Portion): String = "${shortDescription(p)} · ${formatGrams(p.grams)}"
