package app.realme.unlink

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceSupportTest {
    @Test
    fun acceptsOnlyTheValidatedModelName() {
        assertTrue(DeviceSupport.isTargetName("realme Buds Wireless 5 ANC"))
        assertTrue(DeviceSupport.isTargetName(" REALME BUDS WIRELESS 5 ANC "))

        assertFalse(DeviceSupport.isTargetName(null))
        assertFalse(DeviceSupport.isTargetName("realme Buds Wireless 5"))
        assertFalse(DeviceSupport.isTargetName("realme Buds Wireless 5 Lite"))
        assertFalse(DeviceSupport.isTargetName("realme Buds Wireless 5 ANC Pro"))
    }
}
