package com.veyro.p2p.nearby

import org.json.JSONObject

data class FileMetadata(
    val payloadId: Long,
    val fileName: String,
    val totalBytes: Long,
    val mimeType: String
) {
    fun toWireBytes(): ByteArray = JSONObject()
        .put(KEY_PROTOCOL, PROTOCOL_VALUE)
        .put(KEY_PAYLOAD_ID, payloadId)
        .put(KEY_FILE_NAME, fileName)
        .put(KEY_TOTAL_BYTES, totalBytes)
        .put(KEY_MIME_TYPE, mimeType)
        .toString()
        .toByteArray(Charsets.UTF_8)

    companion object {
        private const val KEY_PROTOCOL = "protocol"
        private const val KEY_PAYLOAD_ID = "payloadId"
        private const val KEY_FILE_NAME = "fileName"
        private const val KEY_TOTAL_BYTES = "totalBytes"
        private const val KEY_MIME_TYPE = "mimeType"
        private const val PROTOCOL_VALUE = "veyro.file-metadata.v1"

        fun fromWireBytes(bytes: ByteArray): FileMetadata? = runCatching {
            val json = JSONObject(bytes.toString(Charsets.UTF_8))
            if (json.optString(KEY_PROTOCOL) != PROTOCOL_VALUE) return null

            FileMetadata(
                payloadId = json.getLong(KEY_PAYLOAD_ID),
                fileName = json.getString(KEY_FILE_NAME),
                totalBytes = json.getLong(KEY_TOTAL_BYTES),
                mimeType = json.optString(KEY_MIME_TYPE, "application/octet-stream")
            )
        }.getOrNull()
    }
}
