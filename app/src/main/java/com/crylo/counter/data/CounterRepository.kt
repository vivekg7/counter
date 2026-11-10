package com.crylo.counter.data

import androidx.room.withTransaction
import com.crylo.counter.core.Change
import com.crylo.counter.core.CounterEngine
import com.crylo.counter.core.CounterState
import com.crylo.counter.core.DailyTotal
import com.crylo.counter.core.plus
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Every change is written straight to the database (write-through): there is no
 * in-memory count that could be lost if Android kills the process. Each tap is one
 * transaction that updates the counter and today's history row together, and a mutex
 * serialises taps so two quick ones (or a widget tap racing the app) can't both read
 * the same old count.
 */
class CounterRepository(
    private val db: CounterDatabase,
    private val today: () -> Long = { LocalDate.now().toEpochDay() },
) {
    private val dao = db.dao()
    private val mutex = Mutex()

    val counters: Flow<List<Counter>> = dao.observeCounters()

    fun counter(id: Long): Flow<Counter?> = dao.observeCounter(id)

    suspend fun counterNow(id: Long): Counter? = dao.counter(id)

    fun history(id: Long): Flow<List<DailyTotal>> = dao.observeDaily(id).map { rows -> rows.map { it.toDaily() } }

    suspend fun increment(id: Long): Pair<Counter, Change>? = apply(id, CounterEngine::increment)

    suspend fun decrement(id: Long): Pair<Counter, Change>? = apply(id, CounterEngine::decrement)

    suspend fun newRound(id: Long): Pair<Counter, Change>? = apply(id, CounterEngine::newRound)

    /** Returns the counter as it was, so the caller can offer undo. */
    suspend fun reset(id: Long): Counter? = mutex.withLock {
        db.withTransaction {
            val before = dao.counter(id) ?: return@withTransaction null
            dao.update(before.with(CounterEngine.reset(before.state).state))
            before
        }
    }

    suspend fun restore(id: Long, count: Long, rounds: Int) = mutex.withLock {
        db.withTransaction {
            dao.counter(id)?.let { dao.update(it.copy(count = count, rounds = rounds)) }
        }
    }

    /** Saves the editable settings of a counter; the live count is never overwritten. */
    suspend fun save(edited: Counter): Long = mutex.withLock {
        db.withTransaction {
            val existing = if (edited.id == 0L) null else dao.counter(edited.id)
            if (existing == null) {
                dao.insert(edited.copy(id = 0, count = 0, rounds = 0, position = dao.nextPosition()))
            } else {
                dao.update(
                    existing.copy(
                        name = edited.name,
                        color = edited.color,
                        step = edited.step,
                        target = edited.target,
                        targetAction = edited.targetAction,
                    ),
                )
                existing.id
            }
        }
    }

    suspend fun delete(id: Long) = mutex.withLock { dao.delete(id) }

    private suspend fun apply(id: Long, op: (CounterState) -> Change): Pair<Counter, Change>? = mutex.withLock {
        db.withTransaction {
            val counter = dao.counter(id) ?: return@withTransaction null
            val change = op(counter.state)
            val updated = counter.with(change.state)
            dao.update(updated)
            if (change.countDelta != 0L || change.roundsDelta != 0) {
                val day = today()
                val row = dao.daily(id, day)?.toDaily().plus(day, change.countDelta, change.roundsDelta)
                dao.upsertDaily(DailyRow(id, day, row.total, row.rounds))
            }
            updated to change
        }
    }
}
