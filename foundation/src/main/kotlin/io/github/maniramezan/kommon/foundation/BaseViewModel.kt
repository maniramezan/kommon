package io.github.maniramezan.kommon.foundation

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Base ViewModel with a [StateFlow]-driven unidirectional data flow.
 *
 * Provides common patterns for state management matching TCA's reducer pattern: a single
 * immutable [state] snapshot, updated only through [updateState], and a single [onEvent] entry
 * point for user actions.
 */
public abstract class BaseViewModel<State, Event>(
    initialState: State,
) : ViewModel() {
    private val _state = MutableStateFlow(initialState)
    public val state: StateFlow<State> = _state.asStateFlow()

    protected val currentState: State
        get() = _state.value

    protected fun updateState(update: State.() -> State) {
        _state.update { it.update() }
    }

    /**
     * Handle user events (actions). Override to implement event handling logic.
     */
    public abstract fun onEvent(event: Event)
}
