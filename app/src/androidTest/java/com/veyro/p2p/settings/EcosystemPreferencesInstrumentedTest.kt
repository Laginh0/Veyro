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
}
