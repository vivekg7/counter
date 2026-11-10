package com.crylo.counter.core

/** What one counter counted on one local calendar day. */
data class DailyTotal(val epochDay: Long, val total: Long, val rounds: Int)

/** Add a change's deltas to a day's row; totals never go below zero. */
fun DailyTotal?.plus(epochDay: Long, countDelta: Long, roundsDelta: Int): DailyTotal {
    val total = ((this?.total ?: 0L) + countDelta).coerceAtLeast(0)
    val rounds = ((this?.rounds ?: 0) + roundsDelta).coerceAtLeast(0)
    return DailyTotal(epochDay, total, rounds)
}

data class HistorySummary(
    val today: Long,
    val last7Days: Long,
    val last30Days: Long,
    val allTime: Long,
    val allTimeRounds: Int,
    val bestDay: DailyTotal?,
    /** Consecutive days with a non-zero total, ending today (or yesterday, if today is empty). */
    val streak: Int,
    val activeDays: Int,
)

object HistoryStats {
    fun summarize(entries: List<DailyTotal>, today: Long): HistorySummary {
        val active = entries.filter { it.total > 0 }
        fun since(days: Int) = active.filter { it.epochDay in (today - days + 1)..today }.sumOf { it.total }
        return HistorySummary(
            today = active.firstOrNull { it.epochDay == today }?.total ?: 0,
            last7Days = since(7),
            last30Days = since(30),
            allTime = active.sumOf { it.total },
            allTimeRounds = entries.sumOf { it.rounds },
            bestDay = active.maxWithOrNull(compareBy<DailyTotal> { it.total }.thenBy { it.epochDay }),
            streak = streak(active.map { it.epochDay }.toSet(), today),
            activeDays = active.size,
        )
    }

    /** The [count] days ending at [today], oldest first, with empty days filled in as zero. */
    fun lastDays(entries: List<DailyTotal>, today: Long, count: Int): List<DailyTotal> {
        val byDay = entries.associateBy { it.epochDay }
        return (today - count + 1..today).map { day -> byDay[day] ?: DailyTotal(day, 0, 0) }
    }

    private fun streak(days: Set<Long>, today: Long): Int {
        var day = if (today in days) today else today - 1
        var n = 0
        while (day in days) {
            n++
            day--
        }
        return n
    }
}
