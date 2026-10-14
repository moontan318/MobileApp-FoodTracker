package com.mealmacros.core

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.TextStyle
import java.time.temporal.TemporalAdjusters
import java.util.Locale

/** Edits applied to diary entries. */
object DiaryEdits {

    /** Total weight of [entries], or null if any of them has no known weight. */
    fun totalGrams(entries: List<DiaryEntry>): Double? =
        if (entries.isNotEmpty() && entries.all { it.weightKnown }) entries.sumOf { it.grams } else null

    /**
     * Scales a group so that only [consumedGrams] of it was eaten, keeping each item's share.
     * E.g. 2 sausages (134 g) + 100 g chips + 200 g beans = 434 g; eating 200 g of that keeps
     * 200/434 of every item. Returns null when the group's weight isn't known.
     */
    fun scaleGroupToWeight(entries: List<DiaryEntry>, consumedGrams: Double): List<DiaryEntry>? {
        val total = totalGrams(entries) ?: return null
        if (total <= 0.0 || consumedGrams < 0.0) return null
        return entries.map { it.scaled(consumedGrams / total) }
    }

    /** Scales every entry in a group by the same fraction (0.5 = half of everything). */
    fun scaleGroup(entries: List<DiaryEntry>, fraction: Double): List<DiaryEntry> = entries.map { it.scaled(fraction) }

    /** Moves [entry] to [time] on the same day. */
    fun atTime(entry: DiaryEntry, time: LocalTime, zone: ZoneId = ZoneId.systemDefault()): DiaryEntry {
        val day = Instant.ofEpochMilli(entry.timestamp).atZone(zone).toLocalDate()
        return entry.copy(timestamp = day.atTime(time).atZone(zone).toInstant().toEpochMilli())
    }
}

enum class StatsRange(val title: String) {
    DAY("Day"),
    WEEK("Week"),
    MONTH("Month"),
    YEAR("Year"),
}

/** One bar: [value] is a total (day/week/month views) or a daily average (year view). */
data class StatsBucket(val label: String, val detail: String, val value: Double)

data class StatsSeries(
    val nutrient: Nutrient,
    val range: StatsRange,
    val buckets: List<StatsBucket>,
    /** Total over the whole period. */
    val total: Double,
    /** Days in the period with at least one diary entry. */
    val daysLogged: Int,
) {
    /** Average per logged day (unlogged days would otherwise drag it down). */
    val dailyAverage: Double get() = if (daysLogged > 0) total / daysLogged else 0.0

    /** Whether bar values are amounts per day, so the daily value can be drawn as a reference. */
    val barsArePerDay: Boolean get() = range != StatsRange.DAY
}

/** Aggregates diary entries for the dashboard charts. */
object DiaryStats {

    /** First and last day of the period containing [anchor]. Weeks start on Monday. */
    fun period(range: StatsRange, anchor: LocalDate): Pair<LocalDate, LocalDate> = when (range) {
        StatsRange.DAY -> anchor to anchor
        StatsRange.WEEK -> {
            val start = anchor.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            start to start.plusDays(6)
        }
        StatsRange.MONTH -> anchor.withDayOfMonth(1) to anchor.withDayOfMonth(anchor.lengthOfMonth())
        StatsRange.YEAR -> anchor.withDayOfYear(1) to anchor.withDayOfYear(anchor.lengthOfYear())
    }

    /** The anchor of the previous (-1) or next (+1) period. */
    fun shift(range: StatsRange, anchor: LocalDate, steps: Long): LocalDate = when (range) {
        StatsRange.DAY -> anchor.plusDays(steps)
        StatsRange.WEEK -> anchor.plusWeeks(steps)
        StatsRange.MONTH -> anchor.plusMonths(steps)
        StatsRange.YEAR -> anchor.plusYears(steps)
    }

    fun series(
        entries: List<DiaryEntry>,
        nutrient: Nutrient,
        range: StatsRange,
        anchor: LocalDate,
        zone: ZoneId = ZoneId.systemDefault(),
        locale: Locale = Locale.getDefault(),
    ): StatsSeries {
        val (start, end) = period(range, anchor)
        val inPeriod = entries.mapNotNull { e ->
            val at = Instant.ofEpochMilli(e.timestamp).atZone(zone)
            if (at.toLocalDate() < start || at.toLocalDate() > end) null else at to e.nutrients.amount(nutrient)
        }
        val byDay = inPeriod.groupBy({ it.first.toLocalDate() }, { it.second }).mapValues { it.value.sum() }

        val buckets = when (range) {
            StatsRange.DAY -> {
                val byHour = inPeriod.groupBy({ it.first.hour }, { it.second }).mapValues { it.value.sum() }
                (0..23).map { h ->
                    StatsBucket("%02d".format(h), "%02d:00–%02d:59".format(h, h), byHour[h] ?: 0.0)
                }
            }
            StatsRange.WEEK, StatsRange.MONTH -> generateSequence(start) { it.plusDays(1) }
                .takeWhile { it <= end }
                .map { d ->
                    val label = if (range == StatsRange.WEEK) {
                        d.dayOfWeek.getDisplayName(TextStyle.SHORT, locale)
                    } else {
                        d.dayOfMonth.toString()
                    }
                    StatsBucket(label, "${d.dayOfWeek.getDisplayName(TextStyle.SHORT, locale)} ${d.dayOfMonth} " +
                        d.month.getDisplayName(TextStyle.SHORT, locale), byDay[d] ?: 0.0)
                }
                .toList()
            StatsRange.YEAR -> (1..12).map { m ->
                val days = byDay.filterKeys { it.monthValue == m }
                val average = if (days.isEmpty()) 0.0 else days.values.sum() / days.size
                val month = start.withMonth(m).month
                StatsBucket(
                    month.getDisplayName(TextStyle.SHORT, locale),
                    "${month.getDisplayName(TextStyle.FULL, locale)} (daily average, ${days.size} days logged)",
                    average,
                )
            }
        }
        return StatsSeries(nutrient, range, buckets, inPeriod.sumOf { it.second }, byDay.size)
    }
}
