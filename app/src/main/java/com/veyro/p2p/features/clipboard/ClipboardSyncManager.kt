package com.veyro.p2p.features.clipboard

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.PersistableBundle
import android.widget.Toast
import java.security.MessageDigest

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

    fun readPlainText(): String? = runCatching {
        val clip = clipboardManager.primaryClip ?: return@runCatching null
        if (clip.itemCount == 0) return@runCatching null
        clip.getItemAt(0).text?.toString()
    }.getOrNull()

    fun writePlainText(text: String) {
        remoteWriteFingerprint = fingerprint(text)
        val clip = ClipData.newPlainText(CLIP_LABEL, text).apply {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                description.extras = PersistableBundle().apply {
                    putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true)
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
        val current = readPlainText()?.let(::fingerprint)
        remoteWriteFingerprint = null
        return current == expected
    }

    companion object {
        const val MAX_TEXT_BYTES = 20_000
        private const val CLIP_LABEL = "Veyro synchronized text"

        fun isSafeText(text: String): Boolean = text.toByteArray(Charsets.UTF_8).size <= MAX_TEXT_BYTES

        fun fingerprint(text: String): String = MessageDigest.getInstance("SHA-256")
            .digest(text.toByteArray(Charsets.UTF_8))
            .joinToString("") { byte -> "%02x".format(byte) }
    }
}
