package com.crylo.counter.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.state.getAppWidgetState
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import com.crylo.counter.MainActivity
import com.crylo.counter.R
import com.crylo.counter.app
import com.crylo.counter.data.Counter
import java.text.NumberFormat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf

/** Which counter a widget shows, stored per widget in Glance's own state. */
val CounterIdKey = longPreferencesKey("counter_id")
private val CounterIdParam = ActionParameters.Key<Long>("counter_id")

class CounterWidget : GlanceAppWidget() {
    override val stateDefinition = PreferencesGlanceStateDefinition

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val repo = context.app.repository
        // Read the counter before composing so the first frame already shows the number.
        val firstId = getAppWidgetState(context, PreferencesGlanceStateDefinition, id)[CounterIdKey]
        val firstCounter = firstId?.let { repo.counterNow(it) }
        provideContent {
            val counterId = currentState<Preferences>()[CounterIdKey]
            val flow = remember(counterId) { counterId?.let(repo::counter) ?: flowOf(null) }
            val counter by flow.collectAsState(if (counterId == firstId) firstCounter else null)
            GlanceTheme { Content(context, counterId, counter) }
        }
    }

    @Composable
    private fun Content(context: Context, counterId: Long?, counter: Counter?) {
        val open = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            .apply { counter?.let { putExtra(MainActivity.EXTRA_COUNTER_ID, it.id) } }
        Column(
            GlanceModifier
                .fillMaxSize()
                .background(GlanceTheme.colors.widgetBackground)
                .cornerRadius(24.dp)
                .padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (counter == null) {
                Box(GlanceModifier.fillMaxSize().clickable(actionStartActivity(open)), contentAlignment = Alignment.Center) {
                    Text(
                        context.getString(if (counterId == null) R.string.widget_choose else R.string.widget_missing),
                        style = TextStyle(color = GlanceTheme.colors.onSurface, textAlign = TextAlign.Center),
                    )
                }
                return@Column
            }
            Text(
                counter.name,
                maxLines = 1,
                style = TextStyle(
                    color = GlanceTheme.colors.onSurfaceVariant,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center,
                ),
                modifier = GlanceModifier.fillMaxWidth().padding(vertical = 4.dp).clickable(actionStartActivity(open)),
            )
            Spacer(GlanceModifier.height(4.dp))
            Box(
                GlanceModifier
                    .fillMaxSize()
                    .cornerRadius(18.dp)
                    .background(GlanceTheme.colors.primaryContainer)
                    .clickable(actionRunCallback<IncrementAction>(actionParametersOf(CounterIdParam to counter.id))),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        NumberFormat.getIntegerInstance().format(counter.count),
                        maxLines = 1,
                        style = TextStyle(
                            color = GlanceTheme.colors.onPrimaryContainer,
                            fontSize = 36.sp,
                            fontWeight = FontWeight.Bold,
                        ),
                    )
                    counter.target?.let { target ->
                        val rounds = context.resources.getQuantityString(R.plurals.rounds, counter.rounds, counter.rounds)
                        Text(
                            context.getString(R.string.widget_target, target, rounds),
                            maxLines = 1,
                            style = TextStyle(color = GlanceTheme.colors.onPrimaryContainer, fontSize = 12.sp),
                        )
                    }
                }
            }
        }
    }
}

/**
 * A widget tap counts through the same repository as the app, so it gets the same
 * write-through storage, history and target buzz. An "ask" counter just keeps counting
 * here, since a widget can't show a dialog.
 */
class IncrementAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val id = parameters[CounterIdParam] ?: return
        val app = context.app
        val settings = app.settings.settings.first()
        if (settings.hapticOnTap) app.feedback.tap()
        val result = app.repository.increment(id)
        if (result?.second?.targetReached == true) app.feedback.target(settings.soundOnTarget)
        CounterWidget().updateAll(context)
    }
}

class CounterWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = CounterWidget()
}
