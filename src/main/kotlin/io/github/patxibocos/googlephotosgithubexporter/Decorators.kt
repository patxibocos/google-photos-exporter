package io.github.patxibocos.googlephotosgithubexporter

import mu.KotlinLogging
import net.lingala.zip4j.ZipFile
import net.lingala.zip4j.model.ZipParameters
import org.slf4j.Logger
import java.io.File

class LoggingDecorator(private val repository: ExportRepository, private val logger: Logger = KotlinLogging.logger {}) :
    ExportRepository by repository {
    override suspend fun upload(data: ByteArray, name: String, filePath: String, overrideContent: Boolean) {
        val sizeInMBs = data.size.toFloat() / 1024 / 1024
        logger.info("Uploading $name (${"%.2f".format(sizeInMBs)} MB)")
        repository.upload(data, name, filePath, overrideContent)
    }
}

class RetryDecorator(
    private val repository: ExportRepository,
    private val maxRetries: Int,
    private val logger: Logger = KotlinLogging.logger {}
) : ExportRepository by repository {
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

class SplitDecorator(
    private val repository: ExportRepository,
    maxChunkSizeMBs: Int,
    private val logger: Logger = KotlinLogging.logger {}
) :
    ExportRepository by repository {

    private val maxSizeInBytes = maxChunkSizeMBs * 1024 * 1024

    private class ZipSplit(val name: String, val data: ByteArray)

    private fun zipAndSplit(name: String, content: ByteArray): List<ZipSplit> {
        val file = File(name).apply {
            createNewFile()
            writeBytes(content)
        }
        val zipFile = ZipFile("$name.zip")
        zipFile.createSplitZipFile(listOf(file), ZipParameters(), true, maxSizeInBytes.toLong())
        val zipSplits = zipFile.splitZipFiles.map {
            ZipSplit(it.name, it.readBytes())
        }
        file.delete()
        zipFile.splitZipFiles.forEach(File::delete)
        return zipSplits
    }

    override suspend fun upload(data: ByteArray, name: String, filePath: String, overrideContent: Boolean) {
        if (data.size > maxSizeInBytes) {
            logger.info("Splitting $name into chunks")
            val fileName = filePath.split("/").last()
            val zipSplits = zipAndSplit(fileName, data)
            zipSplits.forEachIndexed { index, zipSplit ->
                val path = "$filePath/${zipSplit.name}"
                val splitName = "$name (part ${index + 1}/${zipSplits.size})"
                upload(zipSplit.data, splitName, path, overrideContent)
            }
            return
        }
        repository.upload(data, name, filePath, overrideContent)
    }
}
