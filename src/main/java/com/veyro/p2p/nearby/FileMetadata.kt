package com.veyro.p2p.nearby

import org.json.JSONObject
import android.util.Base64

data class FileMetadata(
    val payloadId: Long,
    val fileName: String,
    val totalBytes: Long,
    val mimeType: String,
    val sha256: ByteArray
) {
    fun toWireBytes(): ByteArray = JSONObject()
        .put(KEY_PROTOCOL, PROTOCOL_VALUE)
        .put(KEY_PAYLOAD_ID, payloadId)
        .put(KEY_FILE_NAME, fileName)
        .put(KEY_TOTAL_BYTES, totalBytes)
        .put(KEY_MIME_TYPE, mimeType)
        .put(KEY_SHA256, Base64.encodeToString(sha256, Base64.NO_WRAP))
        .toString()
        .toByteArray(Charsets.UTF_8)

    companion object {
        private const val KEY_PROTOCOL = "protocol"
        private const val KEY_PAYLOAD_ID = "payloadId"
        private const val KEY_FILE_NAME = "fileName"
        private const val KEY_TOTAL_BYTES = "totalBytes"
        private const val KEY_MIME_TYPE = "mimeType"
        private const val KEY_SHA256 = "sha256"
        private const val PROTOCOL_VALUE = "veyro.file-metadata.v2"

        fun fromWireBytes(bytes: ByteArray): FileMetadata? = runCatching {
            require(bytes.size in 1..8_192)
            val json = JSONObject(bytes.toString(Charsets.UTF_8))
            if (json.optString(KEY_PROTOCOL) != PROTOCOL_VALUE) return null

            val hash = Base64.decode(json.getString(KEY_SHA256), Base64.NO_WRAP)
            require(hash.size == 32)

            FileMetadata(
                payloadId = json.getLong(KEY_PAYLOAD_ID),
                fileName = json.getString(KEY_FILE_NAME).take(255),
                totalBytes = json.getLong(KEY_TOTAL_BYTES).takeIf { it >= 0L }
                    ?: error("invalid file size"),
                mimeType = json.optString(KEY_MIME_TYPE, "application/octet-stream").take(160),
                sha256 = hash
            )
        }.getOrNull()
    }
}
