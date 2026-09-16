package app.realme.unlink

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionLifecycleGateTest {
    @Test
    fun staleBackgroundOwnerCannotCloseNewForegroundSession() {
        val gate = SessionLifecycleGate()
        val firstForeground = requireNotNull(gate.enterForeground())
        val background = requireNotNull(gate.leaveForeground())
        val secondForeground = requireNotNull(gate.enterForeground())

        assertFalse(gate.isCurrentForeground(firstForeground))
        assertFalse(gate.isCurrentBackground(background))
        assertTrue(gate.isCurrentForeground(secondForeground))
    }

    @Test
    fun duplicateLifecycleCallbacksAreIgnored() {
        val gate = SessionLifecycleGate()
        assertNotNull(gate.enterForeground())
        assertNull(gate.enterForeground())
        assertNotNull(gate.leaveForeground())
        assertNull(gate.leaveForeground())
    }

    @Test
    fun refreshInvalidatesOlderForegroundWork() {
        val gate = SessionLifecycleGate()
        val initial = requireNotNull(gate.enterForeground())
        val refresh = requireNotNull(gate.advanceForeground())

        assertFalse(gate.isCurrentForeground(initial))
        assertTrue(gate.isCurrentForeground(refresh))
    }
}
