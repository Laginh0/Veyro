package com.veyro.p2p.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FeatureSettingsTest {
    @Test
    fun defaultsKeepSensitiveClipboardSyncOptIn() {
        val settings = FeatureSettings()

        assertEquals(15, settings.enabledCount)
        assertEquals(16, FeatureSettings.AVAILABLE_COUNT)
        assertFalse(settings.clipboardSync)
        assertTrue(settings.requiresSpecialAccess)
    }

    @Test
    fun summaryReflectsIndividuallyDisabledFeatures() {
        val settings = FeatureSettings(
            fileTransfer = true,
            batterySync = false,
            connectivitySync = false,
            ping = false,
            notificationSync = false,
            mediaControl = false,
            telephonySync = false,
            findDevice = false,
            safeCommands = false,
            sharedLinks = true,
            remoteInput = false,
            contactSync = false,
            presentationMode = false,
            drawingTablet = false,
            remoteFiles = false,
            clipboardSync = false
        )

        assertEquals(2, settings.enabledCount)
        assertFalse(settings.requiresSpecialAccess)
    }
}
