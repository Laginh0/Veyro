package com.veyro.p2p.nearby

import java.util.concurrent.atomic.AtomicLong

/**
 * Process-wide logical message state shared by Nearby and Desktop relay routes.
 * It deliberately outlives individual transport sockets so a route change cannot
 * reset sequence or duplicate protection.
 */
internal class LogicalSecurityContext(
    private val maximumEntries: Int = 4_096,
    val senderEpoch: Long = System.currentTimeMillis()
) {
    private val outboundSequence = AtomicLong()
    private val lock = Any()
    private val messageExpirations = LinkedHashMap<String, Long>()
    private data class OriginState(val epoch: Long, val lastSequence: Long)
    private val originStates = HashMap<String, OriginState>()

    init {
        require(maximumEntries in 1..65_536)
        require(senderEpoch > 0L)
    }

    fun nextSequence(): Long = outboundSequence.incrementAndGet()

    fun tryAccept(
        originDeviceId: String,
        messageId: String,
        senderEpoch: Long,
        sequenceNumber: Long,
        expiresAtMillis: Long,
        nowMillis: Long
    ): Boolean {
        if (originDeviceId.isBlank() || messageId.isBlank() || senderEpoch <= 0L ||
            sequenceNumber <= 0L ||
            expiresAtMillis < nowMillis
        ) return false
        synchronized(lock) {
            val expired = messageExpirations.entries
                .filter { it.value < nowMillis }
                .map { it.key }
            expired.forEach(messageExpirations::remove)
            val previous = originStates[originDeviceId]
            if (messageId in messageExpirations ||
                (previous != null && senderEpoch < previous.epoch) ||
                (previous != null && senderEpoch == previous.epoch &&
                    sequenceNumber <= previous.lastSequence)
            ) return false
            while (messageExpirations.size >= maximumEntries) {
                val oldest = messageExpirations.minByOrNull { it.value }?.key ?: break
                messageExpirations.remove(oldest)
            }
            messageExpirations[messageId] = expiresAtMillis
            originStates[originDeviceId] = OriginState(senderEpoch, sequenceNumber)
            return true
        }
    }
}
