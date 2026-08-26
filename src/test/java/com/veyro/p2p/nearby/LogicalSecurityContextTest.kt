package com.veyro.p2p.nearby

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LogicalSecurityContextTest {
    @Test
    fun `message id is deduplicated across transport routes`() {
        val context = LogicalSecurityContext()
        val now = 1_000L

        assertTrue(context.tryAccept("android-a", "message-x", 1, 1, now + 1_000, now))
        assertFalse(context.tryAccept("android-a", "message-x", 1, 1, now + 1_000, now))
    }

    @Test
    fun `late sequence from the old route is rejected after failover`() {
        val context = LogicalSecurityContext()
        val now = 1_000L

        assertTrue(context.tryAccept("android-a", "message-102", 7, 102, now + 1_000, now))
        assertFalse(context.tryAccept("android-a", "message-101", 7, 101, now + 1_000, now))
    }

    @Test
    fun `bounded cache evicts without accepting an older origin sequence`() {
        val context = LogicalSecurityContext(maximumEntries = 2)
        val now = 1_000L

        assertTrue(context.tryAccept("android-a", "m1", 1, 1, now + 1_000, now))
        assertTrue(context.tryAccept("android-a", "m2", 1, 2, now + 2_000, now))
        assertTrue(context.tryAccept("android-a", "m3", 1, 3, now + 3_000, now))
        assertFalse(context.tryAccept("android-a", "m1", 1, 1, now + 1_000, now))
    }

    @Test
    fun `newer sender epoch resets sequence while stale epoch remains rejected`() {
        val context = LogicalSecurityContext()
        val now = 1_000L

        assertTrue(context.tryAccept("desktop", "old-9", 9, 900, now + 1_000, now))
        assertTrue(context.tryAccept("desktop", "new-1", 10, 1, now + 1_000, now))
        assertFalse(context.tryAccept("desktop", "late-old", 9, 901, now + 1_000, now))
    }
}
