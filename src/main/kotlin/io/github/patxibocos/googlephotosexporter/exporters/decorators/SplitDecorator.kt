package io.github.patxibocos.googlephotosexporter.exporters.decorators

import io.github.patxibocos.googlephotosexporter.exporters.Exporter
import mu.KotlinLogging
import net.lingala.zip4j.ZipFile
import net.lingala.zip4j.model.ZipParameters
import org.slf4j.Logger
import java.io.File

internal class SplitDecorator(
    private val repository: Exporter,
    maxChunkSizeMBs: Int,
    private val logger: Logger = KotlinLogging.logger {}
) :
    Exporter by repository {

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
