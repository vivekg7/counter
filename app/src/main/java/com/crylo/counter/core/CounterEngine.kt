package com.crylo.counter.core

/** What happens when a counter reaches its target. */
enum class TargetAction {
    /** Stop and ask: keep counting past the target, or start a new round from zero. */
    ASK,

    /** The tap after the target starts the next round from zero automatically. */
    NEW_ROUND,

    /** Never reset; the target fires again at every multiple (33, 66, 99, ...). */
    CONTINUE,
}

/** The part of a counter that the counting rules care about. */
data class CounterState(
    val count: Long,
    val rounds: Int,
    val step: Int = 1,
    val target: Int? = null,
    val targetAction: TargetAction = TargetAction.ASK,
)

/**
 * The result of one operation: the new state plus the deltas the daily history must
 * record. History stores what was counted each day, so a reset changes the counter but
 * never the log, while a minus tap takes back what the matching plus tap added.
 */
data class Change(
    val state: CounterState,
    val countDelta: Long,
    val roundsDelta: Int,
    val targetReached: Boolean,
)

/**
 * The counting rules, kept free of Android so they are proven by JVM tests.
 *
 * A round is completed each time the count crosses a multiple of the target. Minus is the
 * exact inverse of plus: stepping back over a multiple takes the round back too, so
 * wobbling around the target with plus/minus cannot inflate the round count.
 */
object CounterEngine {
    const val MAX_STEP = 1000
    const val MAX_TARGET = 1_000_000

    fun increment(s: CounterState): Change {
        val target = s.target?.takeIf { it > 0 }
        val step = s.step.coerceIn(1, MAX_STEP)
        // Auto new round: once the previous tap reached the target, this one starts over.
        // The target number stays on screen until the next tap, which is what people
        // expect from a hand tally (see 33, feel the buzz, keep going from 1).
        val base =
            if (target != null && s.targetAction == TargetAction.NEW_ROUND && s.count >= target) {
                0L
            } else {
                s.count
            }
        val newCount = base + step
        val crossed = if (target == null) 0 else (newCount / target - base / target).toInt()
        return Change(
            state = s.copy(count = newCount, rounds = s.rounds + crossed),
            countDelta = step.toLong(),
            roundsDelta = crossed,
            targetReached = crossed > 0,
        )
    }

    fun decrement(s: CounterState): Change {
        val target = s.target?.takeIf { it > 0 }
        val step = s.step.coerceIn(1, MAX_STEP)
        val newCount = (s.count - step).coerceAtLeast(0)
        val uncrossed = if (target == null) 0 else (s.count / target - newCount / target).toInt()
        val newRounds = (s.rounds - uncrossed).coerceAtLeast(0)
        return Change(
            state = s.copy(count = newCount, rounds = newRounds),
            countDelta = newCount - s.count,
            roundsDelta = newRounds - s.rounds,
            targetReached = false,
        )
    }

    /** Start the next round: the completed round was already counted when it was reached. */
    fun newRound(s: CounterState): Change = Change(s.copy(count = 0), 0, 0, false)

    /** Back to zero count and zero rounds. History keeps what was done. */
    fun reset(s: CounterState): Change = Change(s.copy(count = 0, rounds = 0), 0, 0, false)

    /**
     * How far the current round is, 0..1, or null without a target. A count sitting exactly
     * on a multiple of the target shows a full ring rather than an empty one.
     */
    fun progress(s: CounterState): Float? {
        val target = s.target?.takeIf { it > 0 } ?: return null
        if (s.count <= 0) return 0f
        val inRound = s.count % target
        return if (inRound == 0L) 1f else inRound.toFloat() / target
    }
}
