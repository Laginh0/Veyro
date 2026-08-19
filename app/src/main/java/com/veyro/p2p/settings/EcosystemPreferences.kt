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

enum class AppLanguage {
    PORTUGUESE,
    ENGLISH;

    companion object {
        fun fromStored(value: String?): AppLanguage = entries.firstOrNull {
            it.name == value
        } ?: PORTUGUESE
    }
}

data class TrustedDeviceRules(
    val deviceName: String,
    val autoAcceptFiles: Boolean = false,
    val allowFindDevice: Boolean = false
)

data class FeatureSettings(
    val fileTransfer: Boolean = true,
    val batterySync: Boolean = true,
    val connectivitySync: Boolean = true,
    val ping: Boolean = true,
    val notificationSync: Boolean = true,
    val mediaControl: Boolean = true,
    val telephonySync: Boolean = true,
    val findDevice: Boolean = true,
    val safeCommands: Boolean = true,
    val sharedLinks: Boolean = true,
    val remoteInput: Boolean = true
) {
    val enabledCount: Int
        get() = listOf(
            fileTransfer,
            batterySync,
            connectivitySync,
            ping,
            notificationSync,
            mediaControl,
            telephonySync,
            findDevice,
            safeCommands,
            sharedLinks,
            remoteInput
        ).count { it }

    val requiresSpecialAccess: Boolean
        get() = notificationSync || mediaControl || telephonySync || findDevice ||
            safeCommands || remoteInput
}

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

    fun appLanguage(): AppLanguage = AppLanguage.fromStored(
        preferences.getString(KEY_APP_LANGUAGE, null)
    )

    fun setAppLanguage(language: AppLanguage) {
        preferences.edit().putString(KEY_APP_LANGUAGE, language.name).apply()
    }

    fun featureSettings(): FeatureSettings = FeatureSettings(
        fileTransfer = preferences.getBoolean(KEY_FEATURE_FILES, true),
        batterySync = preferences.getBoolean(KEY_FEATURE_BATTERY, true),
        connectivitySync = preferences.getBoolean(KEY_FEATURE_CONNECTIVITY, true),
        ping = preferences.getBoolean(KEY_FEATURE_PING, true),
        notificationSync = preferences.getBoolean(KEY_FEATURE_NOTIFICATIONS, true),
        mediaControl = preferences.getBoolean(KEY_FEATURE_MEDIA, true),
        telephonySync = preferences.getBoolean(KEY_FEATURE_TELEPHONY, true),
        findDevice = preferences.getBoolean(KEY_FEATURE_FIND_DEVICE, true),
        safeCommands = preferences.getBoolean(KEY_FEATURE_SAFE_COMMANDS, true),
        sharedLinks = preferences.getBoolean(KEY_FEATURE_SHARED_LINKS, true),
        remoteInput = preferences.getBoolean(KEY_FEATURE_REMOTE_INPUT, true)
    )

    fun setFeatureSettings(settings: FeatureSettings) {
        preferences.edit()
            .putBoolean(KEY_FEATURE_FILES, settings.fileTransfer)
            .putBoolean(KEY_FEATURE_BATTERY, settings.batterySync)
            .putBoolean(KEY_FEATURE_CONNECTIVITY, settings.connectivitySync)
            .putBoolean(KEY_FEATURE_PING, settings.ping)
            .putBoolean(KEY_FEATURE_NOTIFICATIONS, settings.notificationSync)
            .putBoolean(KEY_FEATURE_MEDIA, settings.mediaControl)
            .putBoolean(KEY_FEATURE_TELEPHONY, settings.telephonySync)
            .putBoolean(KEY_FEATURE_FIND_DEVICE, settings.findDevice)
            .putBoolean(KEY_FEATURE_SAFE_COMMANDS, settings.safeCommands)
            .putBoolean(KEY_FEATURE_SHARED_LINKS, settings.sharedLinks)
            .putBoolean(KEY_FEATURE_REMOTE_INPUT, settings.remoteInput)
            .apply()
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
        const val KEY_APP_LANGUAGE = "app_language"
        const val KEY_FEATURE_FILES = "feature_files"
        const val KEY_FEATURE_BATTERY = "feature_battery"
        const val KEY_FEATURE_CONNECTIVITY = "feature_connectivity"
        const val KEY_FEATURE_PING = "feature_ping"
        const val KEY_FEATURE_NOTIFICATIONS = "feature_notifications"
        const val KEY_FEATURE_MEDIA = "feature_media"
        const val KEY_FEATURE_TELEPHONY = "feature_telephony"
        const val KEY_FEATURE_FIND_DEVICE = "feature_find_device"
        const val KEY_FEATURE_SAFE_COMMANDS = "feature_safe_commands"
        const val KEY_FEATURE_SHARED_LINKS = "feature_shared_links"
        const val KEY_FEATURE_REMOTE_INPUT = "feature_remote_input"
        const val KEY_TRUSTED_DEVICE_NAMES = "trusted_device_names"
        const val SUFFIX_NAME = "name"
        const val SUFFIX_AUTO_FILES = "auto_files"
        const val SUFFIX_FIND_DEVICE = "find_device"
        const val MAX_DEVICE_NAME_LENGTH = 120
    }
}
