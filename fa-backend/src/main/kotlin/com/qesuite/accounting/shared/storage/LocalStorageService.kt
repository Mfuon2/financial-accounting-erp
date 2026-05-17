package com.qesuite.accounting.shared.storage

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Service
import org.springframework.web.multipart.MultipartFile
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import java.util.UUID

@Service
@ConditionalOnProperty(name = ["app.storage.type"], havingValue = "local", matchIfMissing = true)
class LocalStorageService(
    @Value("\${app.storage.upload-dir:./uploads}") private val uploadDir: String
) : StorageService {

    private val log = LoggerFactory.getLogger(LocalStorageService::class.java)

    override fun store(entityId: UUID, file: MultipartFile): StoredFile {
        val safeName = file.originalFilename?.replace(Regex("[^a-zA-Z0-9._-]"), "_") ?: "upload"
        val uniqueName = "${UUID.randomUUID()}_$safeName"
        val dir = Paths.get(uploadDir, entityId.toString())
        Files.createDirectories(dir)
        val dest = dir.resolve(uniqueName)
        Files.copy(file.inputStream, dest, StandardCopyOption.REPLACE_EXISTING)
        log.info("storage.local.store path={} size={}", dest, file.size)
        return StoredFile(
            storagePath = "$entityId/$uniqueName",
            fileName    = safeName,
            contentType = file.contentType ?: "application/octet-stream",
            fileSize    = file.size,
        )
    }

    override fun load(storagePath: String): InputStream =
        Files.newInputStream(Paths.get(uploadDir).resolve(storagePath))

    override fun delete(storagePath: String) {
        val path = Paths.get(uploadDir).resolve(storagePath)
        Files.deleteIfExists(path)
        log.info("storage.local.delete path={}", path)
    }
}
