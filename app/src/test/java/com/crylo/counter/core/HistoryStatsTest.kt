package com.crylo.counter.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HistoryStatsTest {
    private val today = 20_000L

    @Test
    fun plusCreatesAndAccumulatesAndClamps() {
        val first = null.plus(today, 33, 1)
        assertEquals(DailyTotal(today, 33, 1), first)
        assertEquals(DailyTotal(today, 34, 1), first.plus(today, 1, 0))
        assertEquals(DailyTotal(today, 0, 0), first.plus(today, -50, -3))
    }

    @Test
    fun summaryWindowsAndBestDay() {
        val entries = listOf(
            DailyTotal(today, 10, 0),
            DailyTotal(today - 1, 20, 0),
            DailyTotal(today - 6, 5, 1),
            DailyTotal(today - 7, 100, 3),
            DailyTotal(today - 29, 1, 0),
            DailyTotal(today - 30, 1000, 0),
        )
        val s = HistoryStats.summarize(entries, today)
        assertEquals(10, s.today)
        assertEquals(35, s.last7Days)
        assertEquals(136, s.last30Days)
        assertEquals(1136, s.allTime)
        assertEquals(4, s.allTimeRounds)
        assertEquals(DailyTotal(today - 30, 1000, 0), s.bestDay)
        assertEquals(6, s.activeDays)
    }

    @Test
    fun emptyHistory() {
        val s = HistoryStats.summarize(emptyList(), today)
        assertEquals(0, s.today)
        assertEquals(0, s.allTime)
        assertNull(s.bestDay)
        assertEquals(0, s.streak)
    }

    @Test
    fun zeroDaysDoNotCountAsActive() {
        val s = HistoryStats.summarize(listOf(DailyTotal(today, 0, 0), DailyTotal(today - 1, 3, 0)), today)
        assertEquals(1, s.activeDays)
        assertEquals(1, s.streak)
        assertEquals(0, s.today)
    }

    @Test
    fun streakEndsTodayOrYesterday() {
        val run = (0L..4L).map { DailyTotal(today - it, 1, 0) } + DailyTotal(today - 6, 1, 0)
        assertEquals(5, HistoryStats.summarize(run, today).streak)
        val fromYesterday = run.filter { it.epochDay != today }
        assertEquals(4, HistoryStats.summarize(fromYesterday, today).streak)
        val stale = listOf(DailyTotal(today - 2, 1, 0))
        assertEquals(0, HistoryStats.summarize(stale, today).streak)
    }

    @Test
    fun bestDayTieGoesToMostRecent() {
        val s = HistoryStats.summarize(listOf(DailyTotal(today - 3, 7, 0), DailyTotal(today - 1, 7, 0)), today)
        assertEquals(today - 1, s.bestDay!!.epochDay)
    }

    @Test
    fun lastDaysFillsGapsOldestFirst() {
        val days = HistoryStats.lastDays(listOf(DailyTotal(today, 4, 0), DailyTotal(today - 2, 9, 1)), today, 4)
        assertEquals(
            listOf(
                DailyTotal(today - 3, 0, 0),
                DailyTotal(today - 2, 9, 1),
                DailyTotal(today - 1, 0, 0),
                DailyTotal(today, 4, 0),
            ),
            days,
        )
    }
}
