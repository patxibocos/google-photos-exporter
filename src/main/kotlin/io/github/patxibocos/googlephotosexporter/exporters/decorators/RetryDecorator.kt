package io.github.patxibocos.googlephotosexporter.exporters.decorators

import io.github.patxibocos.googlephotosexporter.exporters.Exporter
import mu.KotlinLogging
import org.slf4j.Logger

internal class RetryDecorator(
    private val repository: Exporter,
    private val maxRetries: Int,
    private val logger: Logger = KotlinLogging.logger {}
) : Exporter by repository {
    override suspend fun upload(data: ByteArray, name: String, filePath: String, overrideContent: Boolean) {
        suspend fun uploadWithRetry(
            data: ByteArray,
            name: String,
            filePath: String,
            overrideContent: Boolean,
            retry: Int = 0
        ) {
            try {
                repository.upload(data, name, filePath, overrideContent)
            } catch (t: Throwable) {
                if (retry < maxRetries) {
                    logger.warn("Upload failed, retrying (${retry + 1}/$maxRetries)")
                    uploadWithRetry(data, name, filePath, overrideContent, retry + 1)
                } else {
                    logger.error("Upload failed, giving up")
                    throw t
                }
            }
        }
        uploadWithRetry(data, name, filePath, overrideContent)
    }
}
