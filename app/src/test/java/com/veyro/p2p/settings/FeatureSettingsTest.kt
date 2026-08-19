package com.veyro.p2p.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FeatureSettingsTest {
    @Test
    fun defaultsEnableAllAvailableFeatures() {
        val settings = FeatureSettings()

        assertEquals(9, settings.enabledCount)
        assertTrue(settings.requiresSpecialAccess)
    }

    @Test
    fun summaryReflectsIndividuallyDisabledFeatures() {
        val settings = FeatureSettings(
            fileTransfer = true,
            batterySync = false,
            notificationSync = false,
            mediaControl = false,
            telephonySync = false,
            findDevice = false,
            safeCommands = false,
            sharedLinks = true,
            remoteInput = false
        )

        assertEquals(2, settings.enabledCount)
        assertFalse(settings.requiresSpecialAccess)
    }
}
