package com.crylo.counter

import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.crylo.counter.ui.CounterNav
import com.crylo.counter.ui.CounterTheme
import com.crylo.counter.ui.MainViewModel

class MainActivity : ComponentActivity() {
    /** Set by the counting screen while volume-key counting is on; null otherwise. */
    var volumeKeyHandler: (() -> Unit)? = null

    /** A counter to open, from a widget tap. */
    private val openRequest = mutableStateOf<Long?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) openRequest.value = intent.counterId()
        setContent {
            val vm: MainViewModel = viewModel { MainViewModel(app) }
            val settings by vm.settings.collectAsStateWithLifecycle()
            val s = settings ?: return@setContent
            CounterTheme(amoledBlack = s.amoledBlack) {
                CounterNav(vm, s, openRequest)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.counterId()?.let { openRequest.value = it }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        val handler = volumeKeyHandler
        if (handler != null && keyCode.isVolumeKey()) {
            // Holding the key auto-repeats; only a deliberate press counts.
            if (event.repeatCount == 0) handler()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        if (volumeKeyHandler != null && keyCode.isVolumeKey()) return true
        return super.onKeyUp(keyCode, event)
    }

    private fun Int.isVolumeKey() = this == KeyEvent.KEYCODE_VOLUME_UP || this == KeyEvent.KEYCODE_VOLUME_DOWN

    companion object {
        const val EXTRA_COUNTER_ID = "com.crylo.counter.COUNTER_ID"

        private fun Intent.counterId(): Long? = getLongExtra(EXTRA_COUNTER_ID, -1).takeIf { it > 0 }
    }
}
