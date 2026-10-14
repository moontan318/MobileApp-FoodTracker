package com.mealmacros.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.mealmacros.app.AppViewModel
import com.mealmacros.core.DiaryStats
import com.mealmacros.core.Nutrient
import com.mealmacros.core.StatsRange
import com.mealmacros.core.StatsSeries
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.util.Locale

/** Nutrients offered first; the rest are behind "More". */
private val MAIN_NUTRIENTS = listOf(
    Nutrient.ENERGY, Nutrient.PROTEIN, Nutrient.CARBS, Nutrient.FAT, Nutrient.FIBER, Nutrient.SUGARS,
    Nutrient.SATURATED_FAT, Nutrient.SODIUM,
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DashboardTab(vm: AppViewModel) {
    val range = vm.statsRange
    val anchor = LocalDate.ofEpochDay(vm.statsAnchor)
    var showAll by rememberSaveable { mutableStateOf(false) }
    val diary = vm.diary

    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 0.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatsRange.entries.forEach { r ->
                    FilterChip(
                        selected = r == range,
                        onClick = { vm.statsRange = r },
                        label = { Text(r.title) },
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { vm.statsAnchor = DiaryStats.shift(range, anchor, -1).toEpochDay() }) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Previous ${range.title.lowercase()}")
                }
                Text(
                    periodTitle(range, anchor),
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { vm.statsAnchor = DiaryStats.shift(range, anchor, 1).toEpochDay() }) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Next ${range.title.lowercase()}")
                }
            }
        }
        item {
            Text("Show", style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val shown = if (showAll) Nutrient.entries else MAIN_NUTRIENTS
                shown.forEach { n ->
                    val on = n in vm.statsNutrients
                    FilterChip(
                        selected = on,
                        onClick = {
                            vm.statsNutrients = if (on) vm.statsNutrients - n else vm.statsNutrients + n
                        },
                        label = { Text(n.label) },
                    )
                }
                TextButton(onClick = { showAll = !showAll }) {
                    Text(if (showAll) "Fewer" else "More (vitamins, minerals…)")
                }
            }
        }
        if (vm.statsNutrients.isEmpty()) {
            item {
                Text(
                    "Choose one or more nutrients to chart.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        // Keep the order nutrients are listed in, not the order they were tapped.
        val selected = Nutrient.entries.filter { it in vm.statsNutrients }
        items(selected, key = { it.key }) { nutrient ->
            val series = remember(diary, nutrient, range, anchor) { DiaryStats.series(diary, nutrient, range, anchor) }
            NutrientChartCard(series)
        }
        item {
            Text(
                when (range) {
                    StatsRange.DAY -> "Bars show the amount eaten in each hour."
                    StatsRange.WEEK, StatsRange.MONTH -> "Bars show daily totals. The dashed line is the adult daily value."
                    StatsRange.YEAR -> "Bars show the average per logged day in each month. The dashed line is the adult daily value."
                } + " Daily averages only count days with diary entries.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun NutrientChartCard(series: StatsSeries) {
    val n = series.nutrient
    val unit = n.unit.symbol
    var selected by remember(series.range, series.buckets.size) { mutableStateOf<Int?>(null) }
    var showTable by rememberSaveable(n.key) { mutableStateOf(false) }
    val dv = n.dailyValue

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text("${n.label} ($unit)", style = MaterialTheme.typography.titleMedium)
            val avg = series.dailyAverage
            val summary = buildString {
                if (series.range != StatsRange.DAY) {
                    append("Total ${formatAmount(series.total)} $unit · ")
                    append("daily average ${formatAmount(avg)} $unit")
                    if (dv != null && series.daysLogged > 0) append(" (${(avg / dv * 100).toInt()}% DV)")
                    append(" · ${series.daysLogged} days logged")
                } else {
                    append("Total ${formatAmount(series.total)} $unit")
                    if (dv != null) append(" (${(series.total / dv * 100).toInt()}% DV)")
                }
            }
            Text(summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

            if (series.total <= 0.0) {
                Text(
                    "Nothing logged in this period.",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(vertical = 24.dp),
                )
                return@Column
            }

            // Selected bar details (tap a bar); a prompt otherwise.
            val sel = selected?.let { series.buckets.getOrNull(it) }
            Text(
                if (sel != null) "${sel.detail}: ${formatAmount(sel.value)} $unit" else "Tap a bar for its value",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (sel != null) FontWeight.SemiBold else FontWeight.Normal,
                modifier = Modifier.padding(top = 8.dp),
            )
            BarChart(
                values = series.buckets.map { it.value },
                labels = series.buckets.map { it.label },
                selected = selected,
                onSelect = { selected = if (it == selected) null else it },
                reference = if (series.barsArePerDay) dv else null,
                referenceLabel = "DV ${formatAmount(dv ?: 0.0)}",
                labelEvery = when (series.range) {
                    StatsRange.DAY -> 6
                    StatsRange.MONTH -> 5
                    StatsRange.YEAR -> 2
                    StatsRange.WEEK -> 1
                },
                modifier = Modifier.padding(top = 8.dp),
            )
            TextButton(onClick = { showTable = !showTable }) { Text(if (showTable) "Hide table" else "Show as table") }
            if (showTable) {
                series.buckets.filter { it.value > 0 }.forEach { b ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                        Text(b.detail, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                        Text("${formatAmount(b.value)} $unit", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

private fun periodTitle(range: StatsRange, anchor: LocalDate): String {
    val (start, end) = DiaryStats.period(range, anchor)
    val today = LocalDate.now()
    return when (range) {
        StatsRange.DAY -> when (anchor) {
            today -> "Today"
            today.minusDays(1) -> "Yesterday"
            else -> anchor.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))
        }
        StatsRange.WEEK -> {
            val f = DateTimeFormatter.ofPattern("d MMM")
            (if (today in start..end) "This week · " else "") + "${start.format(f)} – ${end.format(f)}"
        }
        StatsRange.MONTH -> "${anchor.month.getDisplayName(TextStyle.FULL, Locale.getDefault())} ${anchor.year}"
        StatsRange.YEAR -> anchor.year.toString()
    }
}
