package com.qesuite.accounting.shared.storage

import io.minio.*
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Service
import org.springframework.web.multipart.MultipartFile
import java.io.InputStream
import java.util.UUID

/**
 * MinIO / S3-compatible object storage implementation.
 *
 * Activated when `app.storage.type=minio`.
 * All documents land in `${MINIO_BUCKET}` bucket under key `{entityId}/{uuid}_{filename}`.
 * The bucket is auto-created on first upload if it does not exist.
 */
@Service
@ConditionalOnProperty(name = ["app.storage.type"], havingValue = "minio")
class MinioStorageService(
    @Value("\${app.storage.minio.endpoint:http://minio:9000}")   private val endpoint: String,
    @Value("\${app.storage.minio.access-key:minioadmin}")        private val accessKey: String,
    @Value("\${app.storage.minio.secret-key:minioadmin}")        private val secretKey: String,
    @Value("\${app.storage.minio.bucket:qesuite-documents}")     private val bucket: String,
) : StorageService {

    private val log = LoggerFactory.getLogger(MinioStorageService::class.java)

    private val client: MinioClient by lazy {
        MinioClient.builder()
            .endpoint(endpoint)
            .credentials(accessKey, secretKey)
            .build()
            .also { ensureBucket(it) }
    }

    override fun store(entityId: UUID, file: MultipartFile): StoredFile {
        val safeName = file.originalFilename?.replace(Regex("[^a-zA-Z0-9._-]"), "_") ?: "upload"
        val objectKey = "$entityId/${UUID.randomUUID()}_$safeName"
        val contentType = file.contentType ?: "application/octet-stream"

        file.inputStream.use { stream ->
            client.putObject(
                PutObjectArgs.builder()
                    .bucket(bucket)
                    .`object`(objectKey)
                    .stream(stream, file.size, -1)
                    .contentType(contentType)
                    .build()
            )
        }
        log.info("storage.minio.store bucket={} key={} size={}", bucket, objectKey, file.size)
        return StoredFile(
            storagePath = objectKey,
            fileName    = safeName,
            contentType = contentType,
            fileSize    = file.size,
        )
    }

    override fun load(storagePath: String): InputStream {
        return client.getObject(
            GetObjectArgs.builder()
                .bucket(bucket)
                .`object`(storagePath)
                .build()
        )
    }

    override fun delete(storagePath: String) {
        client.removeObject(
            RemoveObjectArgs.builder()
                .bucket(bucket)
                .`object`(storagePath)
                .build()
        )
        log.info("storage.minio.delete bucket={} key={}", bucket, storagePath)
    }

    private fun ensureBucket(mc: MinioClient) {
        val exists = mc.bucketExists(BucketExistsArgs.builder().bucket(bucket).build())
        if (!exists) {
            mc.makeBucket(MakeBucketArgs.builder().bucket(bucket).build())
            log.info("storage.minio.bucket-created name={}", bucket)
        }
    }
}
