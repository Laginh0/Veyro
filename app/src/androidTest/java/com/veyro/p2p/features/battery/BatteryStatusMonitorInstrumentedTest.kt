package com.veyro.p2p.features.battery

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BatteryStatusMonitorInstrumentedTest {
    @Test
    fun stickyBatteryBroadcast_producesValidStatus() = runBlocking {
        val monitor = BatteryStatusMonitor(
            InstrumentationRegistry.getInstrumentation().targetContext
        )

        val status = withTimeout(15_000L) {
            monitor.statusUpdates().first()
        }

        assertTrue(status.chargePercentage in 0..100)
        assertTrue(status.eventTimestamp > 0L)
    }
}
