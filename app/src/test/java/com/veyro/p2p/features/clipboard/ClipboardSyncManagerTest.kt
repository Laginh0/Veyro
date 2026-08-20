package com.veyro.p2p.features.clipboard

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ClipboardSyncManagerTest {
    @Test
    fun sizeGuardAcceptsNormalTextAndRejectsOversizedText() {
        assertTrue(ClipboardSyncManager.isSafeText("clipboard text"))
        assertFalse(ClipboardSyncManager.isSafeText("a".repeat(ClipboardSyncManager.MAX_TEXT_BYTES + 1)))
    }

    @Test
    fun fingerprintsAreStableAndContentSensitive() {
        assertTrue(
            ClipboardSyncManager.fingerprint("same") ==
                ClipboardSyncManager.fingerprint("same")
        )
        assertNotEquals(
            ClipboardSyncManager.fingerprint("first"),
            ClipboardSyncManager.fingerprint("second")
        )
    }
}
