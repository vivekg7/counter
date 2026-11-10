package com.crylo.counter.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CounterEngineTest {
    private fun tap(s: CounterState, times: Int): Pair<CounterState, List<Change>> {
        var state = s
        val changes = (1..times).map { CounterEngine.increment(state).also { state = it.state } }
        return state to changes
    }

    @Test
    fun incrementAddsStepWithoutTarget() {
        val c = CounterEngine.increment(CounterState(count = 10, rounds = 0, step = 5))
        assertEquals(15, c.state.count)
        assertEquals(5, c.countDelta)
        assertEquals(0, c.roundsDelta)
        assertFalse(c.targetReached)
    }

    @Test
    fun reachingTargetCompletesRoundOnce() {
        val (state, changes) = tap(CounterState(0, 0, target = 33), 33)
        assertEquals(33, state.count)
        assertEquals(1, state.rounds)
        assertEquals(listOf(32), changes.indices.filter { changes[it].targetReached })
    }

    @Test
    fun askModeKeepsCountingAndFiresAtEveryMultiple() {
        val (state, changes) = tap(CounterState(0, 0, target = 33, targetAction = TargetAction.ASK), 70)
        assertEquals(70, state.count)
        assertEquals(2, state.rounds)
        assertEquals(2, changes.count { it.targetReached })
    }

    @Test
    fun continueModeNeverResets() {
        val (state, _) = tap(CounterState(0, 0, target = 100, targetAction = TargetAction.CONTINUE), 250)
        assertEquals(250, state.count)
        assertEquals(2, state.rounds)
    }

    @Test
    fun autoNewRoundShowsTargetThenRestartsOnNextTap() {
        val start = CounterState(0, 0, target = 33, targetAction = TargetAction.NEW_ROUND)
        val (atTarget, _) = tap(start, 33)
        assertEquals(33, atTarget.count)
        assertEquals(1, atTarget.rounds)
        val next = CounterEngine.increment(atTarget)
        assertEquals(1, next.state.count)
        assertEquals(1, next.state.rounds)
        assertFalse(next.targetReached)
        assertEquals(1, next.countDelta)
        val (afterThree, _) = tap(start, 99)
        assertEquals(33, afterThree.count)
        assertEquals(3, afterThree.rounds)
    }

    @Test
    fun stepThatOvershootsTargetStillCountsRound() {
        val c = CounterEngine.increment(CounterState(30, 0, step = 5, target = 33))
        assertEquals(35, c.state.count)
        assertTrue(c.targetReached)
        assertEquals(1, c.state.rounds)
    }

    @Test
    fun decrementIsInverseOfIncrementAcrossTarget() {
        val atTarget = CounterState(33, 1, target = 33)
        val back = CounterEngine.decrement(atTarget)
        assertEquals(32, back.state.count)
        assertEquals(0, back.state.rounds)
        assertEquals(-1, back.roundsDelta)
        assertEquals(-1, back.countDelta)
        val again = CounterEngine.increment(back.state)
        assertEquals(atTarget, again.state)
    }

    @Test
    fun decrementFloorsAtZero() {
        val c = CounterEngine.decrement(CounterState(3, 0, step = 5))
        assertEquals(0, c.state.count)
        assertEquals(-3, c.countDelta)
        val none = CounterEngine.decrement(c.state)
        assertEquals(0, none.countDelta)
    }

    @Test
    fun decrementAfterAutoRoundKeepsCompletedRound() {
        val c = CounterEngine.decrement(CounterState(1, 1, target = 33, targetAction = TargetAction.NEW_ROUND))
        assertEquals(0, c.state.count)
        assertEquals(1, c.state.rounds)
    }

    @Test
    fun resetAndNewRoundLeaveHistoryAlone() {
        val s = CounterState(40, 2, target = 33)
        val reset = CounterEngine.reset(s)
        assertEquals(CounterState(0, 0, target = 33), reset.state)
        assertEquals(0, reset.countDelta)
        assertEquals(0, reset.roundsDelta)
        val round = CounterEngine.newRound(s)
        assertEquals(0, round.state.count)
        assertEquals(2, round.state.rounds)
    }

    @Test
    fun progressWithinRound() {
        assertNull(CounterEngine.progress(CounterState(5, 0)))
        assertEquals(0f, CounterEngine.progress(CounterState(0, 0, target = 10))!!, 0f)
        assertEquals(0.5f, CounterEngine.progress(CounterState(5, 0, target = 10))!!, 1e-6f)
        assertEquals(1f, CounterEngine.progress(CounterState(20, 2, target = 10))!!, 0f)
        assertEquals(0.1f, CounterEngine.progress(CounterState(21, 2, target = 10))!!, 1e-6f)
    }

    @Test
    fun zeroTargetBehavesLikeNoTarget() {
        val c = CounterEngine.increment(CounterState(0, 0, target = 0))
        assertEquals(1, c.state.count)
        assertFalse(c.targetReached)
    }
}
