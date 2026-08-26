package com.veyro.p2p.nearby

import android.content.Context
import android.util.Base64
import com.veyro.p2p.desktopinterop.DesktopInteropProtocol
import com.veyro.p2p.desktopinterop.DesktopTrustedPeer
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest

/** Persistent Trust Hub for Android logical peers, independent of Nearby endpoint IDs. */
internal class NearbyPeerTrustStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        "veyro_nearby_trust_v1",
        Context.MODE_PRIVATE
    )

    @Synchronized
    fun active(deviceId: String): DesktopTrustedPeer? = load().singleOrNull {
        it.deviceId == deviceId && !it.isRevoked
    }

    @Synchronized
    fun trust(peer: DesktopTrustedPeer) {
        require(peer.deviceId.isNotBlank() && peer.displayName.isNotBlank() && !peer.isRevoked)
        DesktopInteropProtocol.publicKey(peer.identityPublicKeySpki)
        val peers = load().toMutableList()
        val existing = peers.singleOrNull { it.deviceId == peer.deviceId }
        if (existing != null && !MessageDigest.isEqual(
                existing.identityPublicKeySpki,
                peer.identityPublicKeySpki
            )
        ) {
            throw SecurityException("identity_key_collision")
        }
        peers.removeAll { it.deviceId == peer.deviceId }
        peers += peer.copy(
            trustedAtMillis = existing?.trustedAtMillis ?: peer.trustedAtMillis,
            revokedAtMillis = null
        )
        save(peers)
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

    private fun load(): List<DesktopTrustedPeer> {
        val encoded = preferences.getString(KEY_PEERS, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(encoded)
            buildList {
                repeat(array.length()) { index ->
                    val item = array.getJSONObject(index)
                    val publicKey = Base64.decode(item.getString("publicKey"), Base64.NO_WRAP)
                    DesktopInteropProtocol.publicKey(publicKey)
                    add(
                        DesktopTrustedPeer(
                            item.getString("deviceId"),
                            item.getString("displayName"),
                            publicKey,
                            0,
                            item.getLong("trustedAt"),
                            item.getLong("lastSeenAt"),
                            if (item.isNull("revokedAt")) null else item.getLong("revokedAt")
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
                    .put("trustedAt", peer.trustedAtMillis)
                    .put("lastSeenAt", peer.lastSeenAtMillis)
                    .put("revokedAt", peer.revokedAtMillis ?: JSONObject.NULL)
            )
        }
        check(preferences.edit().putString(KEY_PEERS, array.toString()).commit()) {
            "Não foi possível persistir o Trust Hub Nearby."
        }
    }

    private companion object {
        const val KEY_PEERS = "logical_android_peers"
    }
}
