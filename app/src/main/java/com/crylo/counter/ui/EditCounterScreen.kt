package com.crylo.counter.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.crylo.counter.R
import com.crylo.counter.core.CounterEngine
import com.crylo.counter.core.TargetAction
import com.crylo.counter.data.Counter
import com.crylo.counter.data.CounterColors
import kotlinx.coroutines.launch

private val TargetPresets = listOf(33, 99, 100, 108)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun EditCounterScreen(vm: MainViewModel, id: Long?, onDone: (Long?) -> Unit, onDeleted: () -> Unit) {
    var loaded by remember { mutableStateOf<Counter?>(null) }
    LaunchedEffect(id) {
        loaded = id?.let { vm.load(it) } ?: Counter(name = "", color = CounterColors.random())
    }
    val initial = loaded
    val defaultName = stringResource(R.string.default_counter_name)
    val scope = rememberCoroutineScope()

    // Form fields are seeded once the counter has loaded, and keyed so rotation keeps edits.
    var name by rememberSaveable(initial?.id) { mutableStateOf(initial?.name.orEmpty()) }
    var color by rememberSaveable(initial?.id) { mutableStateOf(initial?.color ?: CounterColors.first()) }
    var step by rememberSaveable(initial?.id) { mutableStateOf((initial?.step ?: 1).toString()) }
    var target by rememberSaveable(initial?.id) { mutableStateOf(initial?.target?.toString().orEmpty()) }
    var action by rememberSaveable(initial?.id) { mutableStateOf(initial?.targetAction ?: TargetAction.ASK) }
    var confirmDelete by remember { mutableStateOf(false) }

    val stepValue = step.toIntOrNull()?.takeIf { it in 1..CounterEngine.MAX_STEP }
    val targetValue = target.toIntOrNull()?.takeIf { it in 1..CounterEngine.MAX_TARGET }
    val targetValid = target.isBlank() || targetValue != null
    val valid = initial != null && stepValue != null && targetValid

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(if (id == null) R.string.new_counter else R.string.edit_counter)) },
                navigationIcon = {
                    IconButton(onClick = { onDone(null) }) {
                        Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.close))
                    }
                },
                actions = {
                    TextButton(
                        enabled = valid,
                        onClick = {
                            val base = initial ?: return@TextButton
                            scope.launch {
                                val saved = vm.save(
                                    base.copy(
                                        name = name.trim().ifEmpty { defaultName },
                                        color = color,
                                        step = stepValue ?: 1,
                                        target = targetValue,
                                        targetAction = action,
                                    ),
                                )
                                onDone(saved)
                            }
                        },
                    ) {
                        Icon(Icons.Filled.Check, contentDescription = null)
                        Text(stringResource(R.string.save), Modifier.padding(start = 6.dp))
                    }
                },
            )
        },
    ) { padding ->
        if (initial == null) return@Scaffold
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it.take(60) },
                label = { Text(stringResource(R.string.name)) },
                placeholder = { Text(defaultName) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth(),
            )

            Text(stringResource(R.string.color), style = MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                CounterColors.forEachIndexed { i, argb ->
                    val selected = argb == color
                    val label = stringResource(R.string.color_n, i + 1)
                    Box(
                        Modifier
                            .size(44.dp)
                            .background(accentOf(argb), CircleShape)
                            .then(
                                if (selected) Modifier.border(BorderStroke(3.dp, MaterialTheme.colorScheme.onSurface), CircleShape) else Modifier,
                            )
                            .clickable { color = argb }
                            .semantics {
                                contentDescription = label
                                this.selected = selected
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        if (selected) Icon(Icons.Filled.Check, contentDescription = null, tint = Color.White)
                    }
                }
            }

            OutlinedTextField(
                value = step,
                onValueChange = { step = it.filter(Char::isDigit).take(4) },
                label = { Text(stringResource(R.string.step)) },
                supportingText = {
                    Text(stringResource(if (stepValue == null) R.string.invalid_step else R.string.step_help))
                },
                isError = stepValue == null,
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = target,
                onValueChange = { target = it.filter(Char::isDigit).take(7) },
                label = { Text(stringResource(R.string.target)) },
                supportingText = {
                    Text(stringResource(if (targetValid) R.string.target_help else R.string.invalid_target))
                },
                isError = !targetValid,
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = target.isBlank(),
                    onClick = { target = "" },
                    label = { Text(stringResource(R.string.no_target)) },
                )
                TargetPresets.forEach { preset ->
                    FilterChip(
                        selected = targetValue == preset,
                        onClick = { target = preset.toString() },
                        label = { Text(preset.toString()) },
                    )
                }
            }

            if (targetValue != null) {
                Text(stringResource(R.string.on_target), style = MaterialTheme.typography.titleSmall)
                val options = listOf(
                    TargetAction.ASK to R.string.action_ask,
                    TargetAction.NEW_ROUND to R.string.action_new_round,
                    TargetAction.CONTINUE to R.string.action_continue,
                )
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    options.forEachIndexed { i, (value, label) ->
                        SegmentedButton(
                            selected = action == value,
                            onClick = { action = value },
                            shape = SegmentedButtonDefaults.itemShape(i, options.size),
                        ) { Text(stringResource(label), maxLines = 1) }
                    }
                }
                Text(
                    stringResource(
                        when (action) {
                            TargetAction.ASK -> R.string.action_ask_help
                            TargetAction.NEW_ROUND -> R.string.action_new_round_help
                            TargetAction.CONTINUE -> R.string.action_continue_help
                        },
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (id != null) {
                OutlinedButton(
                    onClick = { confirmDelete = true },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.padding(top = 16.dp, bottom = 24.dp),
                ) {
                    Icon(Icons.Filled.Delete, contentDescription = null)
                    Text(stringResource(R.string.delete_counter), Modifier.padding(start = 8.dp))
                }
            }
        }
    }

    if (confirmDelete && id != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.delete_counter)) },
            text = { Text(stringResource(R.string.delete_counter_body, initial?.name.orEmpty())) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    vm.delete(id)
                    onDeleted()
                }) { Text(stringResource(R.string.delete)) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}
