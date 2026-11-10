package com.crylo.counter.widget

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ListItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.crylo.counter.R
import com.crylo.counter.app
import com.crylo.counter.ui.CounterTheme
import com.crylo.counter.ui.accentOf
import java.text.NumberFormat
import kotlinx.coroutines.launch

/** Shown when a widget is added (or reconfigured) to pick which counter it shows. */
class WidgetConfigActivity : ComponentActivity() {
    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val widgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        val result = Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
        // Backing out without a choice must not leave an empty widget behind.
        setResult(RESULT_CANCELED, result)
        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }
        val repo = app.repository
        setContent {
            val counters by repo.counters.collectAsStateWithLifecycle(emptyList())
            val scope = rememberCoroutineScope()
            CounterTheme(amoledBlack = false) {
                Scaffold(topBar = { TopAppBar(title = { Text(stringResource(R.string.widget_choose)) }) }) { padding ->
                    LazyColumn(contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding())) {
                        items(counters, key = { it.id }) { counter ->
                            ListItem(
                                headlineContent = { Text(counter.name) },
                                leadingContent = {
                                    Box(Modifier.size(16.dp).background(accentOf(counter.color), CircleShape))
                                },
                                trailingContent = { Text(NumberFormat.getIntegerInstance().format(counter.count)) },
                                modifier = Modifier.clickable {
                                    scope.launch {
                                        val glanceId = GlanceAppWidgetManager(this@WidgetConfigActivity).getGlanceIdBy(widgetId)
                                        updateAppWidgetState(this@WidgetConfigActivity, glanceId) { it[CounterIdKey] = counter.id }
                                        CounterWidget().update(this@WidgetConfigActivity, glanceId)
                                        setResult(RESULT_OK, result)
                                        finish()
                                    }
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}
