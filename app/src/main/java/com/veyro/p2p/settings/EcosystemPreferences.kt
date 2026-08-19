package com.veyro.p2p.settings

import android.content.Context
import android.os.Build
import java.security.MessageDigest
import java.util.UUID

enum class EnergyMode {
    CONTINUOUS,
    BALANCED,
    BATTERY_SAVER;

    companion object {
        fun fromStored(value: String?): EnergyMode = entries.firstOrNull {
            it.name == value
        } ?: BALANCED
    }
}

data class TrustedDeviceRules(
    val deviceName: String,
    val autoAcceptFiles: Boolean = false,
    val allowFindDevice: Boolean = false
)

class EcosystemPreferences(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE
    )

    fun localDeviceId(): String {
        return preferences.getString(KEY_LOCAL_DEVICE_ID, null)
            ?: UUID.randomUUID().toString().replace("-", "").take(8).also { generated ->
                preferences.edit().putString(KEY_LOCAL_DEVICE_ID, generated).apply()
            }
    }

    fun localDisplayName(): String = "Veyro - ${Build.MODEL}"

    fun localEndpointName(): String = "${localDisplayName()} #${localDeviceId()}"

    fun ecosystemEnabled(): Boolean = preferences.getBoolean(KEY_ECOSYSTEM_ENABLED, false)

    fun setEcosystemEnabled(enabled: Boolean) {
        preferences.edit().putBoolean(KEY_ECOSYSTEM_ENABLED, enabled).apply()
    }

    fun energyMode(): EnergyMode = EnergyMode.fromStored(
        preferences.getString(KEY_ENERGY_MODE, null)
    )

    fun setEnergyMode(mode: EnergyMode) {
        preferences.edit().putString(KEY_ENERGY_MODE, mode.name).apply()
    }

    @Synchronized
    fun trustedDevices(): List<TrustedDeviceRules> = deviceNames().mapNotNull { name ->
        rulesFor(name)
    }.sortedBy { it.deviceName.lowercase() }

    @Synchronized
    fun rememberDevice(deviceName: String): TrustedDeviceRules {
        val cleanName = cleanDeviceName(deviceName)
        require(cleanName.isNotBlank()) { "O nome do aparelho não pode ficar vazio." }
        val names = deviceNames().toMutableSet().apply { add(cleanName) }
        preferences.edit()
            .putStringSet(KEY_TRUSTED_DEVICE_NAMES, names)
            .putString(ruleKey(cleanName, SUFFIX_NAME), cleanName)
            .apply()
        return rulesFor(cleanName) ?: TrustedDeviceRules(cleanName)
    }

    @Synchronized
    fun updateRules(rules: TrustedDeviceRules) {
        val cleanName = cleanDeviceName(rules.deviceName)
        rememberDevice(cleanName)
        preferences.edit()
            .putBoolean(ruleKey(cleanName, SUFFIX_AUTO_FILES), rules.autoAcceptFiles)
            .putBoolean(ruleKey(cleanName, SUFFIX_FIND_DEVICE), rules.allowFindDevice)
            .apply()
    }

    @Synchronized
    fun removeDevice(deviceName: String) {
        val cleanName = cleanDeviceName(deviceName)
        val names = deviceNames().toMutableSet().apply { remove(cleanName) }
        preferences.edit()
            .putStringSet(KEY_TRUSTED_DEVICE_NAMES, names)
            .remove(ruleKey(cleanName, SUFFIX_NAME))
            .remove(ruleKey(cleanName, SUFFIX_AUTO_FILES))
            .remove(ruleKey(cleanName, SUFFIX_FIND_DEVICE))
            .apply()
    }

    fun rulesFor(deviceName: String?): TrustedDeviceRules? {
        val cleanName = cleanDeviceName(deviceName.orEmpty())
        if (cleanName.isBlank() || cleanName !in deviceNames()) return null
        val storedName = preferences.getString(ruleKey(cleanName, SUFFIX_NAME), cleanName)
            ?: cleanName
        return TrustedDeviceRules(
            deviceName = storedName,
            autoAcceptFiles = preferences.getBoolean(
                ruleKey(cleanName, SUFFIX_AUTO_FILES),
                false
            ),
            allowFindDevice = preferences.getBoolean(
                ruleKey(cleanName, SUFFIX_FIND_DEVICE),
                false
            )
        )
    }

    private fun deviceNames(): Set<String> = preferences
        .getStringSet(KEY_TRUSTED_DEVICE_NAMES, emptySet())
        ?.toSet()
        .orEmpty()

    private fun cleanDeviceName(value: String): String = value.trim().take(MAX_DEVICE_NAME_LENGTH)

    private fun ruleKey(deviceName: String, suffix: String): String =
        "device_${deviceName.sha256().take(24)}_$suffix"

    private fun String.sha256(): String = MessageDigest.getInstance("SHA-256")
        .digest(lowercase().toByteArray(Charsets.UTF_8))
        .joinToString("") { byte -> "%02x".format(byte) }

    private companion object {
        const val PREFERENCES_NAME = "veyro_ecosystem"
        const val KEY_LOCAL_DEVICE_ID = "local_device_id"
        const val KEY_ECOSYSTEM_ENABLED = "ecosystem_enabled"
        const val KEY_ENERGY_MODE = "energy_mode"
        const val KEY_TRUSTED_DEVICE_NAMES = "trusted_device_names"
        const val SUFFIX_NAME = "name"
        const val SUFFIX_AUTO_FILES = "auto_files"
        const val SUFFIX_FIND_DEVICE = "find_device"
        const val MAX_DEVICE_NAME_LENGTH = 120
    }
}
