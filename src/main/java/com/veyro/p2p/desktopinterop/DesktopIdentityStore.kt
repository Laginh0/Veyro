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
        val keyPair = if (certificate != null && privateKey != null) {
            KeyPair(certificate.publicKey, privateKey)
        } else {
            val generator = KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, KEYSTORE_PROVIDER)
            generator.initialize(
                KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY
                )
                    .setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1"))
                    .setDigests(KeyProperties.DIGEST_SHA256)
                    .setUserAuthenticationRequired(false)
                    .build()
            )
            generator.generateKeyPair()
        }
        preferences.edit().putBoolean(KEY_CREATED, true).apply()
        return DesktopIdentity(deviceId, displayName.removePrefix("Veyro - "), keyPair)
    }

    companion object {
        private const val KEYSTORE_PROVIDER = "AndroidKeyStore"
        private const val KEY_ALIAS = "veyro.desktop.identity.p256.v1"
        private const val KEY_CREATED = "identity_key_created"

        fun isSupported(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
    }
}
