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
    val allowFindDevice: Boolean = false,
    val deviceId: String = ""
)

data class FeatureSettings(
    val fileTransfer: Boolean = true,
    val batterySync: Boolean = true,
    val connectivitySync: Boolean = true,
    val ping: Boolean = true,
    val notificationSync: Boolean = false,
    val mediaControl: Boolean = false,
    val telephonySync: Boolean = false,
    val findDevice: Boolean = false,
    val safeCommands: Boolean = false,
    val sharedLinks: Boolean = true,
    val remoteInput: Boolean = false,
    val contactSync: Boolean = true,
    val presentationMode: Boolean = true,
    val drawingTablet: Boolean = true,
    val remoteFiles: Boolean = true,
    val clipboardSync: Boolean = false
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
            remoteInput,
            contactSync,
            presentationMode,
            drawingTablet,
            remoteFiles,
            clipboardSync
        ).count { it }

    val requiresSpecialAccess: Boolean
        get() = notificationSync || mediaControl || telephonySync || findDevice ||
            safeCommands || remoteInput

    companion object {
        const val AVAILABLE_COUNT = 16
    }
}

class EcosystemPreferences(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE
    )

    fun localDeviceId(): String {
        return preferences.getString(KEY_LOCAL_DEVICE_ID, null)
            ?: UUID.randomUUID().toString().replace("-", "").take(16).also { generated ->
                preferences.edit().putString(KEY_LOCAL_DEVICE_ID, generated).apply()
            }
    }

    fun localDisplayName(): String = "Veyro - ${Build.MODEL}"

    fun localEndpointName(): String = "${localDisplayName()} #${localDeviceId()}"

    fun ecosystemEnabled(): Boolean = preferences.getBoolean(KEY_ECOSYSTEM_ENABLED, false)

    fun setEcosystemEnabled(enabled: Boolean) {
        preferences.edit().putBoolean(KEY_ECOSYSTEM_ENABLED, enabled).apply()
    }

    @Synchronized
    fun nextSenderEpoch(): Long {
        val previous = preferences.getLong(KEY_SENDER_EPOCH, 0L)
        check(previous < Long.MAX_VALUE) { "A época lógica do protocolo foi esgotada." }
        val now = System.currentTimeMillis().coerceAtLeast(1L)
        val next = maxOf(now, previous + 1L)
        check(preferences.edit().putLong(KEY_SENDER_EPOCH, next).commit()) {
            "Não foi possível persistir a época lógica do protocolo."
        }
        return next
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
        notificationSync = preferences.getBoolean(KEY_FEATURE_NOTIFICATIONS, false),
        mediaControl = preferences.getBoolean(KEY_FEATURE_MEDIA, false),
        telephonySync = preferences.getBoolean(KEY_FEATURE_TELEPHONY, false),
        findDevice = preferences.getBoolean(KEY_FEATURE_FIND_DEVICE, false),
        safeCommands = preferences.getBoolean(KEY_FEATURE_SAFE_COMMANDS, false),
        sharedLinks = preferences.getBoolean(KEY_FEATURE_SHARED_LINKS, true),
        remoteInput = preferences.getBoolean(KEY_FEATURE_REMOTE_INPUT, false),
        contactSync = preferences.getBoolean(KEY_FEATURE_CONTACTS, true),
        presentationMode = preferences.getBoolean(KEY_FEATURE_PRESENTATION, true),
        drawingTablet = preferences.getBoolean(KEY_FEATURE_DRAWING_TABLET, true),
        remoteFiles = preferences.getBoolean(KEY_FEATURE_REMOTE_FILES, true),
        clipboardSync = preferences.getBoolean(KEY_FEATURE_CLIPBOARD, false)
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
            .putBoolean(KEY_FEATURE_CONTACTS, settings.contactSync)
            .putBoolean(KEY_FEATURE_PRESENTATION, settings.presentationMode)
            .putBoolean(KEY_FEATURE_DRAWING_TABLET, settings.drawingTablet)
            .putBoolean(KEY_FEATURE_REMOTE_FILES, settings.remoteFiles)
            .putBoolean(KEY_FEATURE_CLIPBOARD, settings.clipboardSync)
            .apply()
    }

    @Synchronized
    fun trustedDevices(): List<TrustedDeviceRules> = buildList {
        addAll(deviceIds().mapNotNull(::rulesForDeviceId))
        addAll(deviceNames().mapNotNull(::rulesFor).filter { legacy ->
            none { it.deviceName == legacy.deviceName }
        })
    }.sortedBy { it.deviceName.lowercase() }

    @Synchronized
    fun rememberDevice(deviceName: String, deviceId: String = ""): TrustedDeviceRules {
        val cleanName = cleanDeviceName(deviceName)
        require(cleanName.isNotBlank()) { "O nome do aparelho não pode ficar vazio." }
        val cleanId = cleanDeviceId(deviceId)
        if (cleanId.isNotBlank()) {
            val ids = deviceIds().toMutableSet().apply { add(cleanId) }
            preferences.edit()
                .putStringSet(KEY_TRUSTED_DEVICE_IDS, ids)
                .putString(ruleKeyForId(cleanId, SUFFIX_NAME), cleanName)
                .apply()
            return rulesForDeviceId(cleanId) ?: TrustedDeviceRules(cleanName, deviceId = cleanId)
        }
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
        val cleanId = cleanDeviceId(rules.deviceId)
        rememberDevice(cleanName, cleanId)
        val key: (String) -> String = if (cleanId.isNotBlank()) {
            { suffix -> ruleKeyForId(cleanId, suffix) }
        } else {
            { suffix -> ruleKey(cleanName, suffix) }
        }
        preferences.edit()
            .putBoolean(key(SUFFIX_AUTO_FILES), rules.autoAcceptFiles)
            .putBoolean(key(SUFFIX_FIND_DEVICE), rules.allowFindDevice)
            .apply()
    }

    @Synchronized
    fun removeDevice(deviceName: String) {
        val cleanName = cleanDeviceName(deviceName)
        val removedIds = deviceIds().filter { rulesForDeviceId(it)?.deviceName == cleanName }
        val ids = deviceIds().toMutableSet().apply { removeAll(removedIds.toSet()) }
        val names = deviceNames().toMutableSet().apply { remove(cleanName) }
        val editor = preferences.edit()
            .putStringSet(KEY_TRUSTED_DEVICE_NAMES, names)
            .putStringSet(KEY_TRUSTED_DEVICE_IDS, ids)
            .remove(ruleKey(cleanName, SUFFIX_NAME))
            .remove(ruleKey(cleanName, SUFFIX_AUTO_FILES))
            .remove(ruleKey(cleanName, SUFFIX_FIND_DEVICE))
        removedIds.forEach { id ->
            editor.remove(ruleKeyForId(id, SUFFIX_NAME))
                .remove(ruleKeyForId(id, SUFFIX_AUTO_FILES))
                .remove(ruleKeyForId(id, SUFFIX_FIND_DEVICE))
        }
        editor.apply()
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

    fun rulesForDevice(deviceId: String?, deviceName: String? = null): TrustedDeviceRules? {
        val cleanId = cleanDeviceId(deviceId.orEmpty())
        if (cleanId.isBlank()) return null
        return rulesForDeviceId(cleanId)?.let { stored ->
            if (deviceName.isNullOrBlank()) stored else stored.copy(deviceName = cleanDeviceName(deviceName))
        }
    }

    private fun rulesForDeviceId(deviceId: String): TrustedDeviceRules? {
        if (deviceId !in deviceIds()) return null
        val name = preferences.getString(ruleKeyForId(deviceId, SUFFIX_NAME), null)
            ?.let(::cleanDeviceName)
            ?.takeIf(String::isNotBlank)
            ?: return null
        return TrustedDeviceRules(
            deviceName = name,
            autoAcceptFiles = preferences.getBoolean(ruleKeyForId(deviceId, SUFFIX_AUTO_FILES), false),
            allowFindDevice = preferences.getBoolean(ruleKeyForId(deviceId, SUFFIX_FIND_DEVICE), false),
            deviceId = deviceId
        )
    }

    private fun deviceNames(): Set<String> = preferences
        .getStringSet(KEY_TRUSTED_DEVICE_NAMES, emptySet())
        ?.toSet()
        .orEmpty()

    private fun deviceIds(): Set<String> = preferences
        .getStringSet(KEY_TRUSTED_DEVICE_IDS, emptySet())
        ?.toSet()
        .orEmpty()

    private fun cleanDeviceName(value: String): String = value.trim().take(MAX_DEVICE_NAME_LENGTH)

    private fun cleanDeviceId(value: String): String = value.trim().take(MAX_DEVICE_ID_LENGTH)

    private fun ruleKey(deviceName: String, suffix: String): String =
        "device_${deviceName.sha256().take(24)}_$suffix"

    private fun ruleKeyForId(deviceId: String, suffix: String): String =
        "logical_${deviceId.sha256().take(24)}_$suffix"

    private fun String.sha256(): String = MessageDigest.getInstance("SHA-256")
        .digest(lowercase().toByteArray(Charsets.UTF_8))
        .joinToString("") { byte -> "%02x".format(byte) }

    private companion object {
        const val PREFERENCES_NAME = "veyro_ecosystem"
        const val KEY_LOCAL_DEVICE_ID = "local_device_id"
        const val KEY_ECOSYSTEM_ENABLED = "ecosystem_enabled"
        const val KEY_SENDER_EPOCH = "logical_sender_epoch_v1"
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
        const val KEY_FEATURE_CONTACTS = "feature_contacts"
        const val KEY_FEATURE_PRESENTATION = "feature_presentation"
        const val KEY_FEATURE_DRAWING_TABLET = "feature_drawing_tablet"
        const val KEY_FEATURE_REMOTE_FILES = "feature_remote_files"
        const val KEY_FEATURE_CLIPBOARD = "feature_clipboard"
        const val KEY_TRUSTED_DEVICE_NAMES = "trusted_device_names"
        const val KEY_TRUSTED_DEVICE_IDS = "trusted_device_ids_v2"
        const val SUFFIX_NAME = "name"
        const val SUFFIX_AUTO_FILES = "auto_files"
        const val SUFFIX_FIND_DEVICE = "find_device"
        const val MAX_DEVICE_NAME_LENGTH = 120
        const val MAX_DEVICE_ID_LENGTH = 128
    }
}
