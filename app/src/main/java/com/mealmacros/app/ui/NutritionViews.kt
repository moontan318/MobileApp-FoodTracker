package com.mealmacros.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mealmacros.core.Nutrient
import com.mealmacros.core.NutrientCategory
import com.mealmacros.core.NutrientProfile
import com.mealmacros.core.NutritionCalculator
import java.util.Locale

/** Energy, the macro split bar and protein / carbs / fat totals. */
@Composable
fun EnergyMacroCard(profile: NutrientProfile, modifier: Modifier = Modifier, title: String? = null) {
    val kcal = profile.amount(Nutrient.ENERGY)
    val split = profile.macroEnergySplit()
    Card(modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            if (title != null) {
                Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(4.dp))
            }
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    String.format(Locale.getDefault(), "%.0f", kcal),
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold,
                )
                Text(" kcal", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 6.dp))
                Spacer(Modifier.weight(1f))
                Text(
                    String.format(Locale.getDefault(), "%.0f kJ", NutritionCalculator.kcalToKj(kcal)),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 6.dp),
                )
            }
            Spacer(Modifier.height(12.dp))
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(12.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(MaterialTheme.colorScheme.outlineVariant)
            ) {
                if (split.protein > 0) Box(Modifier.weight(split.protein.toFloat()).fillMaxHeight().background(MacroColors.protein))
                if (split.carbs > 0) Box(Modifier.weight(split.carbs.toFloat()).fillMaxHeight().background(MacroColors.carbs))
                if (split.fat > 0) Box(Modifier.weight(split.fat.toFloat()).fillMaxHeight().background(MacroColors.fat))
            }
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                MacroStat("Protein", profile, Nutrient.PROTEIN, split.protein, MacroColors.protein)
                MacroStat("Carbs", profile, Nutrient.CARBS, split.carbs, MacroColors.carbs)
                MacroStat("Fat", profile, Nutrient.FAT, split.fat, MacroColors.fat)
            }
        }
    }
}

@Composable
private fun MacroStat(label: String, profile: NutrientProfile, nutrient: Nutrient, share: Double, color: Color) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(10.dp).clip(CircleShape).background(color))
            Spacer(Modifier.width(6.dp))
            Text(label, style = MaterialTheme.typography.labelLarge)
        }
        Text(formatNutrient(profile, nutrient), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        Text(
            String.format(Locale.getDefault(), "%.0f%% of energy", share * 100),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** A table of every nutrient in [category] with amount and % daily value. */
@Composable
fun NutrientTable(profile: NutrientProfile, category: NutrientCategory, modifier: Modifier = Modifier) {
    Card(modifier.fillMaxWidth()) {
        Column(Modifier.padding(vertical = 12.dp)) {
            Row(Modifier.padding(horizontal = 16.dp)) {
                Text(category.title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Text("% DV", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(4.dp))
            Nutrient.inCategory(category).forEachIndexed { i, nutrient ->
                if (i > 0 && !nutrient.indent) HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                NutrientRow(profile, nutrient)
            }
        }
    }
}

@Composable
private fun NutrientRow(profile: NutrientProfile, nutrient: Nutrient) {
    val known = profile.isKnown(nutrient)
    val dv = if (known) NutritionCalculator.percentDailyValue(nutrient, profile.amount(nutrient)) else null
    Column(
        Modifier
            .fillMaxWidth()
            .padding(start = if (nutrient.indent) 32.dp else 16.dp, end = 16.dp, top = 6.dp, bottom = 6.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                nutrient.label,
                style = if (nutrient.indent) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.bodyLarge,
                color = if (nutrient.indent) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            Text(
                formatNutrient(profile, nutrient),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
            )
            Text(
                dv?.let { String.format(Locale.getDefault(), "%.0f%%", it) } ?: "",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.width(56.dp).padding(start = 8.dp),
                maxLines = 1,
            )
        }
        if (dv != null) {
            Spacer(Modifier.height(4.dp))
            val fraction = (dv / 100.0).toFloat().coerceIn(0f, 1f)
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(MaterialTheme.colorScheme.outlineVariant)
            ) {
                if (fraction > 0f) {
                    Box(
                        Modifier
                            .fillMaxWidth(fraction)
                            .fillMaxHeight()
                            .background(MaterialTheme.colorScheme.primary)
                    )
                }
            }
        }
    }
}

/** Full breakdown: energy & macros card plus macro, vitamin and mineral tables. */
@Composable
fun NutritionDetails(profile: NutrientProfile, modifier: Modifier = Modifier, title: String? = null) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        EnergyMacroCard(profile, title = title)
        NutrientTable(profile, NutrientCategory.MACRO)
        NutrientTable(profile, NutrientCategory.VITAMIN)
        NutrientTable(profile, NutrientCategory.MINERAL)
        NutritionFootnote(showIncomplete = profile.incomplete.isNotEmpty())
    }
}

@Composable
fun NutritionFootnote(showIncomplete: Boolean) {
    val lines = buildList {
        add("% DV = percentage of the adult daily value (based on a 2,000 kcal diet).")
        if (showIncomplete) add("≥ some ingredients have no data for this nutrient, so the true amount may be higher.")
        add("– no data available.")
        add("Food data: USDA National Nutrient Database for Standard Reference (SR28).")
    }
    Text(
        lines.joinToString("\n"),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 4.dp),
    )
}
