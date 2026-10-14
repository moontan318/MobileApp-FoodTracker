package com.mealmacros.core

import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class DiaryTest {

    private val zone = ZoneOffset.UTC
    private fun at(date: String, time: String) =
        LocalDate.parse(date).atTime(LocalTime.parse(time)).toInstant(zone).toEpochMilli()

    private fun entry(id: String, grams: Double, kcal: Double, protein: Double, ts: Long = at("2026-10-13", "12:00"),
                      quantity: Double? = null, portion: String? = null) = DiaryEntry(
        id = id, name = id, grams = grams, timestamp = ts,
        nutrients = NutrientProfile(mapOf(Nutrient.ENERGY to kcal, Nutrient.PROTEIN to protein)),
        quantity = quantity, portion = portion,
        amountLabel = if (quantity != null && portion != null) DiaryEntry.measureLabel(quantity, portion) else null,
    )

    @Test
    fun twoSausagesBecomeOne() {
        val sausages = entry("sausage", 134.0, 400.0, 20.0, quantity = 2.0, portion = "sausage")
        val one = sausages.scaled(0.5)
        assertEquals(67.0, one.grams, 1e-9)
        assertEquals(200.0, one.nutrients.amount(Nutrient.ENERGY), 1e-9)
        assertEquals(1.0, one.quantity)
        assertEquals("1 × sausage", one.amountLabel)
    }

    @Test
    fun olderEntriesParseTheirLabel() {
        val old = DiaryEntry("a", "Banana", 236.0, 0L, NutrientProfile.EMPTY, amountLabel = "2 × medium (7\" long)")
        assertEquals(2.0 to "medium (7\" long)", old.measure)
        assertEquals("1.5 × medium (7\" long)", old.scaled(0.75).amountLabel)
    }

    @Test
    fun groupScaledToWeightEaten() {
        // 2 sausages (134 g) + 100 g chips + 200 g beans = 434 g; only 200 g eaten.
        val group = listOf(
            entry("sausages", 134.0, 400.0, 20.0, quantity = 2.0, portion = "sausage"),
            entry("chips", 100.0, 250.0, 3.0),
            entry("beans", 200.0, 160.0, 10.0),
        )
        assertEquals(434.0, DiaryEdits.totalGrams(group))
        val eaten = assertNotNull(DiaryEdits.scaleGroupToWeight(group, 200.0))
        assertEquals(200.0, eaten.sumOf { it.grams }, 1e-9)
        val f = 200.0 / 434.0
        assertEquals(134.0 * f, eaten[0].grams, 1e-9)
        assertEquals(100.0 * f, eaten[1].grams, 1e-9)
        assertEquals(810.0 * f, eaten.sumOf { it.nutrients.amount(Nutrient.ENERGY) }, 1e-9)
        assertEquals(2.0 * f, eaten[0].quantity!!, 1e-9)
    }

    @Test
    fun groupWithUnknownWeightNeedsAPercentage() {
        val group = listOf(entry("kfc", 0.0, 468.0, 23.0), entry("chips", 100.0, 250.0, 3.0))
        assertNull(DiaryEdits.totalGrams(group))
        assertNull(DiaryEdits.scaleGroupToWeight(group, 50.0))
        val half = DiaryEdits.scaleGroup(group, 0.5)
        assertEquals(234.0, half[0].nutrients.amount(Nutrient.ENERGY), 1e-9)
    }

    @Test
    fun changeTimeKeepsTheDay() {
        val e = entry("x", 100.0, 1.0, 1.0, ts = at("2026-10-13", "12:00"))
        assertEquals(at("2026-10-13", "08:15"), DiaryEdits.atTime(e, LocalTime.of(8, 15), zone).timestamp)
    }

    @Test
    fun statsBucketsPerRange() {
        val entries = listOf(
            entry("breakfast", 100.0, 300.0, 10.0, at("2026-10-13", "08:10")),
            entry("lunch", 100.0, 600.0, 30.0, at("2026-10-13", "12:30")),
            entry("lunch2", 100.0, 100.0, 5.0, at("2026-10-13", "12:45")),
            entry("tue", 100.0, 1000.0, 40.0, at("2026-10-14", "19:00")),
            entry("nextMonth", 100.0, 500.0, 25.0, at("2026-11-02", "12:00")),
        )
        val day = DiaryStats.series(entries, Nutrient.ENERGY, StatsRange.DAY, LocalDate.parse("2026-10-13"), zone, Locale.UK)
        assertEquals(24, day.buckets.size)
        assertEquals(300.0, day.buckets[8].value)
        assertEquals(700.0, day.buckets[12].value)
        assertEquals(1000.0, day.total)

        // 13 Oct 2026 is a Tuesday; the week runs Monday 12 to Sunday 18.
        val week = DiaryStats.series(entries, Nutrient.ENERGY, StatsRange.WEEK, LocalDate.parse("2026-10-15"), zone, Locale.UK)
        assertEquals(listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"), week.buckets.map { it.label })
        assertEquals(listOf(0.0, 1000.0, 1000.0, 0.0, 0.0, 0.0, 0.0), week.buckets.map { it.value })
        assertEquals(2, week.daysLogged)
        assertEquals(1000.0, week.dailyAverage)

        val month = DiaryStats.series(entries, Nutrient.PROTEIN, StatsRange.MONTH, LocalDate.parse("2026-10-01"), zone, Locale.UK)
        assertEquals(31, month.buckets.size)
        assertEquals(45.0, month.buckets[12].value)

        val year = DiaryStats.series(entries, Nutrient.ENERGY, StatsRange.YEAR, LocalDate.parse("2026-06-01"), zone, Locale.UK)
        assertEquals(12, year.buckets.size)
        assertEquals(1000.0, year.buckets[9].value) // October: (1000 + 1000) / 2 logged days
        assertEquals(500.0, year.buckets[10].value)
        assertEquals(3, year.daysLogged)
    }
}
