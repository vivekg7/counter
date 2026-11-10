package com.crylo.counter

import android.app.Application
import androidx.glance.appwidget.updateAll
import com.crylo.counter.data.CounterDatabase
import com.crylo.counter.data.CounterRepository
import com.crylo.counter.data.SettingsStore
import com.crylo.counter.widget.CounterWidget
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch

class CounterApp : Application() {
    /**
     * Taps are written from this scope rather than a screen's, so leaving the screen (or
     * the activity being destroyed) mid-write never cancels a count.
     */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val repository by lazy { CounterRepository(CounterDatabase.build(this)) }
    val settings by lazy { SettingsStore(this) }
    val feedback by lazy { Feedback(this) }

    @OptIn(FlowPreview::class)
    override fun onCreate() {
        super.onCreate()
        // Keep home-screen widgets in step with counting done in the app. Debounced so a
        // burst of taps redraws the widget once, not once per tap.
        appScope.launch {
            repository.counters.drop(1).debounce(700).collect {
                runCatching { CounterWidget().updateAll(this@CounterApp) }
            }
        }
    }
}

val android.content.Context.app get() = applicationContext as CounterApp
