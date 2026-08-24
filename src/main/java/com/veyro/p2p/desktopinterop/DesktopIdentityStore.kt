package com.veyro.p2p.desktopinterop

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.spec.ECGenParameterSpec

internal data class DesktopIdentity(
    val deviceId: String,
    val displayName: String,
    val keyPair: KeyPair
)

internal class DesktopIdentityStore(
    context: Context,
    private val deviceId: String,
    private val displayName: String
) {
    private val preferences = context.applicationContext.getSharedPreferences(
        "veyro_desktop_interop",
        Context.MODE_PRIVATE
    )

    @SuppressLint("NewApi")
    fun loadOrCreate(): DesktopIdentity {
        check(isSupported()) { "Veyro Desktop requer Android 6 ou superior" }
        val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }
        val certificate = keyStore.getCertificate(KEY_ALIAS)
        val privateKey = keyStore.getKey(KEY_ALIAS, null) as? java.security.PrivateKey
        val createdNewKey = certificate == null || privateKey == null
        val keyPair = if (!createdNewKey) {
            KeyPair(checkNotNull(certificate).publicKey, checkNotNull(privateKey))
        } else {
            val generator = KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, KEYSTORE_PROVIDER)
            val purposes = KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY or
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) KeyProperties.PURPOSE_AGREE_KEY else 0
            generator.initialize(
                KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    purposes
                    )
                    .setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1"))
                    .setDigests(KeyProperties.DIGEST_SHA256, KeyProperties.DIGEST_NONE)
                    .setUserAuthenticationRequired(false)
                    .build()
            )
            generator.generateKeyPair()
        }
        LEGACY_KEY_ALIASES.filter(keyStore::containsAlias).forEach(keyStore::deleteEntry)
        preferences.edit()
            .putBoolean(KEY_CREATED, true)
            .apply {
                if (createdNewKey) remove(KEY_TRUSTED_PEERS)
            }
            .apply()
        return DesktopIdentity(deviceId, displayName.removePrefix("Veyro - "), keyPair)
    }

    companion object {
        private const val KEYSTORE_PROVIDER = "AndroidKeyStore"
        private const val KEY_ALIAS = "veyro.desktop.identity.p256.v3"
        private val LEGACY_KEY_ALIASES = listOf(
            "veyro.desktop.identity.p256.v1",
            "veyro.desktop.identity.p256.v2"
        )
        private const val KEY_CREATED = "identity_key_created"
        private const val KEY_TRUSTED_PEERS = "trusted_desktop_peers_v1"

        fun isSupported(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
    }
}
