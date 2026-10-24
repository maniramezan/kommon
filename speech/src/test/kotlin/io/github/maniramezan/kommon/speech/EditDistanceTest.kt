package io.github.maniramezan.kommon.speech

import io.github.maniramezan.kommon.speech.EditDistance.Operation
import org.junit.Test
import kotlin.test.assertEquals

class EditDistanceTest {
    private fun ops(
        reference: String,
        heard: String,
    ) = EditDistance.operations(reference.split(' ').filter { it.isNotEmpty() }, heard.split(' ').filter { it.isNotEmpty() })

    @Test
    fun `equal sequences are all keeps`() {
        assertEquals(listOf(Operation.Keep(0, 0), Operation.Keep(1, 1)), ops("a b", "a b"))
    }

    @Test
    fun `empty sequences have no operations`() {
        assertEquals(emptyList(), ops("", ""))
    }

    @Test
    fun `empty heard deletes every reference word`() {
        assertEquals(listOf(Operation.Delete(0), Operation.Delete(1)), ops("a b", ""))
    }

    @Test
    fun `empty reference inserts every heard word`() {
        assertEquals(listOf(Operation.Insert(0), Operation.Insert(1)), ops("", "a b"))
    }

    @Test
    fun `one missing middle word is a single deletion`() {
        assertEquals(
            listOf(Operation.Keep(0, 0), Operation.Delete(1), Operation.Keep(2, 1)),
            ops("a b c", "a c"),
        )
    }

    @Test
    fun `a swapped word is a substitution`() {
        assertEquals(listOf(Operation.Keep(0, 0), Operation.Substitute(1, 1)), ops("a b", "a x"))
    }

    @Test
    fun `ties prefer substitution over deletion plus insertion`() {
        assertEquals(listOf(Operation.Substitute(0, 0)), ops("a", "b"))
    }

    @Test
    fun `an extra word in the middle is a single insertion`() {
        assertEquals(
            listOf(Operation.Keep(0, 0), Operation.Insert(1), Operation.Keep(1, 2)),
            ops("a c", "a b c"),
        )
    }
}
