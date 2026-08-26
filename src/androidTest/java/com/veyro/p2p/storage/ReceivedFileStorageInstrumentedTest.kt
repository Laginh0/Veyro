package com.veyro.p2p.storage

import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.veyro.p2p.nearby.FileMetadata
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.security.MessageDigest

@RunWith(AndroidJUnit4::class)
class ReceivedFileStorageInstrumentedTest {

    @Test
    fun savesCompleteFileInVeyroDownloads() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val testBytes = "Veyro Fase 8 - Scoped Storage".toByteArray()
        val fileName = "veyro-phase8-storage-test-${System.currentTimeMillis()}.txt"
        val temporaryFile = File(context.cacheDir, fileName).apply {
            writeBytes(testBytes)
        }
        var savedUri: Uri? = null

        try {
            val savedFile = ReceivedFileStorage(context).saveReceivedFile(
                temporaryUri = Uri.fromFile(temporaryFile),
                metadata = FileMetadata(
                    payloadId = System.currentTimeMillis(),
                    fileName = fileName,
                    totalBytes = testBytes.size.toLong(),
                    mimeType = "text/plain",
                    sha256 = MessageDigest.getInstance("SHA-256").digest(testBytes)
                )
            )
            savedUri = savedFile.uri

            assertEquals(fileName, savedFile.displayName)
            val persistedBytes = context.contentResolver.openInputStream(savedFile.uri)?.use {
                it.readBytes()
            }
            assertArrayEquals(testBytes, persistedBytes)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                context.contentResolver.query(
                    savedFile.uri,
                    arrayOf(
                        MediaStore.MediaColumns.RELATIVE_PATH,
                        MediaStore.MediaColumns.IS_PENDING
                    ),
                    null,
                    null,
                    null
                )?.use { cursor ->
                    assertTrue(cursor.moveToFirst())
                    val relativePath = cursor.getString(0)
                    val isPending = cursor.getInt(1)
                    assertTrue(relativePath.contains("Download/Veyro"))
                    assertEquals(0, isPending)
                } ?: error("Arquivo salvo não foi encontrado no MediaStore.")
            }
        } finally {
            savedUri?.let { context.contentResolver.delete(it, null, null) }
            temporaryFile.delete()
        }
    }
}
