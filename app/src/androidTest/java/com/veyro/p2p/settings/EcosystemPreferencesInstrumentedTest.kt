package com.veyro.p2p.settings

import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EcosystemPreferencesInstrumentedTest {

    @Test
    fun trustRulesPersistAndRemainDisabledByDefault() {
        val preferences = EcosystemPreferences(ApplicationProvider.getApplicationContext())
        val deviceName = "Veyro - Instrumented Test #local"

        preferences.removeDevice(deviceName)
        try {
            val initialRules = preferences.rememberDevice(deviceName)
            assertFalse(initialRules.autoAcceptFiles)
            assertFalse(initialRules.allowFindDevice)

            preferences.updateRules(
                initialRules.copy(
                    autoAcceptFiles = true,
                    allowFindDevice = true
                )
            )

            val reloaded = EcosystemPreferences(ApplicationProvider.getApplicationContext())
                .rulesFor(deviceName)
            assertTrue(reloaded?.autoAcceptFiles == true)
            assertTrue(reloaded?.allowFindDevice == true)
        } finally {
            preferences.removeDevice(deviceName)
        }
    }

    @Test
    fun energyModeAndLocalIdentityPersist() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val preferences = EcosystemPreferences(context)
        val originalMode = preferences.energyMode()
        try {
            preferences.setEnergyMode(EnergyMode.BATTERY_SAVER)
            val reloaded = EcosystemPreferences(context)
            assertEquals(EnergyMode.BATTERY_SAVER, reloaded.energyMode())
            assertEquals(preferences.localEndpointName(), reloaded.localEndpointName())
        } finally {
            preferences.setEnergyMode(originalMode)
        }
    }

    @Test
    fun continuousEcosystemChoicePersists() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val preferences = EcosystemPreferences(context)
        val originalValue = preferences.ecosystemEnabled()
        try {
            preferences.setEcosystemEnabled(true)
            assertTrue(EcosystemPreferences(context).ecosystemEnabled())

            preferences.setEcosystemEnabled(false)
            assertFalse(EcosystemPreferences(context).ecosystemEnabled())
        } finally {
            preferences.setEcosystemEnabled(originalValue)
        }
    }

    @Test
    fun appLanguagePersists() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val preferences = EcosystemPreferences(context)
        val originalLanguage = preferences.appLanguage()
        try {
            preferences.setAppLanguage(AppLanguage.ENGLISH)
            assertEquals(AppLanguage.ENGLISH, EcosystemPreferences(context).appLanguage())

            preferences.setAppLanguage(AppLanguage.PORTUGUESE)
            assertEquals(AppLanguage.PORTUGUESE, EcosystemPreferences(context).appLanguage())
        } finally {
            preferences.setAppLanguage(originalLanguage)
        }
    }

    @Test
    fun featureSettingsPersist() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val preferences = EcosystemPreferences(context)
        val originalSettings = preferences.featureSettings()
        val changedSettings = FeatureSettings(
            fileTransfer = false,
            batterySync = true,
            connectivitySync = false,
            ping = true,
            notificationSync = false,
            mediaControl = true,
            telephonySync = false,
            findDevice = true,
            safeCommands = false,
            sharedLinks = true,
            remoteInput = false,
            contactSync = true,
            presentationMode = false,
            drawingTablet = true,
            remoteFiles = false,
            clipboardSync = true
        )
        try {
            preferences.setFeatureSettings(changedSettings)
            assertEquals(changedSettings, EcosystemPreferences(context).featureSettings())
        } finally {
            preferences.setFeatureSettings(originalSettings)
        }
    }
}
