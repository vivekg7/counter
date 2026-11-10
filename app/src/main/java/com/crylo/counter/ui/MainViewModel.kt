package com.crylo.counter.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.crylo.counter.CounterApp
import com.crylo.counter.core.TargetAction
import com.crylo.counter.data.Counter
import com.crylo.counter.data.Settings
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MainViewModel(private val app: CounterApp) : ViewModel() {
    private val repo = app.repository
    private val store = app.settings

    val counters: StateFlow<List<Counter>?> =
        repo.counters.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Null until the first read, so the theme never flashes the wrong colours. */
    val settings: StateFlow<Settings?> = store.settings.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _askRound = MutableStateFlow<Long?>(null)

    /** The counter that just reached its target in "ask" mode and is waiting for a choice. */
    val askRound = _askRound.asStateFlow()

    fun counter(id: Long) = repo.counter(id)

    fun history(id: Long) = repo.history(id)

    fun increment(id: Long) {
        val s = settings.value ?: Settings()
        // The click fires before the write so it lines up with the finger, not the disk.
        if (s.hapticOnTap) app.feedback.tap()
        app.appScope.launch {
            val (counter, change) = repo.increment(id) ?: return@launch
            if (change.targetReached) {
                app.feedback.target(s.soundOnTarget)
                if (counter.targetAction == TargetAction.ASK) _askRound.value = id
            }
        }
    }

    fun decrement(id: Long) {
        if (settings.value?.hapticOnTap != false) app.feedback.tap()
        app.appScope.launch { repo.decrement(id) }
    }

    fun newRound(id: Long) {
        _askRound.value = null
        app.appScope.launch { repo.newRound(id) }
    }

    fun dismissAsk() {
        _askRound.value = null
    }

    /** Resets and returns the previous counter, for the undo snackbar. */
    suspend fun reset(id: Long): Counter? = app.appScope.async { repo.reset(id) }.await()

    fun restore(previous: Counter) {
        app.appScope.launch { repo.restore(previous.id, previous.count, previous.rounds) }
    }

    suspend fun load(id: Long): Counter? = repo.counterNow(id)

    suspend fun save(counter: Counter): Long = app.appScope.async { repo.save(counter) }.await()

    fun delete(id: Long) {
        app.appScope.launch {
            repo.delete(id)
            if (settings.value?.lastCounterId == id) store.setLastCounter(null)
        }
    }

    fun setLastCounter(id: Long) = update { store.setLastCounter(id) }

    fun setHaptic(on: Boolean) = update { store.setHaptic(on) }

    fun setSound(on: Boolean) = update { store.setSound(on) }

    fun setVolumeKeys(on: Boolean) = update { store.setVolumeKeys(on) }

    fun setKeepScreenOn(on: Boolean) = update { store.setKeepScreenOn(on) }

    fun setAmoled(on: Boolean) = update { store.setAmoled(on) }

    private fun update(block: suspend () -> Unit) {
        app.appScope.launch { block() }
    }
}
