package com.veyro.p2p.nearby

import java.net.URLDecoder
import java.net.URLEncoder

data class EndpointIdentity(
    val deviceId: String,
    val capacityScore: Int,
    val identityKeyFingerprint: String,
    val displayName: String
) {
    val trustedName: String
        get() = "$displayName #$deviceId"

    fun toWireName(): String {
        val prefix = listOf(
            WIRE_PREFIX,
            deviceId,
            capacityScore.coerceIn(0, MAX_SCORE),
            identityKeyFingerprint
        ).joinToString(WIRE_SEPARATOR) + WIRE_SEPARATOR
        val encodedName = buildString {
            val source = displayName.trim().ifBlank { "Veyro" }
            var offset = 0
            while (offset < source.length) {
                val codePoint = source.codePointAt(offset)
                offset += Character.charCount(codePoint)
                val encodedCodePoint = URLEncoder.encode(
                    String(Character.toChars(codePoint)),
                    Charsets.UTF_8.name()
                )
                if ((prefix + this + encodedCodePoint).toByteArray(Charsets.UTF_8).size >
                    MAX_WIRE_NAME_BYTES
                ) {
                    break
                }
                append(encodedCodePoint)
            }
        }
        return prefix + encodedName.ifBlank { "Veyro" }
    }

    companion object {
        private const val WIRE_PREFIX = "VYR3"
        private const val WIRE_SEPARATOR = "|"
        private const val MAX_SCORE = 999
        private const val MAX_WIRE_NAME_BYTES = 131

        fun parse(wireName: String): EndpointIdentity? {
            val parts = wireName.split(WIRE_SEPARATOR, limit = 5)
            if (parts.size != 5 || parts[0] != WIRE_PREFIX) return null
            val deviceId = parts[1].takeIf { it.matches(Regex("[a-zA-Z0-9]{6,16}")) }
                ?: return null
            val score = parts[2].toIntOrNull()?.takeIf { it in 0..MAX_SCORE } ?: return null
            val fingerprint = parts[3].lowercase().takeIf {
                it.matches(Regex("[0-9a-f]{16}"))
            } ?: return null
            val displayName = runCatching {
                URLDecoder.decode(parts[4], Charsets.UTF_8.name())
            }.getOrNull()?.trim()?.takeIf { it.isNotEmpty() } ?: return null
            return EndpointIdentity(deviceId, score, fingerprint, displayName.take(80))
        }

        fun shouldInitiate(local: EndpointIdentity, remote: EndpointIdentity): Boolean {
            val capacityComparison = local.capacityScore.compareTo(remote.capacityScore)
            if (capacityComparison != 0) return capacityComparison < 0
            return local.deviceId < remote.deviceId
        }

        fun deterministicJitterMillis(localDeviceId: String, remoteDeviceId: String): Long {
            val stableHash = "$localDeviceId:$remoteDeviceId".fold(17) { hash, char ->
                (hash * 31 + char.code) and Int.MAX_VALUE
            }
            return 100L + (stableHash % 201)
        }
    }
}
