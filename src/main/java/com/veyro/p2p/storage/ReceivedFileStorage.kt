package com.veyro.p2p.storage

import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.annotation.RequiresApi
import com.veyro.p2p.nearby.FileMetadata
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest

data class SavedFile(
    val uri: Uri,
    val displayName: String
)

class ReceivedFileStorage(private val context: Context) {
    private val contentResolver = context.contentResolver

    suspend fun saveReceivedFile(
        temporaryUri: Uri,
        metadata: FileMetadata
    ): SavedFile = withContext(Dispatchers.IO) {
        val displayName = sanitizeFileName(metadata.fileName, metadata.payloadId)
        val mimeType = metadata.mimeType.ifBlank { DEFAULT_MIME_TYPE }
        require(metadata.sha256.size == 32 && MessageDigest.isEqual(
            sha256(temporaryUri),
            metadata.sha256
        )) { "O arquivo recebido falhou na verificação SHA-256." }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            saveWithMediaStore(temporaryUri, displayName, mimeType, metadata.totalBytes)
        } else {
            saveToLegacyDownloads(temporaryUri, displayName, mimeType, metadata.totalBytes)
        }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun saveWithMediaStore(
        temporaryUri: Uri,
        displayName: String,
        mimeType: String,
        expectedBytes: Long
    ): SavedFile {
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
            put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
            put(
                MediaStore.MediaColumns.RELATIVE_PATH,
                "${Environment.DIRECTORY_DOWNLOADS}/$VEYRO_DIRECTORY"
            )
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val destinationUri = contentResolver.insert(
            MediaStore.Downloads.EXTERNAL_CONTENT_URI,
            values
        ) ?: error("Não foi possível criar o arquivo em Downloads/$VEYRO_DIRECTORY.")

        try {
            val copiedBytes = copyUri(temporaryUri, destinationUri)
            requireCompleteCopy(copiedBytes, expectedBytes)

            contentResolver.update(
                destinationUri,
                ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) },
                null,
                null
            )
            return SavedFile(destinationUri, displayName)
        } catch (error: Throwable) {
            contentResolver.delete(destinationUri, null, null)
            throw error
        }
    }

    @Suppress("DEPRECATION")
    private fun saveToLegacyDownloads(
        temporaryUri: Uri,
        displayName: String,
        mimeType: String,
        expectedBytes: Long
    ): SavedFile {
        val downloadsDirectory = Environment.getExternalStoragePublicDirectory(
            Environment.DIRECTORY_DOWNLOADS
        )
        val veyroDirectory = File(downloadsDirectory, VEYRO_DIRECTORY)
        check(veyroDirectory.exists() || veyroDirectory.mkdirs()) {
            "Não foi possível criar Downloads/$VEYRO_DIRECTORY."
        }

        val destination = uniqueDestination(veyroDirectory, displayName)
        try {
            val copiedBytes = contentResolver.openInputStream(temporaryUri)?.use { input ->
                FileOutputStream(destination).use { output ->
                    input.copyTo(output, COPY_BUFFER_SIZE)
                }
            } ?: error("Não foi possível abrir o arquivo temporário recebido.")
            requireCompleteCopy(copiedBytes, expectedBytes)

            MediaScannerConnection.scanFile(
                context,
                arrayOf(destination.absolutePath),
                arrayOf(mimeType),
                null
            )
            return SavedFile(Uri.fromFile(destination), destination.name)
        } catch (error: Throwable) {
            destination.delete()
            throw error
        }
    }

    private fun copyUri(sourceUri: Uri, destinationUri: Uri): Long {
        val input = contentResolver.openInputStream(sourceUri)
            ?: error("Não foi possível abrir o arquivo temporário recebido.")
        val output = contentResolver.openOutputStream(destinationUri, "w")
            ?: run {
                input.close()
                error("Não foi possível gravar o arquivo recebido.")
            }

        return input.use { source ->
            output.use { destination ->
                source.copyTo(destination, COPY_BUFFER_SIZE)
            }
        }
    }

    private fun requireCompleteCopy(copiedBytes: Long, expectedBytes: Long) {
        check(expectedBytes <= 0L || copiedBytes == expectedBytes) {
            "Arquivo incompleto: $copiedBytes de $expectedBytes bytes copiados."
        }
    }

    private fun sha256(uri: Uri): ByteArray {
        val digest = MessageDigest.getInstance("SHA-256")
        val input = contentResolver.openInputStream(uri)
            ?: error("Não foi possível abrir o arquivo temporário para verificação.")
        input.use { source ->
            val buffer = ByteArray(COPY_BUFFER_SIZE)
            while (true) {
                val count = source.read(buffer)
                if (count < 0) break
                if (count > 0) digest.update(buffer, 0, count)
            }
        }
        return digest.digest()
    }

    private fun uniqueDestination(directory: File, displayName: String): File {
        val requestedFile = File(directory, displayName)
        if (!requestedFile.exists()) return requestedFile

        val extension = requestedFile.extension.takeIf { it.isNotEmpty() }
        val baseName = if (extension == null) displayName else requestedFile.nameWithoutExtension
        var suffix = 1
        while (true) {
            val candidateName = if (extension == null) {
                "$baseName ($suffix)"
            } else {
                "$baseName ($suffix).$extension"
            }
            val candidate = File(directory, candidateName)
            if (!candidate.exists()) return candidate
            suffix += 1
        }
    }

    private fun sanitizeFileName(rawName: String, payloadId: Long): String {
        val safeName = rawName
            .substringAfterLast('/')
            .substringAfterLast('\\')
            .replace(Regex("""[\\/:*?"<>|\p{Cntrl}]"""), "_")
            .trim()
            .trim('.')
        return safeName.ifBlank { "arquivo_$payloadId" }
    }

    private companion object {
        const val VEYRO_DIRECTORY = "Veyro"
        const val DEFAULT_MIME_TYPE = "application/octet-stream"
        const val COPY_BUFFER_SIZE = 64 * 1024
    }
}
