package app.trimall.core

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicBoolean

/** Process-wide run state shared by the service, the app UI, and the widget. */
object RunController {
    data class State(
        val running: Boolean = false,
        val stage: String = "Idle",
        val done: Int = 0,
        val total: Int = 0,
    )

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()
    private val busy = AtomicBoolean(false)

    fun tryBegin(): Boolean {
        if (!busy.compareAndSet(false, true)) return false
        _state.value = State(running = true, stage = "Starting…")
        return true
    }

    fun update(stage: String, done: Int = _state.value.done, total: Int = _state.value.total) {
        _state.value = State(running = true, stage = stage, done = done, total = total)
    }

    fun end() {
        _state.value = State()
        busy.set(false)
    }
}
