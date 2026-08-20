package com.veyro.p2p.features.clipboard

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import java.security.MessageDigest

class ClipboardSyncManager(
    context: Context,
    private val onLocalClipboardChanged: () -> Unit
) : AutoCloseable {
    private val clipboardManager = context.applicationContext
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
        clipboardManager.setPrimaryClip(ClipData.newPlainText(CLIP_LABEL, text))
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
