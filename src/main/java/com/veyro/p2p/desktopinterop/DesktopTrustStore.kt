package com.veyro.p2p.desktopinterop

import android.content.Context
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject

internal data class DesktopTrustedPeer(
    val deviceId: String,
    val displayName: String,
    val identityPublicKeySpki: ByteArray,
    val capabilities: Int,
    val trustedAtMillis: Long,
    val lastSeenAtMillis: Long,
    val revokedAtMillis: Long?
) {
    val isRevoked: Boolean
        get() = revokedAtMillis != null
}

internal class DesktopTrustStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        "veyro_desktop_interop",
        Context.MODE_PRIVATE
    )

    @Synchronized
    fun active(deviceId: String): DesktopTrustedPeer? = load().firstOrNull {
        it.deviceId == deviceId && !it.isRevoked
    }

    @Synchronized
    fun activeByDisplayName(displayName: String): DesktopTrustedPeer? = load().firstOrNull {
        it.displayName == displayName && !it.isRevoked
    }

    @Synchronized
    fun trust(peer: DesktopTrustedPeer) {
        DesktopInteropProtocol.publicKey(peer.identityPublicKeySpki)
        val peers = load().filterNot { it.deviceId == peer.deviceId }.toMutableList()
        peers += peer.copy(revokedAtMillis = null)
        save(peers)
    }

    @Synchronized
    fun markSeen(deviceId: String) {
        val peers = load().toMutableList()
        val index = peers.indexOfFirst { it.deviceId == deviceId && !it.isRevoked }
        if (index >= 0) {
            peers[index] = peers[index].copy(lastSeenAtMillis = System.currentTimeMillis())
            save(peers)
        }
    }

    @Synchronized
    fun revokeByDisplayName(displayName: String): Boolean {
        val peers = load().toMutableList()
        val now = System.currentTimeMillis()
        var changed = false
        peers.indices.forEach { index ->
            if (!peers[index].isRevoked && peers[index].displayName == displayName) {
                peers[index] = peers[index].copy(revokedAtMillis = now)
                changed = true
            }
        }
        if (changed) save(peers)
        return changed
    }

    @Synchronized
    fun allActive(): List<DesktopTrustedPeer> = load().filterNot(DesktopTrustedPeer::isRevoked)

    private fun load(): List<DesktopTrustedPeer> {
        val encoded = preferences.getString(KEY_TRUSTED_PEERS, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(encoded)
            buildList {
                repeat(array.length()) { index ->
                    val item = array.getJSONObject(index)
                    val publicKey = Base64.decode(item.getString("publicKey"), Base64.NO_WRAP)
                    DesktopInteropProtocol.publicKey(publicKey)
                    add(
                        DesktopTrustedPeer(
                            deviceId = item.getString("deviceId"),
                            displayName = item.getString("displayName"),
                            identityPublicKeySpki = publicKey,
                            capabilities = item.getInt("capabilities"),
                            trustedAtMillis = item.getLong("trustedAt"),
                            lastSeenAtMillis = item.getLong("lastSeenAt"),
                            revokedAtMillis = if (item.isNull("revokedAt")) null else item.getLong("revokedAt")
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun save(peers: List<DesktopTrustedPeer>) {
        val array = JSONArray()
        peers.forEach { peer ->
            array.put(
                JSONObject()
                    .put("deviceId", peer.deviceId)
                    .put("displayName", peer.displayName)
                    .put("publicKey", Base64.encodeToString(peer.identityPublicKeySpki, Base64.NO_WRAP))
                    .put("capabilities", peer.capabilities)
                    .put("trustedAt", peer.trustedAtMillis)
                    .put("lastSeenAt", peer.lastSeenAtMillis)
                    .put("revokedAt", peer.revokedAtMillis ?: JSONObject.NULL)
            )
        }
        preferences.edit().putString(KEY_TRUSTED_PEERS, array.toString()).apply()
    }

    private companion object {
        const val KEY_TRUSTED_PEERS = "trusted_desktop_peers_v1"
    }
}
