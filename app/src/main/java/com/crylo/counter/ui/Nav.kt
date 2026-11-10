package com.crylo.counter.ui

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import com.crylo.counter.data.Settings

sealed interface Route {
    data object Home : Route
    data object Prefs : Route
    data class Count(val id: Long) : Route
    data class Edit(val id: Long?) : Route
    data class History(val id: Long) : Route
}

private fun Route.encode(): String = when (this) {
    Route.Home -> "home"
    Route.Prefs -> "prefs"
    is Route.Count -> "count:$id"
    is Route.Edit -> "edit:${id ?: 0}"
    is Route.History -> "history:$id"
}

private fun decode(s: String): Route {
    val id = s.substringAfter(':', "0").toLongOrNull() ?: 0
    return when (s.substringBefore(':')) {
        "count" -> Route.Count(id)
        "edit" -> Route.Edit(id.takeIf { it > 0 })
        "history" -> Route.History(id)
        "prefs" -> Route.Prefs
        else -> Route.Home
    }
}

private val StackSaver = listSaver<SnapshotStateList<Route>, String>(
    save = { stack -> stack.map { it.encode() } },
    restore = { saved -> mutableStateListOf<Route>().apply { addAll(saved.map(::decode)) } },
)

/**
 * A plain back stack is all four screens need, and it keeps the app free of a navigation
 * library. It survives rotation and process death through [rememberSaveable].
 */
@Composable
fun CounterNav(vm: MainViewModel, settings: Settings, openRequest: MutableState<Long?>) {
    val stack = rememberSaveable(saver = StackSaver) { mutableStateListOf<Route>(Route.Home) }
    var started by rememberSaveable { mutableStateOf(false) }

    // A tally is opened to count, so go straight back to the counter used last time.
    LaunchedEffect(Unit) {
        if (!started) {
            started = true
            val last = settings.lastCounterId
            if (openRequest.value == null && last != null && vm.load(last) != null) stack.add(Route.Count(last))
        }
    }
    val request by openRequest
    LaunchedEffect(request) {
        val id = request ?: return@LaunchedEffect
        openRequest.value = null
        stack.clear()
        stack.add(Route.Home)
        stack.add(Route.Count(id))
    }

    fun push(r: Route) = stack.add(r)
    fun pop() {
        if (stack.size > 1) stack.removeAt(stack.lastIndex)
    }

    BackHandler(enabled = stack.size > 1) { pop() }

    when (val route = stack.last()) {
        Route.Home -> CounterListScreen(
            vm = vm,
            onOpen = { push(Route.Count(it)) },
            onAdd = { push(Route.Edit(null)) },
            onSettings = { push(Route.Prefs) },
        )
        Route.Prefs -> SettingsScreen(vm, settings, onBack = ::pop)
        is Route.Count -> CountingScreen(
            vm = vm,
            id = route.id,
            settings = settings,
            onBack = ::pop,
            onEdit = { push(Route.Edit(route.id)) },
            onHistory = { push(Route.History(route.id)) },
        )
        is Route.Edit -> EditCounterScreen(
            vm = vm,
            id = route.id,
            onDone = { savedId ->
                pop()
                // A new counter opens straight into counting.
                if (route.id == null && savedId != null) push(Route.Count(savedId))
            },
            onDeleted = {
                stack.removeAll { it is Route.Count && it.id == route.id || it is Route.History && it.id == route.id }
                pop()
            },
        )
        is Route.History -> HistoryScreen(vm, route.id, onBack = ::pop)
    }
}
