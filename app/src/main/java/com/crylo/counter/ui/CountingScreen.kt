package com.crylo.counter.ui

import androidx.activity.compose.LocalActivity
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.crylo.counter.MainActivity
import com.crylo.counter.R
import com.crylo.counter.core.CounterEngine
import com.crylo.counter.data.Counter
import com.crylo.counter.data.Settings
import java.text.NumberFormat
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CountingScreen(
    vm: MainViewModel,
    id: Long,
    settings: Settings,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onHistory: () -> Unit,
) {
    val flow = remember(id) { vm.counter(id) }
    val counter by flow.collectAsStateWithLifecycle(initialValue = null)
    val askRound by vm.askRound.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val pulse = remember { Animatable(1f) }

    LaunchedEffect(id) { vm.setLastCounter(id) }

    val view = LocalView.current
    DisposableEffect(settings.keepScreenOn) {
        view.keepScreenOn = settings.keepScreenOn
        onDispose { view.keepScreenOn = false }
    }

    val count: () -> Unit = {
        vm.increment(id)
        scope.launch {
            pulse.snapTo(0.95f)
            pulse.animateTo(1f)
        }
    }
    val currentCount by rememberUpdatedState(count)

    val activity = LocalActivity.current as? MainActivity
    DisposableEffect(settings.volumeKeys, id) {
        activity?.volumeKeyHandler = if (settings.volumeKeys) ({ currentCount() }) else null
        onDispose { activity?.volumeKeyHandler = null }
    }

    val c = counter
    val accent = accentOf(c?.color ?: 0xFF888888.toInt())
    val resetMessage = stringResource(R.string.counter_reset)
    val undoLabel = stringResource(R.string.undo)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(c?.name.orEmpty(), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                actions = {
                    IconButton(onClick = onHistory) {
                        Icon(Icons.Filled.DateRange, contentDescription = stringResource(R.string.history))
                    }
                    IconButton(onClick = onEdit) {
                        Icon(Icons.Filled.Edit, contentDescription = stringResource(R.string.edit))
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        if (c == null) return@Scaffold
        Column(Modifier.fillMaxSize().padding(padding)) {
            val description = stringResource(R.string.count_description, format(c.count), c.step)
            val tapLabel = stringResource(R.string.tap_action)
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    // Count on touch-down, like a mechanical clicker: no wait for the finger
                    // to lift, and no double-tap delay, so fast counting never drops taps.
                    .pointerInput(Unit) {
                        awaitEachGesture {
                            awaitFirstDown()
                            currentCount()
                            waitForUpOrCancellation()
                        }
                    }
                    .semantics {
                        contentDescription = description
                        onClick(label = tapLabel) {
                            currentCount()
                            true
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                CountDisplay(c, accent, Modifier.scale(pulse.value))
            }
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                FilledTonalIconButton(
                    onClick = { vm.decrement(id) },
                    modifier = Modifier.size(72.dp),
                    enabled = c.count > 0,
                    colors = IconButtonDefaults.filledTonalIconButtonColors(),
                ) {
                    Icon(Icons.Filled.Minus, contentDescription = stringResource(R.string.minus), Modifier.size(32.dp))
                }
                OutlinedButton(
                    onClick = {
                        scope.launch {
                            val before = vm.reset(id) ?: return@launch
                            snackbar.currentSnackbarData?.dismiss()
                            val result = snackbar.showSnackbar(resetMessage, undoLabel, duration = SnackbarDuration.Long)
                            if (result == SnackbarResult.ActionPerformed) vm.restore(before)
                        }
                    },
                    enabled = c.count > 0 || c.rounds > 0,
                    modifier = Modifier.height(56.dp),
                ) {
                    Icon(Icons.Filled.Refresh, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.reset))
                }
            }
        }
    }

    if (c != null && askRound == id) {
        AlertDialog(
            onDismissRequest = vm::dismissAsk,
            title = { Text(stringResource(R.string.target_reached)) },
            text = { Text(stringResource(R.string.target_reached_body, c.name, format(c.count))) },
            confirmButton = { TextButton(onClick = { vm.newRound(id) }) { Text(stringResource(R.string.new_round)) } },
            dismissButton = { TextButton(onClick = vm::dismissAsk) { Text(stringResource(R.string.keep_counting)) } },
        )
    }
}

@Composable
private fun CountDisplay(c: Counter, accent: androidx.compose.ui.graphics.Color, modifier: Modifier) {
    val progress = CounterEngine.progress(c.state)
    val animated by animateFloatAsState(progress ?: 0f, label = "progress")
    val track = MaterialTheme.colorScheme.surfaceContainerHighest
    BoxWithConstraints(modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        val diameter = minOf(maxWidth, maxHeight)
        Box(Modifier.size(diameter), contentAlignment = Alignment.Center) {
            if (progress != null) {
                Canvas(Modifier.fillMaxSize()) {
                    val stroke = size.minDimension * 0.045f
                    val inset = stroke / 2
                    val arcSize = androidx.compose.ui.geometry.Size(size.width - stroke, size.height - stroke)
                    val topLeft = androidx.compose.ui.geometry.Offset(inset, inset)
                    drawArc(track, 0f, 360f, false, topLeft, arcSize, style = Stroke(stroke))
                    drawArc(accent, -90f, 360f * animated, false, topLeft, arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                val text = format(c.count)
                val density = LocalDensity.current
                // Fit the number inside the ring: big for short counts, shrinking as digits grow.
                val fontSize = with(density) {
                    val byWidth = diameter.toPx() * 0.72f / (text.length.coerceAtLeast(2) * 0.6f)
                    minOf(diameter.toPx() * 0.36f, byWidth).toSp()
                }
                Text(
                    text,
                    fontSize = fontSize,
                    fontWeight = FontWeight.Medium,
                    color = if (progress == null) accent else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                )
                val target = c.target
                val sub = when {
                    target != null -> stringResource(R.string.of_target, target) + " · " +
                        pluralStringResource(R.plurals.rounds, c.rounds, c.rounds)
                    c.count == 0L -> stringResource(R.string.tap_to_count)
                    else -> null
                }
                if (sub != null) {
                    Text(sub, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

private fun format(n: Long): String = NumberFormat.getIntegerInstance().format(n)
