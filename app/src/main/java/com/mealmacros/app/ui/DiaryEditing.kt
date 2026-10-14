package com.mealmacros.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mealmacros.app.formatPlain
import com.mealmacros.app.parseNumber
import com.mealmacros.core.DiaryEdits
import com.mealmacros.core.DiaryEntry
import com.mealmacros.core.Nutrient
import com.mealmacros.core.NutrientProfile
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId

fun entryTime(entry: DiaryEntry): LocalTime =
    Instant.ofEpochMilli(entry.timestamp).atZone(ZoneId.systemDefault()).toLocalTime()

/** "1 × medium · 118 g" style description of how much an entry is. */
fun entryAmount(entry: DiaryEntry): String =
    listOfNotNull(entry.amountLabel, entry.grams.takeIf { it > 0 }?.let(::formatGrams)).joinToString(" · ")
        .ifEmpty { "1 item" }

private fun numberFilter(v: String) = v.filter { it.isDigit() || it == '.' || it == ',' }

/**
 * Edits one diary entry: how much was eaten (as a number of the measure it was logged with,
 * a weight, or a percentage) and when. Nutrients are rescaled to match.
 */
@Composable
fun EditEntryDialog(
    entry: DiaryEntry,
    onDismiss: () -> Unit,
    onSave: (DiaryEntry) -> Unit,
    onDelete: () -> Unit,
) {
    val measure = entry.measure
    val initial = when {
        measure != null -> DiaryEntry.formatQuantity(measure.first)
        entry.weightKnown -> formatPlain(Math.round(entry.grams * 10) / 10.0)
        else -> "100"
    }
    var text by remember { mutableStateOf(initial) }
    var time by remember { mutableStateOf(entryTime(entry)) }
    var confirmDelete by remember { mutableStateOf(false) }
    val value = parseNumber(text)?.takeIf { it > 0 }
    val factor = value?.let {
        when {
            measure != null -> it / measure.first
            entry.weightKnown -> it / entry.grams
            else -> it / 100.0
        }
    }
    val edited = factor?.let { DiaryEdits.atTime(entry.scaled(it), time) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(entry.name, style = MaterialTheme.typography.titleMedium, maxLines = 3, overflow = TextOverflow.Ellipsis) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text("Logged: ${entryAmount(entry)}", style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = numberFilter(it) },
                    label = {
                        Text(
                            when {
                                measure != null -> "How many"
                                entry.weightKnown -> "Weight eaten"
                                else -> "Amount eaten"
                            }
                        )
                    },
                    suffix = {
                        Text(
                            when {
                                measure != null -> "× ${measure.second.substringBefore(" (")}"
                                entry.weightKnown -> "g"
                                else -> "%"
                            }
                        )
                    },
                    singleLine = true,
                    isError = value == null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                )
                if (edited != null) {
                    Text(
                        listOf(entryAmount(edited), macroSummary(edited.nutrients)).joinToString("\n"),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
                TimeRow(time, onChange = { time = it }, modifier = Modifier.padding(top = 4.dp))
                TextButton(onClick = { confirmDelete = true }) {
                    Text("Remove from diary", color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = { TextButton(enabled = edited != null, onClick = { edited?.let(onSave) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Remove “${entry.name}”?") },
            confirmButton = { TextButton(onClick = onDelete) { Text("Remove") } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}

/**
 * Scales a group of entries to what was actually eaten, either as a weight of the whole
 * group (e.g. 200 g of a 434 g plate) or a percentage. Each item keeps its share.
 */
@Composable
fun GroupAmountDialog(entries: List<DiaryEntry>, onDismiss: () -> Unit, onSave: (List<DiaryEntry>) -> Unit) {
    val total = DiaryEdits.totalGrams(entries)
    var byWeight by remember { mutableStateOf(total != null) }
    var gramsText by remember { mutableStateOf(total?.let { formatPlain(Math.round(it * 10) / 10.0) } ?: "") }
    var percentText by remember { mutableStateOf("100") }
    val result: List<DiaryEntry>? = if (byWeight) {
        parseNumber(gramsText)?.takeIf { it > 0 }?.let { DiaryEdits.scaleGroupToWeight(entries, it) }
    } else {
        parseNumber(percentText)?.takeIf { it > 0 }?.let { DiaryEdits.scaleGroup(entries, it / 100.0) }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("How much of these ${entries.size} did you eat?") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (total != null) {
                        FilterChip(selected = byWeight, onClick = { byWeight = true }, label = { Text("Weight") })
                    }
                    FilterChip(selected = !byWeight, onClick = { byWeight = false }, label = { Text("Percentage") })
                }
                if (total == null) {
                    Text(
                        "Some of these items have no published weight, so the group can only be adjusted by percentage.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (byWeight && total != null) {
                    Text("Logged in total: ${formatGrams(total)}", style = MaterialTheme.typography.bodySmall)
                    OutlinedTextField(
                        value = gramsText,
                        onValueChange = { gramsText = numberFilter(it) },
                        label = { Text("Weight eaten") },
                        suffix = { Text("g") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    OutlinedTextField(
                        value = percentText,
                        onValueChange = { percentText = numberFilter(it) },
                        label = { Text("Amount eaten") },
                        suffix = { Text("%") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (result != null) {
                    Text("New amounts", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 12.dp))
                    result.forEach { e ->
                        Text(
                            "${e.name}: ${entryAmount(e)} · ${formatKcal(e.nutrients.amount(Nutrient.ENERGY))}",
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                    HorizontalDivider(Modifier.padding(vertical = 6.dp))
                    Text(
                        "Total: ${macroSummary(NutrientProfile.sum(result.map { it.nutrients }))}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        },
        confirmButton = { TextButton(enabled = result != null, onClick = { result?.let(onSave) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
