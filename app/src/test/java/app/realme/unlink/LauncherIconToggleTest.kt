package app.realme.unlink

import android.content.pm.PackageManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LauncherIconToggleTest {
    @Test
    fun defaultAndDisabledStatesReportHidden() {
        assertFalse(LauncherIconToggle.isEnabled(PackageManager.COMPONENT_ENABLED_STATE_DEFAULT))
        assertFalse(LauncherIconToggle.isEnabled(PackageManager.COMPONENT_ENABLED_STATE_DISABLED))
        assertFalse(LauncherIconToggle.isEnabled(PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER))
    }

    @Test
    fun enabledStateReportsVisible() {
        assertTrue(LauncherIconToggle.isEnabled(PackageManager.COMPONENT_ENABLED_STATE_ENABLED))
    }

    @Test
    fun targetStateMapsBooleanToComponentState() {
        assertEquals(
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
            LauncherIconToggle.targetState(true),
        )
        assertEquals(
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
            LauncherIconToggle.targetState(false),
        )
    }
}
