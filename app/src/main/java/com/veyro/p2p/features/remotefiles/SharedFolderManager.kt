package com.veyro.p2p.features.remotefiles

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import com.veyro.p2p.protocol.RemoteFileEntry

data class SharedFolderInfo(val uri: Uri, val displayName: String)

class SharedFolderManager(context: Context) {
    private val appContext = context.applicationContext
    private val resolver = appContext.contentResolver
    private val preferences = appContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    private val allowedDocumentIds = mutableSetOf<String>()

    fun shareTree(uri: Uri): Result<SharedFolderInfo> = runCatching {
        resolver.takePersistableUriPermission(
            uri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION
        )
        allowedDocumentIds.clear()
        allowedDocumentIds += DocumentsContract.getTreeDocumentId(uri)
        val name = queryName(uri).ifBlank { "Pasta compartilhada" }
        preferences.edit()
            .putString(KEY_TREE_URI, uri.toString())
            .putString(KEY_TREE_NAME, name)
            .apply()
        SharedFolderInfo(uri, name)
    }

    fun clearSharedTree() {
        sharedTree()?.let { info ->
            runCatching {
                resolver.releasePersistableUriPermission(
                    info.uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
        }
        preferences.edit().remove(KEY_TREE_URI).remove(KEY_TREE_NAME).apply()
        allowedDocumentIds.clear()
    }

    fun sharedTree(): SharedFolderInfo? {
        val uri = preferences.getString(KEY_TREE_URI, null)?.let(Uri::parse) ?: return null
        val persisted = resolver.persistedUriPermissions.any { it.uri == uri && it.isReadPermission }
        if (!persisted) return null
        return SharedFolderInfo(uri, preferences.getString(KEY_TREE_NAME, null) ?: queryName(uri))
    }

    fun rootDocumentId(): String? = sharedTree()?.uri?.let(DocumentsContract::getTreeDocumentId)

    @Synchronized
    fun listChildren(parentDocumentId: String?): Result<List<RemoteFileEntry>> = runCatching {
        val tree = sharedTree()?.uri ?: error("Nenhuma pasta foi compartilhada.")
        val rootId = DocumentsContract.getTreeDocumentId(tree)
        allowedDocumentIds += rootId
        val parentId = parentDocumentId?.takeIf(String::isNotBlank) ?: rootId
        require(isAllowedDocument(tree, rootId, parentId)) { "Pasta fora da área compartilhada." }
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(tree, parentId)
        val entries = resolver.query(
            childrenUri,
            arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_MIME_TYPE,
                DocumentsContract.Document.COLUMN_SIZE
            ),
            null,
            null,
            null
        )?.use { cursor ->
            val idIndex = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
            val nameIndex = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
            val typeIndex = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE)
            val sizeIndex = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_SIZE)
            buildList {
                while (cursor.moveToNext() && size < MAX_ENTRIES) {
                    val id = cursor.getString(idIndex).orEmpty()
                    val type = cursor.getString(typeIndex).orEmpty()
                    if (id.isBlank()) continue
                    add(
                        RemoteFileEntry.newBuilder()
                            .setDocumentId(id.take(MAX_DOCUMENT_ID_LENGTH))
                            .setDisplayName(cursor.getString(nameIndex).orEmpty().take(MAX_NAME_LENGTH))
                            .setMimeType(type.take(MAX_MIME_LENGTH))
                            .setSizeBytes(if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) cursor.getLong(sizeIndex) else 0L)
                            .setIsDirectory(type == DocumentsContract.Document.MIME_TYPE_DIR)
                            .build()
                    )
                }
            }
        }.orEmpty().sortedWith(compareByDescending<RemoteFileEntry> { it.isDirectory }.thenBy {
            it.displayName.lowercase()
        })
        allowedDocumentIds += entries.map(RemoteFileEntry::getDocumentId)
        entries
    }

    @Synchronized
    fun documentUri(documentId: String): Uri? {
        val tree = sharedTree()?.uri ?: return null
        val rootId = DocumentsContract.getTreeDocumentId(tree)
        if (!isAllowedDocument(tree, rootId, documentId)) return null
        return DocumentsContract.buildDocumentUriUsingTree(tree, documentId)
            .takeIf { uri -> runCatching { resolver.getType(uri) }.getOrNull() != null }
    }

    private fun isAllowedDocument(tree: Uri, rootId: String, documentId: String): Boolean {
        if (documentId !in allowedDocumentIds && documentId != rootId) return false
        if (documentId == rootId) return true
        val candidate = DocumentsContract.buildDocumentUriUsingTree(tree, documentId)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            runCatching {
                val root = DocumentsContract.buildDocumentUriUsingTree(tree, rootId)
                DocumentsContract.isChildDocument(resolver, root, candidate)
            }.getOrDefault(false)
        } else {
            true
        }
    }

    private fun queryName(uri: Uri): String = resolver.query(
        uri,
        arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME),
        null,
        null,
        null
    )?.use { cursor ->
        if (cursor.moveToFirst()) cursor.getString(0).orEmpty() else ""
    }.orEmpty()

    private companion object {
        const val PREFERENCES_NAME = "veyro_shared_folder"
        const val KEY_TREE_URI = "tree_uri"
        const val KEY_TREE_NAME = "tree_name"
        const val MAX_ENTRIES = 200
        const val MAX_DOCUMENT_ID_LENGTH = 1024
        const val MAX_NAME_LENGTH = 255
        const val MAX_MIME_LENGTH = 160
    }
}
