package com.veyro.p2p.features.clipboard

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.PersistableBundle
import android.widget.Toast
import androidx.annotation.RequiresApi
import java.security.MessageDigest

sealed interface ClipboardReadResult {
    data class Text(val value: String) : ClipboardReadResult
    data object Empty : ClipboardReadResult
    data object Sensitive : ClipboardReadResult
    data object RemoteDevice : ClipboardReadResult
}

class ClipboardSyncManager(
    context: Context,
    private val onLocalClipboardChanged: () -> Unit
) : AutoCloseable {
    private val appContext = context.applicationContext
    private val clipboardManager = appContext
        .getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    private val listener = ClipboardManager.OnPrimaryClipChangedListener {
        if (!consumeRemoteWrite()) onLocalClipboardChanged()
    }
    private var remoteWriteFingerprint: String? = null

    init {
        clipboardManager.addPrimaryClipChangedListener(listener)
    }

    fun readPlainText(): ClipboardReadResult = runCatching {
        val clip = clipboardManager.primaryClip ?: return@runCatching null
        if (clip.itemCount == 0) return@runCatching null
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            if (hasDescriptionFlag(clip.description, EXTRA_IS_SENSITIVE)) {
                return@runCatching ClipboardReadResult.Sensitive
            }
            if (hasDescriptionFlag(clip.description, EXTRA_IS_REMOTE_DEVICE)) {
                return@runCatching ClipboardReadResult.RemoteDevice
            }
        }
        clip.getItemAt(0).text?.toString()?.let(ClipboardReadResult::Text)
    }.getOrNull() ?: ClipboardReadResult.Empty

    fun writePlainText(text: String) {
        remoteWriteFingerprint = fingerprint(text)
        val clip = ClipData.newPlainText(CLIP_LABEL, text).apply {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                description.extras = PersistableBundle().apply {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true)
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                        putBoolean(ClipDescription.EXTRA_IS_REMOTE_DEVICE, true)
                    }
                }
            }
        }
        clipboardManager.setPrimaryClip(clip)
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.S_V2) {
            Toast.makeText(
                appContext,
                "Veyro copiou um texto recebido para o clipboard.",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    override fun close() {
        clipboardManager.removePrimaryClipChangedListener(listener)
    }

    private fun consumeRemoteWrite(): Boolean {
        val expected = remoteWriteFingerprint ?: return false
        val current = rawPlainText()?.let(::fingerprint)
        remoteWriteFingerprint = null
        return current == expected
    }

    private fun rawPlainText(): String? = runCatching {
        val clip = clipboardManager.primaryClip ?: return@runCatching null
        if (clip.itemCount == 0) return@runCatching null
        clip.getItemAt(0).text?.toString()
    }.getOrNull()

    @RequiresApi(Build.VERSION_CODES.N)
    private fun hasDescriptionFlag(description: ClipDescription, key: String): Boolean =
        description.extras?.getBoolean(key) == true

    companion object {
        const val MAX_TEXT_BYTES = 20_000
        private const val CLIP_LABEL = "Veyro synchronized text"
        private const val EXTRA_IS_SENSITIVE = "android.content.extra.IS_SENSITIVE"
        private const val EXTRA_IS_REMOTE_DEVICE = "android.content.extra.IS_REMOTE_DEVICE"

        fun isSafeText(text: String): Boolean = text.toByteArray(Charsets.UTF_8).size <= MAX_TEXT_BYTES

        fun fingerprint(text: String): String = MessageDigest.getInstance("SHA-256")
            .digest(text.toByteArray(Charsets.UTF_8))
            .joinToString("") { byte -> "%02x".format(byte) }
    }
}
