package edu.purdue.cs536.morse

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SignalDebouncerTest {
    @Test fun ignoresShortFlipsAndAcceptsStableState() {
        val debouncer = SignalDebouncer(40)
        assertFalse(debouncer.update(true, 0))
        assertFalse(debouncer.update(false, 20))
        assertFalse(debouncer.update(true, 30))
        assertFalse(debouncer.update(true, 69))
        assertTrue(debouncer.update(true, 70))
    }
}
