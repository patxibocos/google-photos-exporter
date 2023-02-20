package io.github.patxibocos.googlephotosexporter.exporters.decorators

import io.github.patxibocos.googlephotosexporter.exporters.Exporter
import mu.KotlinLogging
import org.slf4j.Logger

internal class LoggingDecorator(private val repository: Exporter, private val logger: Logger = KotlinLogging.logger {}) :
    Exporter by repository {
    override suspend fun upload(data: ByteArray, name: String, filePath: String, overrideContent: Boolean) {
        val sizeInMBs = data.size.toFloat() / 1024 / 1024
        logger.info("Uploading $name (${"%.2f".format(sizeInMBs)} MB)")
        repository.upload(data, name, filePath, overrideContent)
    }
}
