package com.qesuite.accounting.shared.storage

import org.springframework.web.multipart.MultipartFile
import java.io.InputStream
import java.util.UUID

/**
 * Pluggable file-storage abstraction.
 *
 * Active implementation is selected via `app.storage.type`:
 *   local  — local disk under UPLOAD_DIR (default; dev / single-node)
 *   minio  — MinIO / S3-compatible object store (production)
 */
interface StorageService {
    fun store(entityId: UUID, file: MultipartFile): StoredFile
    fun load(storagePath: String): InputStream
    fun delete(storagePath: String)
}

data class StoredFile(
    val storagePath: String,
    val fileName: String,
    val contentType: String,
    val fileSize: Long,
)
