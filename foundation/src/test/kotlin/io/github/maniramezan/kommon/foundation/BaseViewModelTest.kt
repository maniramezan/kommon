package io.github.maniramezan.kommon.foundation

import org.junit.Test
import kotlin.test.assertEquals

private data class TestState(
    val count: Int = 0,
)

private sealed interface TestEvent {
    data object Increment : TestEvent
}

private class TestViewModel : BaseViewModel<TestState, TestEvent>(TestState()) {
    override fun onEvent(event: TestEvent) {
        when (event) {
            TestEvent.Increment -> updateState { copy(count = count + 1) }
        }
    }

    fun currentCount(): Int = currentState.count
}

class BaseViewModelTest {
    @Test
    fun `onEvent updates state and is reflected in the StateFlow`() {
        val viewModel = TestViewModel()
        assertEquals(0, viewModel.state.value.count)

        viewModel.onEvent(TestEvent.Increment)
        viewModel.onEvent(TestEvent.Increment)

        assertEquals(2, viewModel.state.value.count)
        assertEquals(2, viewModel.currentCount())
    }
}
