package com.mealmacros.app.ui

import com.mealmacros.core.Nutrient
import com.mealmacros.core.NutrientProfile
import java.util.Locale
import kotlin.math.abs

/** Rounds to a sensible number of decimals for display. */
fun formatAmount(value: Double): String {
    val a = abs(value)
    val pattern = when {
        a == 0.0 -> "%.0f"
        a < 0.1 -> "%.3f"
        a < 1 -> "%.2f"
        a < 10 -> "%.1f"
        else -> "%.0f"
    }
    return String.format(Locale.getDefault(), pattern, value)
}

fun formatGrams(value: Double): String = "${formatAmount(value)} g"

fun formatKcal(value: Double): String = String.format(Locale.getDefault(), "%.0f kcal", value)

fun formatNutrient(profile: NutrientProfile, nutrient: Nutrient): String {
    if (!profile.isKnown(nutrient)) return "–"
    val prefix = if (profile.isIncomplete(nutrient)) "≥ " else ""
    return "$prefix${formatAmount(profile.amount(nutrient))} ${nutrient.unit.symbol}"
}

/** Short one-line macro summary, e.g. "250 kcal · P 20 g · C 30 g · F 5 g". */
fun macroSummary(p: NutrientProfile): String =
    "${formatKcal(p.amount(Nutrient.ENERGY))} · P ${formatGrams(p.amount(Nutrient.PROTEIN))} · " +
        "C ${formatGrams(p.amount(Nutrient.CARBS))} · F ${formatGrams(p.amount(Nutrient.FAT))}"
