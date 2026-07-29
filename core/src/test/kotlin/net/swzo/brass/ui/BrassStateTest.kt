package net.swzo.brass.ui

import net.swzo.brass.ui.kit.base.BrassState
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class BrassStateTest {

    @Test
    fun `onChange fires immediately with the current value`() {
        val s = BrassState(7)
        var seen = -1
        s.onChange { seen = it }
        assertEquals(7, seen, "a freshly bound widget must show the right thing at once")
    }

    @Test
    fun `setting the same value notifies nobody`() {
        val s = BrassState(1)
        var calls = 0
        s.onChange { calls++ }   // the immediate call
        s.value = 1
        s.value = 1
        assertEquals(1, calls, "assigning an unchanged value must be free")
    }

    @Test
    fun `the unsubscribe handle actually unsubscribes`() {
        val s = BrassState(0)
        var calls = 0
        val stop = s.onChange { calls++ }
        s.value = 1
        stop()
        s.value = 2
        assertEquals(2, calls, "one immediate call plus one change")
    }

    @Test
    fun `a listener may unbind during notification`() {
        val s = BrassState(0)
        var stop: (() -> Unit)? = null
        stop = s.onChange { stop?.invoke() }
        s.value = 1   // must not throw ConcurrentModificationException
    }

    @Test
    fun `map derives and tracks`() {
        val n = BrassState(2)
        val doubled = n.map { it * 2 }
        assertEquals(4, doubled.value)
        n.value = 5
        assertEquals(10, doubled.value)
    }

    @Test
    fun `combine tracks both sources`() {
        val a = BrassState(1)
        val b = BrassState(2)
        val sum = a.combine(b) { x, y -> x + y }
        assertEquals(3, sum.value)
        a.value = 10
        assertEquals(12, sum.value)
        b.value = 20
        assertEquals(30, sum.value)
    }

    @Test
    fun `filter keeps the last passing value`() {
        val n = BrassState(0)
        val evens = n.filter { it % 2 == 0 }
        n.value = 3
        assertEquals(0, evens.value, "an odd value must not get through")
        n.value = 4
        assertEquals(4, evens.value)
    }

    @Test
    fun `update applies a transform`() {
        val n = BrassState(1)
        n.update { it + 1 }
        assertEquals(2, n.value)
    }
}
